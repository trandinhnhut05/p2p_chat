package p2p;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerDiscoveryListener extends Thread {

    public static final long PEER_TIMEOUT_MS = 5000;

    private final int discoveryPort;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();

    public PeerDiscoveryListener(int discoveryPort) {
        this.discoveryPort = discoveryPort;
        setName("PeerDiscoveryListener");
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            // 🔥 BẮT BUỘC bind 0.0.0.0
            InetAddress bindAddr = InetAddress.getByName("0.0.0.0");
            DatagramSocket socket =
                    new DatagramSocket(
                            new InetSocketAddress(bindAddr, discoveryPort));

            socket.setReuseAddress(true);
            socket.setBroadcast(true);

            byte[] buffer = new byte[2048];

            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(
                        packet.getData(), 0,
                        packet.getLength(),
                        StandardCharsets.UTF_8
                );

                handlePacket(
                        packet.getAddress().getHostAddress(),
                        msg
                );
            }
        } catch (Exception e) {
            if (running.get()) e.printStackTrace();
        }
    }

    private void handlePacket(String ip, String msg) {
        try {
            if (!msg.startsWith("DISCOVER|")) return;

            String[] parts = msg.split("\\|");
            if (parts.length < 3) return;

            String username = parts[1];
            int port = Integer.parseInt(parts[2]);

            String key = ip + ":" + port;

            Peer peer = peers.get(key);
            if (peer == null) {
                peer = new Peer(
                        InetAddress.getByName(ip),
                        port,
                        username,
                        key
                );
                peers.put(key, peer);
            }
            peer.updateLastSeen();

        } catch (Exception ignored) {}
    }

    public List<Peer> snapshot() {
        long now = System.currentTimeMillis();
        List<Peer> list = new ArrayList<>();
        for (Peer p : peers.values()) {
            if (now - p.getLastSeen() <= PEER_TIMEOUT_MS)
                list.add(p);
        }
        return list;
    }

    public void shutdown() {
        running.set(false);
        interrupt();
    }
}
