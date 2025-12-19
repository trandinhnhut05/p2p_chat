package p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PeerDiscoveryListener
 * ----------------------
 * Lắng nghe UDP broadcast trong LAN để phát hiện peer online
 * Format gói tin:
 *   DISCOVER|username|tcpPort
 */
public class PeerDiscoveryListener extends Thread {

    public static final long PEER_TIMEOUT_MS = 5000; // 5s coi như offline

    private final int discoveryPort;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // key = ip:port
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();

    public PeerDiscoveryListener(int discoveryPort) {
        this.discoveryPort = discoveryPort;
        setName("PeerDiscoveryListener");
        setDaemon(true);
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(discoveryPort)) {
            byte[] buffer = new byte[2048];

            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                InetAddress addr = packet.getAddress();

                handlePacket(addr.getHostAddress(), msg);
            }
        } catch (Exception e) {
            if (running.get()) {
                e.printStackTrace();
            }
        }
    }

    private void handlePacket(String ip, String msg) {
        try {
            if (!msg.startsWith("DISCOVER|")) return;

            String[] parts = msg.split("\\|");
            if (parts.length < 3) return;

            String username = parts[1];
            int port = Integer.parseInt(parts[2]);

            String fingerprint = ip + ":" + port;
            String key = fingerprint;

            Peer peer = peers.get(key);

            if (peer == null) {
                peer = new Peer(
                        InetAddress.getByName(ip),
                        port,
                        username,
                        fingerprint
                );
                peers.put(key, peer);
            }

            peer.updateLastSeen();

        } catch (Exception ignored) {
        }
    }


    /**
     * Lấy snapshot danh sách peer (dùng cho UI thread)
     */
    public List<Peer> snapshot() {
        long now = System.currentTimeMillis();
        List<Peer> list = new ArrayList<>();

        for (Peer p : peers.values()) {
            if (now - p.getLastSeen() <= PEER_TIMEOUT_MS) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Remove peer theo IP (dùng khi user xóa thủ công)
     */
    public boolean removePeer(String ip) {
        return peers.entrySet().removeIf(e -> e.getValue().getIp().equals(ip));
    }

    public void shutdown() {
        running.set(false);
        interrupt();
    }
}
