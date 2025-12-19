package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class VoiceReceiver extends Thread {
    private final int port;
    private final KeyManager keyManager;
    private volatile boolean running = true;
    private DatagramSocket ds;


    private final String callKey;

    public VoiceReceiver(int port, KeyManager keyManager, String callKey) {
        this.port = port;
        this.keyManager=keyManager;
        this.callKey = callKey;
    }


    @Override
    public void run() {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        try (
             SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info)) {
            ds = new DatagramSocket(port);
            speakers.open(format);
            speakers.start();

            byte[] buf = new byte[2048];

            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                ds.receive(pkt);

                if (!running) break;
                byte[] ivBytes = new byte[16];
                System.arraycopy(pkt.getData(), 0, ivBytes, 0, 16);
                IvParameterSpec iv = new IvParameterSpec(ivBytes);

                byte[] encrypted = new byte[pkt.getLength() - 16];
                System.arraycopy(pkt.getData(), 16, encrypted, 0, encrypted.length);

//                String senderIp = pkt.getAddress().getHostAddress();
                SecretKey aes = keyManager.getOrCreate(callKey);
                if (aes == null) continue;

                byte[] decrypted = CryptoUtils.decryptAES(encrypted, aes, iv);
                speakers.write(decrypted, 0, decrypted.length);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopReceive() {
        running = false;
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

}
