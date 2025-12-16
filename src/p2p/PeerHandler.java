package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.io.InputStream;
import java.net.Socket;
import java.util.Base64;

public class PeerHandler extends Thread {

    public interface MessageCallback {
        void onMessage(String fromIp, String fingerprint, String message);
        void onFileReceived(String fromIp, String filename, File file);
    }

    private final Socket socket;
    private final MessageCallback callback;
    private final KeyManager keyManager;

    public PeerHandler(Socket socket, KeyManager km, MessageCallback cb) {
        this.socket = socket;
        this.keyManager = km;
        this.callback = cb;
    }

    @Override
    public void run() {
        try (InputStream is = socket.getInputStream()) {
            byte[] ivBytes = is.readNBytes(16);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            int lenHi = is.read();
            int lenLo = is.read();
            int dataLen = (lenHi << 8) | lenLo;
            byte[] encrypted = is.readNBytes(dataLen);

            String peerId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();

            // Kiểm tra nếu là AES key
            String possibleKeyMsg = new String(encrypted, "UTF-8");
            if (possibleKeyMsg.startsWith("/AESKEY:")) {
                byte[] encKey = Base64.getDecoder().decode(possibleKeyMsg.substring(8));
                SecretKey aes = keyManager.decryptRSAKey(encKey);
                keyManager.storeSessionKey(peerId, aes);
                return;
            }

            SecretKey aes = keyManager.getSessionKey(peerId);
            if (aes == null) return; // chưa có key, bỏ qua

            byte[] decrypted = CryptoUtils.decryptAES(encrypted, aes, iv);
            String message = new String(decrypted, "UTF-8");
            callback.onMessage(socket.getInetAddress().getHostAddress(), null, message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
