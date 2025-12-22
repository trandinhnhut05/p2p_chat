package p2p;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VideoSender extends Thread {

    private static final int CHUNK_SIZE = 1300;
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;

    private final InetAddress target;
    private final int port;
    private final KeyManager keyManager;
    private final String callKey;

    private volatile boolean running = true;
    private short frameId = 0;
    private final ImageView localPreview;

    public VideoSender(InetAddress target, int port,
                       KeyManager keyManager, String callKey, ImageView localPreview) {
        this.target = target;
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
        this.localPreview = localPreview;
    }

    @Override
    public void run() {
        VideoCapture cam = new VideoCapture(0, Videoio.CAP_DSHOW);
        if (!cam.isOpened()) {
            System.err.println("❌ Cannot open camera");
            return;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            Mat frame = new Mat();

            while (running) {
                SecretKey key = keyManager.getOrCreate(callKey);
                if (key == null) {
                    Thread.sleep(50);
                    continue;
                }

                cam.read(frame);
                if (frame.empty()) continue;

                Imgproc.resize(frame, frame, new Size(WIDTH, HEIGHT));

                // ================= UPDATE SELF-PREVIEW =================
                if (localPreview != null) {
                    Mat copy = frame.clone();
                    Image fxImage = matToImage(copy);
                    Platform.runLater(() -> localPreview.setImage(fxImage));
                    copy.release();
                }

                MatOfByte jpg = new MatOfByte();
                Imgcodecs.imencode(".jpg", frame, jpg);
                byte[] raw = jpg.toArray();

                IvParameterSpec iv = CryptoUtils.generateIv();
                byte[] encrypted = CryptoUtils.encryptAES(raw, key, iv);

                byte[] payload = new byte[16 + encrypted.length];
                System.arraycopy(iv.getIV(), 0, payload, 0, 16);
                System.arraycopy(encrypted, 0, payload, 16, encrypted.length);

                int totalChunks = (int) Math.ceil(payload.length / (double) CHUNK_SIZE);
                frameId++;

                for (int i = 0; i < totalChunks; i++) {
                    int off = i * CHUNK_SIZE;
                    int len = Math.min(CHUNK_SIZE, payload.length - off);

                    byte[] packet = new byte[6 + len];
                    packet[0] = (byte) (frameId >> 8);
                    packet[1] = (byte) frameId;
                    packet[2] = (byte) i;
                    packet[3] = (byte) totalChunks;
                    packet[4] = (byte) (len >> 8);
                    packet[5] = (byte) len;

                    System.arraycopy(payload, off, packet, 6, len);
                    socket.send(new DatagramPacket(packet, packet.length, target, port));
                }

                Thread.sleep(40); // ~25 FPS
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cam.release();
        }
    }
    private Image matToImage(Mat frame) {
        try {
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".png", frame, buffer);
            return new Image(new ByteArrayInputStream(buffer.toArray()));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void stopSend() {
        running = false;
        interrupt();
    }
}
