package p2p;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VideoReceiver extends Thread {

    // ===== Tunables =====
    private static final int MAX_PACKET_SIZE = 1500;
    private static final int FRAME_TIMEOUT_MS = 300; // drop frame quá trễ
    private static final int MAX_BUFFERED_FRAMES = 5;

    private final int port;
    private final KeyManager keyManager;
    private final ImageView imageView;
    private final String callKey;

    private volatile boolean running = true;
    private DatagramSocket socket;

    // ===== Frame buffer =====
    private static class FrameBuffer {
        byte[][] chunks;
        int received;
        int expected;
        long firstSeen;
    }

    // frameId -> FrameBuffer
    private final Map<Integer, FrameBuffer> frameMap = new ConcurrentHashMap<>();

    // để bỏ frame cũ (anti-lag)
    private volatile int latestFrameId = -1;

    public VideoReceiver(int port,
                         KeyManager keyManager,
                         ImageView imageView,
                         String callKey) {

        this.port = port;
        this.keyManager = keyManager;
        this.imageView = imageView;
        this.callKey = callKey;
    }

    // ================= THREAD =================
    @Override
    public void run() {

        if (!OpenCVLoader.init()) {
            System.err.println("❌ OpenCV init failed (VideoReceiver)");
            return;
        }

        try {
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(1 << 20); // 1MB buffer
            System.out.println("🎥 VideoReceiver listening on port " + port);

            byte[] buf = new byte[MAX_PACKET_SIZE];

            while (running) {

                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());

                if (data.length < 6) continue;

                // ===== Parse header =====
                int frameId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                int index   = data[2] & 0xFF;
                int total   = data[3] & 0xFF;
                int len     = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);

                if (index >= total || data.length < 6 + len) continue;

                // ===== Anti-lag: drop frame cũ =====
                if (latestFrameId != -1) {
                    int diff = (frameId - latestFrameId) & 0xFFFF;
                    if (diff > 30000) continue; // frame quá cũ
                }

                latestFrameId = frameId;

                // ===== Limit buffer =====
                if (frameMap.size() > MAX_BUFFERED_FRAMES) {
                    dropOldestFrame();
                }

                // ===== Buffer frame =====
                FrameBuffer fb = frameMap.computeIfAbsent(frameId, k -> {
                    FrameBuffer f = new FrameBuffer();
                    f.chunks = new byte[total][];
                    f.expected = total;
                    f.firstSeen = System.currentTimeMillis();
                    return f;
                });

                if (fb.chunks[index] == null) {
                    fb.chunks[index] = Arrays.copyOfRange(data, 6, 6 + len);
                    fb.received++;
                }

                // ===== Frame complete =====
                if (fb.received == fb.expected) {
                    frameMap.remove(frameId);
                    handleCompleteFrame(fb);
                }

                cleanupTimeoutFrames();
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("🎥 VideoReceiver stopped");
        }
    }

    // ================= FRAME PROCESS =================
    private void handleCompleteFrame(FrameBuffer fb) {

        try {
            int size = 0;
            for (byte[] c : fb.chunks) size += c.length;

            byte[] full = new byte[size];
            int pos = 0;
            for (byte[] c : fb.chunks) {
                System.arraycopy(c, 0, full, pos, c.length);
                pos += c.length;
            }

            if (!keyManager.hasKey(callKey)) return;

            SecretKey key = keyManager.getOrCreate(callKey);

            if (full.length < 16) return;

            byte[] iv  = Arrays.copyOfRange(full, 0, 16);
            byte[] enc = Arrays.copyOfRange(full, 16, full.length);

            byte[] raw = CryptoUtils.decryptAES(
                    enc,
                    key,
                    new IvParameterSpec(iv)
            );

            Mat img = Imgcodecs.imdecode(
                    new MatOfByte(raw),
                    Imgcodecs.IMREAD_COLOR
            );

            if (img.empty()) return;

            Image fx = matToImage(img);
            Platform.runLater(() -> imageView.setImage(fx));

        } catch (Exception ignored) {
            // production: drop frame silently
        }
    }

    // ================= CLEANUP =================
    private void cleanupTimeoutFrames() {
        long now = System.currentTimeMillis();
        frameMap.entrySet().removeIf(
                e -> now - e.getValue().firstSeen > FRAME_TIMEOUT_MS
        );
    }

    private void dropOldestFrame() {
        frameMap.entrySet().stream()
                .min((a, b) -> Long.compare(
                        a.getValue().firstSeen,
                        b.getValue().firstSeen))
                .ifPresent(e -> frameMap.remove(e.getKey()));
    }

    // ================= CONTROL =================
    public void stopReceive() {
        running = false;
        if (socket != null) socket.close();
        Platform.runLater(() -> imageView.setImage(null));
    }

    // ================= UTILS =================
    private Image matToImage(Mat mat) {
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buf);
        return new Image(new ByteArrayInputStream(buf.toArray()));
    }
}
