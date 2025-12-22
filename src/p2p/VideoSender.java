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
    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int CHUNK_SIZE = 1300;

    private final InetAddress target;
    private final int port;
    private final KeyManager keyManager;
    private final String callKey;
    private final ImageView localPreview;
    private volatile boolean running = true;
    private short frameId = 0;

    public VideoSender(InetAddress target, int port, KeyManager keyManager, String callKey, ImageView localPreview) {
        this.target = target;
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
        this.localPreview = localPreview;
    }

    @Override
    public void run() {
        if (!OpenCVLoader.init()) return;

        VideoCapture cam = new VideoCapture(0);
        if (!cam.isOpened()) {
            System.err.println("❌ Cannot open camera");
            return;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            Mat frame = new Mat();
            while (running) {
                cam.read(frame);
                if (frame.empty()) continue;

                Imgproc.resize(frame, frame, new Size(WIDTH, HEIGHT));

                if (localPreview != null) {
                    Mat copy = frame.clone();
                    Image fx = matToImage(copy);
                    Platform.runLater(() -> localPreview.setImage(fx));
                    copy.release();
                }

                SecretKey key = keyManager.getOrCreate(callKey);
                if (key == null) continue;

                MatOfByte buf = new MatOfByte();
                Imgcodecs.imencode(".jpg", frame, buf);
                IvParameterSpec ivSpec = CryptoUtils.generateIv();
                byte[] encrypted = CryptoUtils.encryptAES(buf.toArray(), key, ivSpec);
                byte[] iv = ivSpec.getIV();

                byte[] payload = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, payload, 0, iv.length);
                System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

                int totalChunks = (int) Math.ceil(payload.length / (double) CHUNK_SIZE);
                frameId++;
                if (frameId < 0) frameId = 0; // reset khi overflow

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

                Thread.sleep(40);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cam.release();
        }
    }

    public void stopSend() {
        running = false;
        interrupt();
    }

    private Image matToImage(Mat mat) {
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buf);
        return new Image(new ByteArrayInputStream(buf.toArray()));
    }
}
