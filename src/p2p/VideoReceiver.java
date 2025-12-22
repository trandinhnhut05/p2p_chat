package p2p;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VideoReceiver extends Thread {
    private static final int TIMEOUT_MS = 100; // Nếu frame chưa đầy 100ms -> bỏ
    private final int port;
    private final KeyManager keyManager;
    private final ImageView imageView;
    private final String callKey;
    private volatile boolean running = true;
    private DatagramSocket socket;

    private static class FrameBuffer {
        byte[][] chunks;
        long timestamp;
        int received = 0;
        int expected = -1;
    }

    private final Map<Short, FrameBuffer> frames = new ConcurrentHashMap<>();

    public VideoReceiver(int port, KeyManager keyManager, ImageView imageView, String callKey) {
        this.port = port;
        this.keyManager = keyManager;
        this.imageView = imageView;
        this.callKey = callKey;
    }

    @Override
    public void run() {

        if (!OpenCVLoader.init()) return;

        try {
            socket = new DatagramSocket(port);
            System.out.println("🎥 VideoReceiver started on port " + port);

            byte[] buf = new byte[1500];

            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());

                short frameId = (short) (((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
                int index = data[2] & 0xFF;
                int total = data[3] & 0xFF;
                int len = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);

                FrameBuffer fb = frames.computeIfAbsent(frameId, k -> {
                    FrameBuffer f = new FrameBuffer();
                    f.chunks = new byte[total][];
                    f.timestamp = System.currentTimeMillis();
                    f.expected = total;
                    return f;
                });

                if (fb.chunks[index] == null) {
                    fb.chunks[index] = Arrays.copyOfRange(data, 6, 6 + len);
                    fb.received++;
                }

                if (fb.received == fb.expected) {
                    // Gộp frame
                    int size = Arrays.stream(fb.chunks).mapToInt(b -> b.length).sum();
                    byte[] full = new byte[size];
                    int pos = 0;
                    for (byte[] c : fb.chunks) {
                        System.arraycopy(c, 0, full, pos, c.length);
                        pos += c.length;
                    }

                    frames.remove(frameId); // xong frame này

                    if (!keyManager.hasKey(callKey)) continue;
                    SecretKey key = keyManager.getOrCreate(callKey);
                    byte[] iv = Arrays.copyOfRange(full, 0, 16);
                    byte[] enc = Arrays.copyOfRange(full, 16, full.length);

                    byte[] raw = CryptoUtils.decryptAES(enc, key, new IvParameterSpec(iv));
                    Mat img = Imgcodecs.imdecode(new MatOfByte(raw), Imgcodecs.IMREAD_COLOR);
                    if (!img.empty()) {
                        Image fx = matToImage(img);
                        Platform.runLater(() -> imageView.setImage(fx));
                    }
                }

                // Clean old frames timeout
                long now = System.currentTimeMillis();
                frames.entrySet().removeIf(e -> now - e.getValue().timestamp > TIMEOUT_MS);
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    public void stopReceive() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        Platform.runLater(() -> imageView.setImage(null));
    }

    private Image matToImage(Mat mat) {
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, buf);
        return new Image(new ByteArrayInputStream(buf.toArray()));
    }
}
