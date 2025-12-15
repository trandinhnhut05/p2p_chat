package p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Discovery listener that:
 * - binds with setReuseAddress(true)
 * - updates peer.lastSeen on each discovery packet
 * - parses optional fingerprint field
 */
public class PeerDiscoveryListener extends Thread {
    private final List<Peer> peers = new ArrayList<>();
    private volatile boolean running = true;
    private final int discoveryPort;

    public static final long PEER_TIMEOUT_MS = 8_000L;

    public PeerDiscoveryListener(int discoveryPort) {
        this.discoveryPort = discoveryPort;
        setDaemon(true);
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(discoveryPort));

            byte[] buffer = new byte[1024];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                String[] parts = data.split(";");
                if (parts.length < 3) continue;

                String uname = parts[0];
                String ip = parts[1];
                int port;
                try { port = Integer.parseInt(parts[2]); } catch (NumberFormatException ex) { continue; }

                String fp = null;
                if (parts.length >= 4) {
                    fp = parts[3].trim();
                    if (fp.isEmpty()) fp = null;
                }

                long now = System.currentTimeMillis();

                synchronized (peers) {
                    boolean found = false;
                    for (Peer p : peers) {
                        if (p.ip.equals(ip) && p.port == port) {
                            p.setLastSeen(now);
                            // if discovery carries fingerprint and peer doesn't have it yet -> set it
                            if (fp != null && (p.getFingerprint() == null || p.getFingerprint().isEmpty())) {
                                p.setFingerprint(fp);
                            }
                            // optionally update username
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        Peer p = new Peer(uname, ip, port);
                        p.setLastSeen(now);
                        if (fp != null) p.setFingerprint(fp);
                        peers.add(p);
                    }
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            if (socket != null) socket.close();
        }
    }

    /**
     * Return a snapshot list of peers, after removing timed-out peers.
     */
    public List<Peer> snapshot() {
        long now = System.currentTimeMillis();
        synchronized (peers) {
            Iterator<Peer> it = peers.iterator();
            while (it.hasNext()) {
                Peer p = it.next();
                if (now - p.getLastSeen() > PEER_TIMEOUT_MS) it.remove();
            }
            return new ArrayList<>(peers);
        }
    }

    /**
     * Manually remove a peer by ip+port (used by UI "Remove" action).
     * Returns true if removed.
     */
    public boolean removePeer(String ip, int port) {
        synchronized (peers) {
            Iterator<Peer> it = peers.iterator();
            while (it.hasNext()) {
                Peer p = it.next();
                if (p.ip.equals(ip) && p.port == port) {
                    it.remove();
                    return true;
                }
            }
            return false;
        }
    }
}
