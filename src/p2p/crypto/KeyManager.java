package p2p.crypto;

import p2p.Peer;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataOutputStream;
import java.net.Socket;
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
    private final Map<String, SecretKey> sessionKeys = new ConcurrentHashMap<>();

    public SecretKey getSessionKey(String keyId) {
        return sessionKeys.get(keyId);
    }

    public SecretKey createSessionKey(String keyId) throws Exception {
        if (sessionKeys.containsKey(keyId))
            return sessionKeys.get(keyId);

        KeyGenerator kg = KeyGenerator.getInstance(AES);
        kg.init(128, new SecureRandom());
        SecretKey key = kg.generateKey();
        sessionKeys.put(keyId, key);
        return key;
    }

    public void storeSessionKey(String keyId, byte[] rawKey) {
        SecretKey key = new SecretKeySpec(rawKey, AES);
        sessionKeys.put(keyId, key);
    }

    public boolean hasKey(String keyId) {
        return sessionKeys.containsKey(keyId);
    }

    public SecretKey getOrCreate(String keyId) throws Exception {
        SecretKey key = sessionKeys.get(keyId);
        if (key == null)
            key = createSessionKey(keyId);
        return key;
    }

    public Cipher createAESCipher(int mode, String keyId) throws Exception {
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(mode, getOrCreate(keyId));
        return cipher;
    }

    public byte[] encrypt(String keyId, byte[] plain) throws Exception {
        return createAESCipher(Cipher.ENCRYPT_MODE, keyId).doFinal(plain);
    }

    public byte[] decrypt(String keyId, byte[] encrypted) throws Exception {
        return createAESCipher(Cipher.DECRYPT_MODE, keyId).doFinal(encrypted);
    }
}

