package p2p;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Peer discovery listener that:
 * - binds with setReuseAddress(true) so multiple listeners on same machine can share discovery port
 * - updates peer.lastSeen on each discovery packet
 * - snapshot() removes peers older than PEER_TIMEOUT_MS
 */
public class PeerDiscoveryListener extends Thread {
    private final List<Peer> peers = new ArrayList<>();
    private volatile boolean running = true;
    private final int discoveryPort;

    // timeout in milliseconds -> if we haven't heard from a peer for this long, we drop it
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

                long now = System.currentTimeMillis();

                // find existing peer by ip+port
                synchronized (peers) {
                    boolean found = false;
                    for (Peer p : peers) {
                        if (p.ip.equals(ip) && p.port == port) {
                            p.setLastSeen(now);
                            // update username if changed
                            // (optional) keep username updated
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        Peer p = new Peer(uname, ip, port);
                        p.setLastSeen(now);
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
            // remove stale
            Iterator<Peer> it = peers.iterator();
            while (it.hasNext()) {
                Peer p = it.next();
                if (now - p.getLastSeen() > PEER_TIMEOUT_MS) {
                    it.remove();
                }
            }
            // return copy
            return new ArrayList<>(peers);
        }
    }
}
