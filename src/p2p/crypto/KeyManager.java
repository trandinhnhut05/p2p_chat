package p2p.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KeyManager
 * ----------
 * Quản lý AES session key cho từng peer (theo peerId / fingerprint)
 */
public class KeyManager {

    private static final String AES = "AES";

    // peerId -> AES SecretKey
    private final Map<String, SecretKey> sessionKeys = new ConcurrentHashMap<>();

    public KeyManager() {}

    /* ================= SESSION KEY ================= */

    public synchronized SecretKey getSessionKey(String peerId) {
        return sessionKeys.get(peerId);
    }

    public synchronized SecretKey createSessionKey(String peerId) throws Exception {
        if (sessionKeys.containsKey(peerId))
            return sessionKeys.get(peerId);

        KeyGenerator kg = KeyGenerator.getInstance(AES);
        kg.init(128, new SecureRandom());
        SecretKey key = kg.generateKey();
        sessionKeys.put(peerId, key);
        return key;
    }

    public synchronized void storeSessionKey(String peerId, byte[] rawKey) {
        SecretKey key = new SecretKeySpec(rawKey, AES);
        sessionKeys.put(peerId, key);
    }

    /**
     * Dùng khi cần chắc chắn peer có key (chat, video)
     */
    public synchronized SecretKey getOrCreate(String peerId) throws Exception {
        SecretKey key = sessionKeys.get(peerId);
        if (key == null)
            key = createSessionKey(peerId);
        return key;
    }

    /* ================= CIPHER ================= */

    /**
     * Dùng cho PeerHandler / FileReceiver
     */
    public Cipher createAESCipher(int mode, String peerId) throws Exception {
        SecretKey key = getOrCreate(peerId);
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(mode, key);
        return cipher;
    }

    /* ================= BYTE ENCRYPT ================= */

    public byte[] encrypt(String peerId, byte[] plain) throws Exception {
        Cipher cipher = createAESCipher(Cipher.ENCRYPT_MODE, peerId);
        return cipher.doFinal(plain);
    }

    public byte[] decrypt(String peerId, byte[] encrypted) throws Exception {
        Cipher cipher = createAESCipher(Cipher.DECRYPT_MODE, peerId);
        return cipher.doFinal(encrypted);
    }

    /* ================= VIDEO / FUTURE ================= */

    /**
     * Placeholder – nếu sau này gửi key qua socket
     */
    public void createAndSendSessionKey(String peerId) throws Exception {
        createSessionKey(peerId);
        // gửi raw key qua socket (nếu cần)
    }
}
