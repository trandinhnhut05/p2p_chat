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

public class VideoSender extends Thread {

    // ===== Video config =====
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int CHUNK_SIZE = 1300;
    private static final int FPS_DELAY_MS = 40; // ~25fps

    // ===== Network / crypto =====
    private final InetAddress target;
    private final int port;
    private final KeyManager keyManager;
    private final String callKey;

    // ===== UI =====
    private final ImageView localPreview;

    // ===== Thread state =====
    private volatile boolean running = true;
    private volatile boolean paused = false;


    // 16-bit frame id (0 → 65535)
    private int frameId = 0;

    public VideoSender(InetAddress target,
                       int port,
                       KeyManager keyManager,
                       String callKey,
                       ImageView localPreview) {

        this.target = target;
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
        this.localPreview = localPreview;
    }

    // ================= THREAD =================
    @Override
    public void run() {

        System.out.println("🎥 VideoSender STARTED -> "
                + target.getHostAddress() + ":" + port
                + " callKey=" + callKey);

        if (!OpenCVLoader.init()) {
            System.err.println("❌ OpenCV init failed (VideoSender)");
            return;
        }

        VideoCapture cam = new VideoCapture(0);
        if (!cam.isOpened()) {
            System.err.println("❌ Cannot open camera");
            return;
        }
        System.out.println("📷 Camera opened");

        try (DatagramSocket socket = new DatagramSocket()) {

            Mat frame = new Mat();

            while (running) {

                // ===== Pause handling =====
                if (paused) {
                    Thread.sleep(50);
                    continue;
                }

                // ===== Capture =====
                cam.read(frame);
                if (frame.empty()) continue;

                Imgproc.resize(frame, frame, new Size(WIDTH, HEIGHT));

                // ===== Local preview =====
                if (localPreview != null) {
                    Mat copy = frame.clone();
                    Image fx = matToImage(copy);
                    Platform.runLater(() -> localPreview.setImage(fx));
                    copy.release();
                }

                // ===== Encryption =====
                SecretKey key = keyManager.getOrCreate(callKey);
                if (key == null) continue;

                MatOfByte jpg = new MatOfByte();
                Imgcodecs.imencode(".jpg", frame, jpg);

                IvParameterSpec ivSpec = CryptoUtils.generateIv();
                byte[] encrypted = CryptoUtils.encryptAES(
                        jpg.toArray(),
                        key,
                        ivSpec
                );

                byte[] iv = ivSpec.getIV();
                byte[] payload = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, payload, 0, iv.length);
                System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

                // ===== Chunking =====
                int totalChunks = (int) Math.ceil(
                        payload.length / (double) CHUNK_SIZE
                );

                frameId = (frameId + 1) & 0xFFFF; // 16-bit wrap

                for (int i = 0; i < totalChunks; i++) {
                    int off = i * CHUNK_SIZE;
                    int len = Math.min(CHUNK_SIZE, payload.length - off);

                    byte[] packet = new byte[6 + len];

                    // Header
                    packet[0] = (byte) (frameId >> 8);
                    packet[1] = (byte) frameId;
                    packet[2] = (byte) i;
                    packet[3] = (byte) totalChunks;
                    packet[4] = (byte) (len >> 8);
                    packet[5] = (byte) len;

                    // Payload
                    System.arraycopy(payload, off, packet, 6, len);

                    socket.send(
                            new DatagramPacket(packet, packet.length, target, port)
                    );
                }

                Thread.sleep(FPS_DELAY_MS);
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            cam.release();
            System.out.println("🎥 VideoSender STOPPED");
        }
    }

    // ================= CONTROL =================
    public void stopSend() {
        running = false;
        interrupt();
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    // ================= UTILS =================
    private Image matToImage(Mat mat) {
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buf);
        return new Image(new ByteArrayInputStream(buf.toArray()));
    }
}
