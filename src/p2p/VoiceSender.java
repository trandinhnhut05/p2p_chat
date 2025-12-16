package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VoiceSender extends Thread {
    private final InetAddress target;
    private final int port;
    private final KeyManager keyManager;
    private volatile boolean running = true;

    public VoiceSender(InetAddress target, int port, KeyManager keyManager) {
        this.target = target;
        this.port = port;
        this.keyManager = keyManager;
    }

    @Override
    public void run() {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        try (DatagramSocket ds = new DatagramSocket();
             TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info)) {

            microphone.open(format);
            microphone.start();

            byte[] buffer = new byte[1024];

            while (running) {
                int n = microphone.read(buffer, 0, buffer.length);
                if (n > 0) {
                    SecretKey aes = keyManager.getSessionKey(target.getHostAddress());
                    if (aes == null) continue;

                    IvParameterSpec iv = CryptoUtils.generateIv();
                    byte[] encrypted = CryptoUtils.encryptAES(buffer, aes, iv);

                    byte[] sendData = new byte[iv.getIV().length + encrypted.length];
                    System.arraycopy(iv.getIV(), 0, sendData, 0, iv.getIV().length);
                    System.arraycopy(encrypted, 0, sendData, iv.getIV().length, encrypted.length);

                    ds.send(new DatagramPacket(sendData, sendData.length, target, port));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopSend() {
        running = false;
        this.interrupt();
    }
}
