package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class VoiceReceiver extends Thread {

    private int port;
    private final KeyManager keyManager;
    private final String callKey;
    private volatile boolean running = true;
    private DatagramSocket socket;

    private static final int AUDIO_PAYLOAD = 640; // 20ms @ 16kHz, 16bit mono
    private static final int IV_SIZE = 16;

    public VoiceReceiver(int port, KeyManager keyManager, String callKey) {
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
    }

    @Override
    public void run() {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            socket = new DatagramSocket(port);
            System.out.println("🎧 VoiceReceiver listening on port " + port);

            try (SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info)) {
                speakers.open(format);
                speakers.start();

                byte[] buffer = new byte[AUDIO_PAYLOAD + IV_SIZE];

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    if (!keyManager.hasKey(callKey)) continue;
                    if (packet.getLength() <= IV_SIZE) continue;

                    byte[] ivBytes = new byte[IV_SIZE];
                    System.arraycopy(packet.getData(), 0, ivBytes, 0, IV_SIZE);
                    byte[] encrypted = new byte[packet.getLength() - IV_SIZE];
                    System.arraycopy(packet.getData(), IV_SIZE, encrypted, 0, encrypted.length);

                    SecretKey key = keyManager.getSessionKey(callKey);
                    if (key == null) continue;

                    byte[] decrypted;
                    try {
                        decrypted = CryptoUtils.decryptAES(encrypted, key, new IvParameterSpec(ivBytes));
                    } catch (Exception e) {
                        continue;
                    }

                    int frameSize = format.getFrameSize();
                    int validBytes = decrypted.length - (decrypted.length % frameSize);
                    if (validBytes > 0) speakers.write(decrypted, 0, validBytes);
                }
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    public void stopReceive() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }
}
