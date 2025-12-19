package p2p;

import java.net.InetAddress;
import java.util.Objects;

/**
 * Peer
 * ----
 * Đại diện cho 1 peer trong mạng P2P
 */
public class Peer {

    private final InetAddress address;
    private final int servicePort;
    private final String username;
    private final String fingerprint;

    private long lastSeen;
    private String lastMessage = "";

    public Peer(InetAddress address,
                int servicePort,
                String username,
                String fingerprint) {
        this.address = address;
        this.servicePort = servicePort;
        this.username = username;
        this.fingerprint = fingerprint;
        this.lastSeen = System.currentTimeMillis();
    }

    /* ================= GETTERS ================= */

    public InetAddress getAddress() {
        return address;
    }

    public String getIp() {
        return address.getHostAddress();
    }

    public int getServicePort() {
        return servicePort;
    }

    public String getUsername() {
        return username;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }


    /**
     * peerId = fingerprint
     */
    public String getId() {
        return fingerprint;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    /* ================= SETTERS ================= */

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    /* ================= OVERRIDE ================= */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;
        Peer peer = (Peer) o;
        return Objects.equals(fingerprint, peer.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprint);
    }

    @Override
    public String toString() {
        return username + " (" +
                address.getHostAddress() + ":" +
                servicePort + ")";
    }
}
