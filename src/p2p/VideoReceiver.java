package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class VideoReceiver extends Thread {

    private final int port;
    private final KeyManager keyManager;
    private volatile boolean running = true;
    private final ImageView imageView;

    public VideoReceiver(int port, KeyManager keyManager, ImageView imageView) {
        this.port = port;
        this.keyManager = keyManager;
        this.imageView = imageView;
    }

    @Override
    public void run() {
        try (DatagramSocket ds = new DatagramSocket(port)) {
            byte[] buf = new byte[65535];

            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                ds.receive(pkt);

                if (pkt.getLength() < 20) continue;

                byte[] ivBytes = new byte[16];
                System.arraycopy(pkt.getData(), 0, ivBytes, 0, 16);
                IvParameterSpec iv = new IvParameterSpec(ivBytes);

                byte[] encrypted = new byte[pkt.getLength() - 16];
                System.arraycopy(pkt.getData(), 16, encrypted, 0, encrypted.length);

                String peerId = pkt.getAddress().getHostAddress();
                SecretKey aes = keyManager.getSessionKey(peerId);
                if (aes == null) continue;

                byte[] decrypted = CryptoUtils.decryptAES(encrypted, aes, iv);
                if (decrypted == null || decrypted.length == 0) continue;

                Image img = new Image(new ByteArrayInputStream(decrypted));
                if (img.isError()) continue;

                Platform.runLater(() -> imageView.setImage(img));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopReceive() {
        running = false;
        interrupt();
    }
}
