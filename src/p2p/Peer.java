package p2p;

import java.util.Objects;

/**
 * Peer information.
 * - username: display name
 * - ip, port: last seen address for connecting
 * - fingerprint: optional identity (e.g. public-key fingerprint). If present, used as stable id.
 *
 * getId() returns fingerprint if available, otherwise ip_port fallback.
 */
public class Peer {
    public final String username;
    public final String ip;
    public final int port;

    private volatile long lastSeen = 0L;
    private volatile String lastMessage = "";
    private volatile boolean muted = false;
    private volatile boolean blocked = false;
    private volatile long lastPingMs = -1L;

    // NEW: optional stable identity of peer (e.g. key fingerprint). Can be null/empty.
    private volatile String fingerprint = null;

    public Peer(String username, String ip, int port) {
        this.username = username;
        this.ip = ip;
        this.port = port;
    }

    // convenience constructor including fingerprint
    public Peer(String username, String ip, int port, String fingerprint) {
        this.username = username;
        this.ip = ip;
        this.port = port;
        this.fingerprint = fingerprint;
    }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) {
        if (lastMessage == null) lastMessage = "";
        this.lastMessage = lastMessage;
    }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public long getLastPingMs() { return lastPingMs; }
    public void setLastPingMs(long lastPingMs) { this.lastPingMs = lastPingMs; }

    // fingerprint accessors
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    /**
     * Stable id used for filenames / settings keys.
     * If fingerprint exists and non-empty, return it.
     * Otherwise return ip_port (safe fallback).
     */
    public String getId() {
        if (fingerprint != null && !fingerprint.trim().isEmpty()) return fingerprint.trim();
        // fallback: ip_port
        return ip.replace(':', '_') + "_" + port;
    }

    @Override
    public String toString() {
        if (fingerprint != null && !fingerprint.isEmpty()) return username + " [" + fingerprint + "] (" + ip + ":" + port + ")";
        return username + " (" + ip + ":" + port + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;
        Peer peer = (Peer) o;
        // Identity: prefer fingerprint when present (two peers with same fingerprint are same identity)
        if (this.fingerprint != null && !this.fingerprint.isEmpty() && peer.fingerprint != null && !peer.fingerprint.isEmpty()) {
            return this.fingerprint.equals(peer.fingerprint);
        }
        // otherwise fallback to ip+port identity
        return port == peer.port && Objects.equals(ip, peer.ip);
    }

    @Override
    public int hashCode() {
        if (fingerprint != null && !fingerprint.isEmpty()) return Objects.hash(fingerprint);
        return Objects.hash(ip, port);
    }
}
