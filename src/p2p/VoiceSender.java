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
    private final String callKey;
    private volatile boolean running = true;

    private static final int BUFFER_SIZE = 640;

    public VoiceSender(InetAddress target, int port,
                       KeyManager keyManager, String callKey) {
        this.target = target;
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
    }

    @Override
    public void run() {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            try (DatagramSocket ds = new DatagramSocket();
                 TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info)) {

                microphone.open(format);
                microphone.start();
                byte[] buffer = new byte[BUFFER_SIZE];

                while (running) {
                    int n = microphone.read(buffer, 0, buffer.length);
                    if (n <= 0) continue;

                    SecretKey key = keyManager.getOrCreate(callKey);
                    IvParameterSpec iv = CryptoUtils.generateIv();
                    byte[] encrypted = CryptoUtils.encryptAES(buffer, key, iv);

                    byte[] sendData = new byte[iv.getIV().length + encrypted.length];
                    System.arraycopy(iv.getIV(), 0, sendData, 0, iv.getIV().length);
                    System.arraycopy(encrypted, 0, sendData, iv.getIV().length, encrypted.length);

                    ds.send(new DatagramPacket(sendData, sendData.length, target, port));
                }
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    public void stopSend() {
        running = false;
        interrupt();
    }
}
