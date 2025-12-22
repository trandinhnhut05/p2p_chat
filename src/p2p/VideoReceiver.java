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

    /* ================= CONFIG ================= */

    private static final int MAX_PACKET_SIZE = 1500;
    private static final int FRAME_TIMEOUT_MS = 800;
    private static final int MAX_INFLIGHT_FRAMES = 50;

    /* ================= STATE ================= */

    private final int port;
    private final KeyManager keyManager;
    private final ImageView imageView;
    private final String callKey;

    private volatile boolean running = true;
    private DatagramSocket socket;

    /* ================= FRAME BUFFER ================= */

    private static class FrameBuffer {
        byte[][] chunks;
        int received;
        int expected;
        long lastUpdate;
    }

    private final Map<Integer, FrameBuffer> frames = new ConcurrentHashMap<>();

    /* ================= CONSTRUCTOR ================= */

    public VideoReceiver(int port, KeyManager keyManager, ImageView imageView, String callKey) {
        this.port = port;
        this.keyManager = keyManager;
        this.imageView = imageView;
        this.callKey = callKey;
        setName("VideoReceiver-" + port);
        setDaemon(true);
    }

    /* ================= THREAD ================= */

    @Override
    public void run() {

        if (!OpenCVLoader.init()) {
            System.err.println("❌ OpenCV init failed (VideoReceiver)");
            return;
        }

        try {
            socket = new DatagramSocket(port);
            System.out.println("🎥 VideoReceiver listening on port " + port);

            byte[] buf = new byte[MAX_PACKET_SIZE];

            while (running) {

                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
                if (data.length < 6) continue;

                /* -------- HEADER -------- */
                int frameId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                int index   = data[2] & 0xFF;
                int total   = data[3] & 0xFF;
                int len     = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);

                if (total <= 0 || index >= total) continue;

                /* -------- FRAME BUFFER -------- */
                FrameBuffer fb = frames.computeIfAbsent(frameId, id -> {
                    FrameBuffer f = new FrameBuffer();
                    f.chunks = new byte[total][];
                    f.expected = total;
                    f.received = 0;
                    f.lastUpdate = System.currentTimeMillis();
                    return f;
                });

                if (fb.chunks[index] == null) {
                    fb.chunks[index] = Arrays.copyOfRange(data, 6, 6 + len);
                    fb.received++;
                    fb.lastUpdate = System.currentTimeMillis();
                }

                /* -------- COMPLETE FRAME -------- */
                if (fb.received == fb.expected) {
                    frames.remove(frameId);
                    processFrame(fb);
                }

                cleanupOldFrames();
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            closeSocket();
        }
    }

    /* ================= FRAME PROCESS ================= */

    private void processFrame(FrameBuffer fb) {

        try {
            int size = Arrays.stream(fb.chunks).mapToInt(b -> b.length).sum();
            byte[] full = new byte[size];

            int pos = 0;
            for (byte[] c : fb.chunks) {
                System.arraycopy(c, 0, full, pos, c.length);
                pos += c.length;
            }

            if (!keyManager.hasKey(callKey)) return;
            SecretKey key = keyManager.getOrCreate(callKey);

            byte[] iv  = Arrays.copyOfRange(full, 0, 16);
            byte[] enc = Arrays.copyOfRange(full, 16, full.length);

            byte[] raw = CryptoUtils.decryptAES(enc, key, new IvParameterSpec(iv));
            Mat img = Imgcodecs.imdecode(new MatOfByte(raw), Imgcodecs.IMREAD_COLOR);

            if (img.empty()) return;

            Image fx = matToImage(img);
            Platform.runLater(() -> imageView.setImage(fx));

        } catch (Exception ignored) {
            // Drop corrupted frame silently
        }
    }

    /* ================= CLEANUP ================= */

    private void cleanupOldFrames() {
        long now = System.currentTimeMillis();

        frames.entrySet().removeIf(e ->
                now - e.getValue().lastUpdate > FRAME_TIMEOUT_MS
        );

        if (frames.size() > MAX_INFLIGHT_FRAMES) {
            frames.clear(); // panic cleanup
        }
    }

    /* ================= STOP ================= */

    public void stopReceive() {
        running = false;
        closeSocket();
        Platform.runLater(() -> imageView.setImage(null));
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /* ================= UTIL ================= */

    private Image matToImage(Mat mat) {
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, buf);
        return new Image(new ByteArrayInputStream(buf.toArray()));
    }
}
