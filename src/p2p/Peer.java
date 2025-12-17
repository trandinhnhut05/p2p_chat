package p2p;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Đại diện 1 peer trong P2P, lưu AES session key và RSA key pair.
 */
public class Peer {

    public String username;
    public String ip;
    public int port;

    private long lastPingMs = -1; // mặc định chưa ping
    private String fingerprint; // fingerprint định danh
    private boolean muted;
    private boolean blocked;
    private long lastSeen;
    private String lastMessage;

    // RSA key pair của peer
    private KeyPair keyPair;

    // AES session key cho peer này (E2EE)
    private final ConcurrentMap<String, SecretKey> sessionKeys = new ConcurrentHashMap<>();

    public Peer(String username, String ip, int port) {
        this.username = username;
        this.ip = ip;
        this.port = port;
    }

    // --- Getter/Setter ---


    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;

    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public long getLastPingMs() { return lastPingMs; }
    public void setLastPingMs(long rtt) { this.lastPingMs = rtt; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    // định danh peer (fingerprint ưu tiên)
    public String getId() {
        return (fingerprint != null && !fingerprint.isBlank()) ? fingerprint : ip + "_" + port;
    }

    // --- RSA key pair ---
    public void setKeyPair(KeyPair kp) { this.keyPair = kp; }
    public PrivateKey getPrivateKey() {
        return (keyPair != null) ? keyPair.getPrivate() : null;
    }
    public PublicKey getPublicKey() {
        return (keyPair != null) ? keyPair.getPublic() : null;
    }

    // --- AES session key management ---
    public void setSessionKey(String peerId, SecretKey key) { sessionKeys.put(peerId, key); }
    public SecretKey getSessionKey(String peerId) { return sessionKeys.get(peerId); }

    // equality cho peerList
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Peer)) return false;
        Peer other = (Peer) o;
        if (fingerprint != null && !fingerprint.isBlank() && other.fingerprint != null && !other.fingerprint.isBlank())
            return fingerprint.equals(other.fingerprint);
        return ip.equals(other.ip) && port == other.port;
    }

    @Override
    public int hashCode() {
        if (fingerprint != null && !fingerprint.isBlank()) return fingerprint.hashCode();
        return (ip + "_" + port).hashCode();
    }
}
