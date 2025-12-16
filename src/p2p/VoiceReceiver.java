package p2p;

import javax.sound.sampled.*;
import java.net.*;

public class VoiceReceiver extends Thread {
    private final int listenPort;
    private volatile boolean running = true;

    public VoiceReceiver(int port) {
        this.listenPort = port;
    }

    public void stopReceive() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try {
            AudioFormat format = AudioUtils.getFormat();
            SourceDataLine speaker = (SourceDataLine)
                    AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));

            speaker.open(format);
            speaker.start();

            DatagramSocket socket = new DatagramSocket(listenPort);
            byte[] buffer = new byte[1024];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                speaker.write(packet.getData(), 0, packet.getLength());
            }

            speaker.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
