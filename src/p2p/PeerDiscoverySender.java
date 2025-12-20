package p2p;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerDiscoverySender extends Thread {

    private final String username;
    private final int servicePort;
    private final int discoveryPort;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PeerDiscoverySender(String username,
                               int servicePort,
                               int discoveryPort) {
        this.username = username;
        this.servicePort = servicePort;
        this.discoveryPort = discoveryPort;
        setName("PeerDiscoverySender");
        setDaemon(true);
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {

            socket.setBroadcast(true);

            // 🔥 DÙNG BROADCAST SUBNET THẬT
            InetAddress broadcast =
                    InetAddress.getByName("192.168.1.255");

            while (running.get()) {
                String msg =
                        "DISCOVER|" + username + "|" + servicePort;

                byte[] data =
                        msg.getBytes(StandardCharsets.UTF_8);

                DatagramPacket packet =
                        new DatagramPacket(
                                data,
                                data.length,
                                broadcast,
                                discoveryPort
                        );

                socket.send(packet);
                Thread.sleep(2000);
            }

        } catch (Exception e) {
            if (running.get()) e.printStackTrace();
        }
    }

    public void shutdown() {
        running.set(false);
        interrupt();
    }
}
