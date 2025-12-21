package p2p.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KeyManager
 * ----------
 * Quản lý AES session key cho từng peer / call
 */
public class KeyManager {

    private static final String AES = "AES";
    private static final String AES_MODE = "AES/CBC/PKCS5Padding";

    private final Map<String, SecretKey> sessionKeys = new ConcurrentHashMap<>();

    /* ================= KEY ================= */

    public boolean hasKey(String keyId) {
        return sessionKeys.containsKey(keyId);
    }

    public SecretKey getSessionKey(String keyId) {
        return sessionKeys.get(keyId);
    }

    public SecretKey getOrCreate(String keyId) {
        return sessionKeys.computeIfAbsent(keyId, k -> {
            try {
                KeyGenerator kg = KeyGenerator.getInstance(AES);
                kg.init(128, new SecureRandom());
                return kg.generateKey();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }


    public void storeSessionKey(String keyId, byte[] rawKey) {
        sessionKeys.put(keyId, new SecretKeySpec(rawKey, AES));
    }

    /* ================= CIPHER ================= */

    public Cipher createEncryptCipher(String keyId, IvParameterSpec iv) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreate(keyId), iv);
        return cipher;
    }

    public Cipher createDecryptCipher(String keyId, IvParameterSpec iv) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreate(keyId), iv);
        return cipher;
    }
}
