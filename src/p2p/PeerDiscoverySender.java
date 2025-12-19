package p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PeerDiscoverySender
 * --------------------
 * Gửi broadcast UDP để thông báo sự tồn tại của peer trong LAN
 * Format gói tin (UTF-8):
 *   DISCOVER|username|tcpPort
 */
public class PeerDiscoverySender extends Thread {

    private final String username;
    private final int servicePort;
    private final int discoveryPort;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public PeerDiscoverySender(String username, int servicePort, int discoveryPort) {
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

            while (running.get()) {
                String msg = "DISCOVER|" + username + "|" + servicePort;
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);

                // broadcast tới toàn mạng LAN
                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        InetAddress.getByName("255.255.255.255"),
                        discoveryPort
                );

                socket.send(packet);

                // gửi mỗi 2 giây
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            if (running.get()) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        running.set(false);
        interrupt();
    }
}
