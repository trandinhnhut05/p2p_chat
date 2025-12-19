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

public class VideoReceiver extends Thread {

    private final int port;
    private final KeyManager keyManager;
    private final ImageView imageView;
    private volatile boolean running = true;

    private byte[][] chunks;
    private int receivedChunks = 0;
    private int expectedChunks = -1;
    private DatagramSocket socket;


    static {
        System.loadLibrary("opencv_java4120");
    }

    private final String callKey;

    public VideoReceiver(int port, KeyManager keyManager,
                         ImageView imageView, String callKey) {
        this.port = port;
        this.keyManager = keyManager;
        this.imageView = imageView;
        this.callKey = callKey;
    }


    @Override
    public void run() {
        try  {
            socket = new DatagramSocket(port);
            byte[] buffer = new byte[1500];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                if (!running) break;
                int index = packet.getData()[0] & 0xFF;
                int total = packet.getData()[1] & 0xFF;

                if (chunks == null || expectedChunks != total) {
                    chunks = new byte[total][];
                    receivedChunks = 0;
                    expectedChunks = total;
                }

                byte[] data = Arrays.copyOfRange(packet.getData(), 2, packet.getLength());
                chunks[index] = data;
                receivedChunks++;

                // Nhận đủ frame
                if (receivedChunks == expectedChunks) {
                    int size = Arrays.stream(chunks).mapToInt(a -> a.length).sum();
                    byte[] full = new byte[size];
                    int pos = 0;

                    for (byte[] c : chunks) {
                        System.arraycopy(c, 0, full, pos, c.length);
                        pos += c.length;
                    }

                    // Tách IV + encrypted
                    byte[] ivBytes = Arrays.copyOfRange(full, 0, 16);
                    byte[] encrypted = Arrays.copyOfRange(full, 16, full.length);

                    SecretKey aes = keyManager.getOrCreate(callKey);

                    byte[] decrypted = CryptoUtils.decryptAES(
                            encrypted,
                            aes,
                            new IvParameterSpec(ivBytes)
                    );

                    Mat img = Imgcodecs.imdecode(
                            new MatOfByte(decrypted),
                            Imgcodecs.IMREAD_COLOR
                    );

                    if (!img.empty()) {
                        Image fxImg = matToImage(img);
                        Platform.runLater(() -> imageView.setImage(fxImg));
                    }

                    chunks = null;
                    receivedChunks = 0;
                }
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    public void stopReceive() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close(); // 🔥 QUAN TRỌNG
        }
        Platform.runLater(() -> imageView.setImage(null));
    }



    private static Image matToImage(Mat mat) {
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, buf);
        return new Image(new ByteArrayInputStream(buf.toArray()));
    }
}
