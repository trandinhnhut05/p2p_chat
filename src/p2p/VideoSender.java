package p2p;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import org.opencv.videoio.Videoio;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VideoSender extends Thread {

    private static final int CHUNK_SIZE = 1400; // an toàn cho UDP
    private static final int FRAME_WIDTH = 320;
    private static final int FRAME_HEIGHT = 240;

    private final InetAddress target;
    private final int port;
    private final KeyManager keyManager;
    private volatile boolean running = true;

    static {
        System.loadLibrary("opencv_java4120");
    }

    private final String callKey;

    public VideoSender(InetAddress target, int port,
                       KeyManager keyManager, String callKey) {
        this.target = target;
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
    }


    @Override
    public void run() {
        VideoCapture camera = new VideoCapture(0, Videoio.CAP_DSHOW);

        if (!camera.isOpened()) {
            System.err.println("❌ Cannot open camera");
            return;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            Mat frame = new Mat();

            while (running) {
                camera.read(frame);
                if (frame.empty()) continue;

                Imgproc.resize(frame, frame,
                        new Size(FRAME_WIDTH, FRAME_HEIGHT));

                MatOfByte buffer = new MatOfByte();
                Imgcodecs.imencode(".jpg", frame, buffer);
                byte[] rawFrame = buffer.toArray();

                // Encrypt
                SecretKey aes = keyManager.getOrCreate(callKey);
                IvParameterSpec iv = CryptoUtils.generateIv();
                byte[] encrypted = CryptoUtils.encryptAES(rawFrame, aes, iv);

                // Gộp IV + encrypted
                byte[] payload = new byte[16 + encrypted.length];
                System.arraycopy(iv.getIV(), 0, payload, 0, 16);
                System.arraycopy(encrypted, 0, payload, 16, encrypted.length);

                // Chia chunk UDP
                int totalChunks = (int) Math.ceil(payload.length / (double) CHUNK_SIZE);

                for (int i = 0; i < totalChunks; i++) {
                    int offset = i * CHUNK_SIZE;
                    int len = Math.min(CHUNK_SIZE, payload.length - offset);

                    byte[] chunk = new byte[len + 2];
                    chunk[0] = (byte) i;               // chunk index
                    chunk[1] = (byte) totalChunks;     // total chunks
                    System.arraycopy(payload, offset, chunk, 2, len);

                    socket.send(new DatagramPacket(chunk, chunk.length, target, port));
                }

                Thread.sleep(40); // ~25 FPS
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            camera.release();
        }
    }

    public void stopSend() {
        running = false;
        interrupt();
    }
}
