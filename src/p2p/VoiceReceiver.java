package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ArrayBlockingQueue;

public class VoiceReceiver extends Thread {

    private final int port;
    private final KeyManager keyManager;
    private final String callKey;
    private volatile boolean running = true;
    private DatagramSocket socket;

    private static final int AUDIO_PAYLOAD = 640; // 20ms @16kHz mono 16bit
    private static final int IV_SIZE = 16;
    private final ArrayBlockingQueue<byte[]> audioQueue = new ArrayBlockingQueue<>(50);

    public VoiceReceiver(int port, KeyManager keyManager, String callKey) {
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
    }

    @Override
    public void run() {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
        try (DatagramSocket ds = new DatagramSocket(port);
             SourceDataLine speakers = AudioSystem.getSourceDataLine(format)) {

            socket = ds;
            speakers.open(format);
            speakers.start();

            // Thread phụ để playback liên tục
            Thread playback = new Thread(() -> {
                try {
                    while (running) {
                        byte[] data = audioQueue.take();
                        speakers.write(data, 0, data.length);
                    }
                } catch (InterruptedException ignored) {}
            });
            playback.start();

            byte[] buffer = new byte[AUDIO_PAYLOAD + IV_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                if (!keyManager.hasKey(callKey) || packet.getLength() <= IV_SIZE) continue;

                byte[] iv = new byte[IV_SIZE];
                System.arraycopy(packet.getData(), 0, iv, 0, IV_SIZE);
                byte[] encrypted = new byte[packet.getLength() - IV_SIZE];
                System.arraycopy(packet.getData(), IV_SIZE, encrypted, 0, encrypted.length);

                SecretKey key = keyManager.getSessionKey(callKey);
                if (key == null) continue;

                try {
                    byte[] decrypted = CryptoUtils.decryptAES(encrypted, key, new IvParameterSpec(iv));
                    // bỏ frame không đủ
                    if (decrypted.length >= AUDIO_PAYLOAD) {
                        audioQueue.offer(decrypted);
                    }
                } catch (Exception ignored) {}
            }

            playback.interrupt();

        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    public void stopReceive() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        interrupt();
    }
}
