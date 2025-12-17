package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PeerClient {

    private static final KeyManager keyManager = new KeyManager();

    public static void sendPing(Peer peer, long timestamp) {
        try {
            // payload là /PING:<timestamp>
            String msg = "/PING:" + timestamp;
            sendMessage(peer, msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Gửi tin nhắn E2EE
    public static void sendMessage(Peer peer, String msg) {
        try {
            String peerId = peer.getId();
            SecretKey aes = keyManager.getSessionKey(peerId);

            // tạo AES key nếu chưa có
            if (aes == null) {
                aes = keyManager.createAndSendSessionKey(peerId);
            }

            IvParameterSpec iv = CryptoUtils.generateIv();
            byte[] encrypted = CryptoUtils.encryptAES(msg.getBytes("UTF-8"), aes, iv);

            // gửi iv + encrypted dạng Base64
            String payload = Base64.getEncoder().encodeToString(iv.getIV()) + ":" +
                    Base64.getEncoder().encodeToString(encrypted);
            sendRawMessage(peer, payload);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Gửi dữ liệu thô
    public static void sendRawMessage(Peer peer, String payload) {
        try (Socket s = new Socket(peer.ip, peer.port);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println(payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Giải mã tin nhắn E2EE
    public static String decryptMessage(Peer peer, String payload) {
        try {
            String peerId = peer.getId();

            // Nếu payload là AES key
            if (payload.startsWith("/AESKEY:")) {
                byte[] encKey = Base64.getDecoder().decode(payload.substring(8));
                SecretKey aes = keyManager.decryptRSAKey(encKey);
                keyManager.storeSessionKey(peerId, aes);
                return null;
            }

            SecretKey aes = keyManager.getSessionKey(peerId);
            if (aes == null) return "[No AES key]";

            String[] parts = payload.split(":");
            if (parts.length != 2) return "[Invalid message format]";

            IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(parts[0]));
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            byte[] decrypted = CryptoUtils.decryptAES(encrypted, aes, iv);

            return new String(decrypted, "UTF-8");

        } catch (Exception e) {
            e.printStackTrace();
            return "[Decryption failed]";
        }
    }
    public static void sendRawKeyToPeer(String peerId, byte[] encryptedKey) {
        try {
            String[] parts = peerId.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try (Socket s = new Socket(ip, port);
                 OutputStream os = s.getOutputStream()) {

                String payload = "/AESKEY:" +
                        Base64.getEncoder().encodeToString(encryptedKey);

                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void sendEncryptedMessage(Peer peer, byte[] iv, byte[] encrypted) {
        try {
            String payload = Base64.getEncoder().encodeToString(iv) + ":" +
                    Base64.getEncoder().encodeToString(encrypted);
            sendRawMessage(peer, payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static KeyManager getKeyManager() { return keyManager; }
}
