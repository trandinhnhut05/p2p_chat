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
    private DatagramSocket ds;

    private final int BUFFER_SIZE = 1024;

    public VoiceReceiver(int port, KeyManager keyManager, String callKey) {
        this.port = port;
        this.keyManager = keyManager;
        this.callKey = callKey;
    }


    @Override
    public void run() {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        int frameSize = format.getFrameSize(); // 2 bytes

        // Thử bind port, nếu bị chiếm thì tăng dần
        int attempts = 0;
        while (attempts < 5) {
            try {
                ds = new DatagramSocket(port);
                System.out.println("VoiceReceiver listening on port " + port);
                break;
            } catch (Exception e) {
                port++;
                attempts++;
            }
        }

        if (ds == null) {
            System.err.println("❌ Cannot start VoiceReceiver, all ports busy");
            return;
        }

        try (SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info)) {
            speakers.open(format);
            speakers.start();

            byte[] buf = new byte[BUFFER_SIZE + 16]; // 16 byte IV

            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                try {
                    ds.receive(pkt);
                } catch (java.net.SocketException e) {
                    if (!running) break; // socket closed do stop
                    else throw e;
                }

                if (pkt.getLength() < 16) continue; // packet lỗi
                byte[] ivBytes = new byte[16];
                System.arraycopy(pkt.getData(), 0, ivBytes, 0, 16);
                IvParameterSpec iv = new IvParameterSpec(ivBytes);

                byte[] encrypted = new byte[pkt.getLength() - 16];
                System.arraycopy(pkt.getData(), 16, encrypted, 0, encrypted.length);

                SecretKey aes = keyManager.getOrCreate(callKey);
                if (aes == null) continue;

                byte[] decrypted = CryptoUtils.decryptAES(encrypted, aes, iv);

                // ⚡ đảm bảo số byte là bội số frame size
                int bytesToWrite = decrypted.length - (decrypted.length % frameSize);
                if (bytesToWrite > 0) {
                    speakers.write(decrypted, 0, bytesToWrite);
                }
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            if (ds != null && !ds.isClosed()) ds.close();
        }
    }


    public void stopReceive() {
        running = false;
        if (ds != null && !ds.isClosed()) ds.close();
    }

    public int getPort() {
        return port;
    }
}
