package p2p;

import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.*;

public class VideoSender extends Thread {
    private final InetAddress targetIp;
    private final int port;
    private volatile boolean running = true;

    public VideoSender(InetAddress ip, int port) {
        this.targetIp = ip;
        this.port = port;
    }

    public void stopSend() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try {
            Webcam webcam = Webcam.getDefault();
            webcam.open();

            DatagramSocket socket = new DatagramSocket();

            while (running) {
                BufferedImage image = webcam.getImage();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);

                byte[] data = baos.toByteArray();

                DatagramPacket packet =
                        new DatagramPacket(data, data.length, targetIp, port);

                socket.send(packet);

                Thread.sleep(50); // ~20 FPS
            }

            webcam.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
