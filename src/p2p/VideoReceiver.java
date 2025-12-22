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
    private final String callKey;
    private volatile boolean running = true;
    private DatagramSocket socket;

    private byte[][] chunks;
    private int received = 0;
    private int expected = -1;
    private short currentFrame = -1;

    public VideoReceiver(int port, KeyManager keyManager, ImageView imageView, String callKey) {
        this.port = port;
        this.keyManager = keyManager;
        this.imageView = imageView;
        this.callKey = callKey;
    }

    @Override
    public void run() {
        if (!OpenCVLoader.init()) return; // kiểm tra OpenCV

        try {
            socket = new DatagramSocket(port);
            byte[] buf = new byte[1500];

            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                byte[] data = pkt.getData();

                short frameId = (short) (((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
                int index = data[2] & 0xFF;
                int total = data[3] & 0xFF;
                int len = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);

                if (frameId != currentFrame) {
                    currentFrame = frameId;
                    chunks = new byte[total][];
                    received = 0;
                    expected = total;
                }

                if (chunks[index] == null) {
                    chunks[index] = Arrays.copyOfRange(data, 6, 6 + len);
                    received++;
                }

                if (received == expected) {
                    int size = Arrays.stream(chunks).mapToInt(b -> b.length).sum();
                    byte[] full = new byte[size];
                    int pos = 0;
                    for (byte[] c : chunks) {
                        System.arraycopy(c, 0, full, pos, c.length);
                        pos += c.length;
                    }

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
                    chunks = null;
                    received = 0;
                    expected = -1;
                }
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
