package p2p;

import javax.sound.sampled.*;
import java.net.*;

public class VoiceSender extends Thread {
    private final InetAddress targetIP;
    private final int targetPort;
    private volatile boolean running = true;

    public VoiceSender(InetAddress ip, int port) {
        this.targetIP = ip;
        this.targetPort = port;
    }

    public void stopSend() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try {
            AudioFormat format = AudioUtils.getFormat();
            TargetDataLine mic = (TargetDataLine)
                    AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));

            mic.open(format);
            mic.start();

            DatagramSocket socket = new DatagramSocket();
            byte[] buffer = new byte[1024];

            while (running) {
                int count = mic.read(buffer, 0, buffer.length);
                DatagramPacket packet =
                        new DatagramPacket(buffer, count, targetIP, targetPort);
                socket.send(packet);
            }

            mic.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
