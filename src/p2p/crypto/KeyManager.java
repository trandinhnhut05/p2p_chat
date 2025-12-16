package p2p.crypto;

import p2p.PeerClient;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý AES session key và RSA public key của peer.
 */
public class KeyManager {

    private final Map<String, SecretKey> sessionKeys = new ConcurrentHashMap<>();
    private final Map<String, PublicKey> peerPublicKeys = new ConcurrentHashMap<>();
    private PrivateKey myPrivateKey; // private key của chính mình

    public KeyManager() {
    }

    // --- AES session key ---
    public SecretKey getSessionKey(String peerId) {
        return sessionKeys.get(peerId);
    }

    public void storeSessionKey(String peerId, SecretKey key) {
        sessionKeys.put(peerId, key);
    }

    /**
     * Tạo AES key mới
     */
    public SecretKey createSessionKey() throws Exception {
        return KeyGenerator.getInstance("AES").generateKey();
    }

    /**
     * Tạo AES key mới và gửi đến peer qua RSA
     */
    public SecretKey createAndSendSessionKey(String peerId) throws Exception {
        SecretKey key = createSessionKey();
        storeSessionKey(peerId, key);

        PublicKey pub = peerPublicKeys.get(peerId);
        if (pub != null) {
            byte[] encryptedKey = encryptSessionKeyForPeer(key, pub);
            PeerClient.sendRawKeyToPeer(peerId, encryptedKey);
        }

        return key;
    }

    /**
     * Mã hóa AES key bằng RSA của peer
     */
    public byte[] encryptSessionKeyForPeer(SecretKey key, PublicKey peerPub) throws Exception {
        return CryptoUtils.encryptRSA(key.getEncoded(), peerPub);
    }

    /**
     * Giải mã AES key nhận từ peer bằng private key của mình
     */
    public SecretKey decryptRSAKey(byte[] encKey) throws Exception {
        if (myPrivateKey == null) throw new IllegalStateException("Private key not set");
        byte[] keyBytes = CryptoUtils.decryptRSA(encKey, myPrivateKey);
        return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
    }

    // --- RSA keys ---
    public void setPrivateKey(PrivateKey pk) {
        this.myPrivateKey = pk;
    }

    public void storePeerPublicKey(String peerId, PublicKey pub) {
        peerPublicKeys.put(peerId, pub);
    }
}
