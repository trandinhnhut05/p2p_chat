package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import com.github.sarxos.webcam.Webcam;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import javax.imageio.ImageIO;

public class VideoSender extends Thread {

    private final InetAddress target;
    private final int port;
    private final KeyManager keyManager;
    private volatile boolean running = true;
    private final Webcam webcam;

    public VideoSender(InetAddress target, int port, KeyManager keyManager) {
        this.target = target;
        this.port = port;
        this.keyManager = keyManager;

        webcam = Webcam.getDefault();
        webcam.setViewSize(new Dimension(320, 240)); // 🔴 bắt buộc
        webcam.open();
    }

    @Override
    public void run() {
        try (DatagramSocket ds = new DatagramSocket()) {

            while (running) {
                BufferedImage img = webcam.getImage();
                if (img == null) continue;

                String peerId = target.getHostAddress() + ":" + port;
                SecretKey aes = keyManager.getSessionKey(peerId);


                if (aes == null) {
                    System.out.println("❌ No AES key for video peer: " + peerId);
                    continue;
                }


                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", baos);
                byte[] frameData = baos.toByteArray();

                // 🔴 tránh UDP overflow
                if (frameData.length > 60000) continue;

                IvParameterSpec iv = CryptoUtils.generateIv();
                byte[] encrypted = CryptoUtils.encryptAES(frameData, aes, iv);

                byte[] sendData = new byte[16 + encrypted.length];
                System.arraycopy(iv.getIV(), 0, sendData, 0, 16);
                System.arraycopy(encrypted, 0, sendData, 16, encrypted.length);

                DatagramPacket pkt =
                        new DatagramPacket(sendData, sendData.length, target, port);

                ds.send(pkt);
                Thread.sleep(33); // ~30 FPS
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            webcam.close();
        }
    }

    public void stopSend() {
        running = false;
        interrupt();
    }
}
