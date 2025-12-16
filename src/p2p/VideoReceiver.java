package p2p;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class VideoReceiver extends Thread {
    private final int port;
    private final ImageView imageView;
    private volatile boolean running = true;

    public VideoReceiver(int port, ImageView view) {
        this.port = port;
        this.imageView = view;
    }

    public void stopReceive() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try {
            DatagramSocket socket = new DatagramSocket(port);
            byte[] buffer = new byte[65507];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ByteArrayInputStream bais =
                        new ByteArrayInputStream(packet.getData(), 0, packet.getLength());

                Image img = new Image(bais);

                Platform.runLater(() -> imageView.setImage(img));
            }

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
