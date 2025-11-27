package p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class PeerDiscoverySender extends Thread {
    private final String username;
    private final int servicePort;   // TCP port of this client
    private final int discoveryPort; // UDP discovery port (shared)
    private volatile boolean running = true;

    public PeerDiscoverySender(String username, int servicePort, int discoveryPort) {
        this.username = username;
        this.servicePort = servicePort;
        this.discoveryPort = discoveryPort;
        setDaemon(true);
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setReuseAddress(true);

            String localIP = InetAddress.getLocalHost().getHostAddress();

            while (running) {
                String msg = username + ";" + localIP + ";" + servicePort;
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);

                DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        InetAddress.getByName("255.255.255.255"),
                        discoveryPort
                );

                try {
                    socket.send(packet);
                } catch (Exception e) {
                    // continue - maybe transient network error
                }

                try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }
}
