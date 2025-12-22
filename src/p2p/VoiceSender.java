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

    private static final int BUFFER_SIZE = 640; // 20ms @16kHz

    public VoiceSender(InetAddress target, int port, KeyManager keyManager, String callKey) {
        this.target = target;
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
    }

    @Override
    public void run() {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
        try (DatagramSocket ds = new DatagramSocket();
             TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format))) {

            mic.open(format);
            mic.start();
            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                int read = mic.read(buffer, 0, buffer.length);
                if (read <= 0) continue;

                // Nếu key chưa tạo → skip frame
                SecretKey key = keyManager.getOrCreate(callKey);
                if (key == null) continue;

                IvParameterSpec iv = CryptoUtils.generateIv();
                byte[] encrypted = CryptoUtils.encryptAES(buffer, key, iv);

                byte[] sendData = new byte[iv.getIV().length + encrypted.length];
                System.arraycopy(iv.getIV(), 0, sendData, 0, iv.getIV().length);
                System.arraycopy(encrypted, 0, sendData, iv.getIV().length, encrypted.length);

                ds.send(new DatagramPacket(sendData, sendData.length, target, port));

                Thread.sleep(20); // đảm bảo rate ổn định ~50FPS
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
