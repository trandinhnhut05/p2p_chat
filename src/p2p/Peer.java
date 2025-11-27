package p2p;

import java.util.Objects;

/**
 * Peer information. lastSeen stores the last discovery timestamp (epoch ms).
 * lastSeen is NOT part of equals/hashCode (so identity still by ip+port).
 */
public class Peer {
    public final String username;
    public final String ip;
    public final int port;

    // lastSeen in milliseconds since epoch (updated by PeerDiscoveryListener)
    private volatile long lastSeen = 0L;

    public Peer(String username, String ip, int port) {
        this.username = username;
        this.ip = ip;
        this.port = port;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    public String toString() {
        return username + " (" + ip + ":" + port + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;
        Peer peer = (Peer) o;
        return port == peer.port && Objects.equals(ip, peer.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
}
