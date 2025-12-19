package p2p;

import p2p.crypto.KeyManager;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import java.io.*;
import java.net.Socket;

/**
 * PeerClient
 * ----------
 * Chủ động kết nối tới peer khác để gửi dữ liệu
 */
public class PeerClient {

    private static KeyManager keyManager;

    public static void init(KeyManager km) {
        keyManager = km;
    }

    /* ================= MESSAGE ================= */

    public static void sendMessage(Peer peer, String message) {
        if (keyManager == null) return;

        try (Socket socket = new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("MSG");
            dos.flush();

            Cipher cipher = keyManager.createAESCipher(Cipher.ENCRYPT_MODE, peer.getId());

            try (CipherOutputStream cos = new CipherOutputStream(dos, cipher);
                 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(cos))) {

                bw.write(message);
                bw.newLine();
                bw.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ================= FILE ================= */

    public static void sendFile(Peer peer, File file) {
        if (keyManager == null) return;
        FileSender.sendFile(peer, file, keyManager);
    }
}
