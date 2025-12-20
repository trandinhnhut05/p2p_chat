package p2p;

import p2p.crypto.KeyManager;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
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

        try {
            // 1️⃣ Đảm bảo có session key cho peer
            if (!keyManager.hasKey(peer.getId())) {
                SecretKey key = keyManager.createSessionKey(peer.getId());

                try (Socket s =
                             new Socket(peer.getAddress(), peer.getServicePort());
                     DataOutputStream dos =
                             new DataOutputStream(s.getOutputStream())) {

                    dos.writeUTF("SESSION_KEY");
                    dos.writeUTF(peer.getId());
                    dos.write(key.getEncoded());
                    dos.flush();
                }
            }

            // 2️⃣ Gửi message đã mã hóa
            try (Socket socket =
                         new Socket(peer.getAddress(), peer.getServicePort());
                 DataOutputStream dos =
                         new DataOutputStream(socket.getOutputStream())) {

                dos.writeUTF("MSG");

                byte[] encrypted =
                        keyManager.encrypt(peer.getId(), message.getBytes());

                dos.writeInt(encrypted.length);
                dos.write(encrypted);
                dos.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /* ================= CALL ================= */

    public static void sendCallRequest(Peer peer,
                                       int videoPort,
                                       int audioPort) {

        String callKey = "CALL-" + peer.getId();

        try {
            if (!keyManager.hasKey(callKey)) {
                SecretKey key = keyManager.createSessionKey(callKey);

                try (Socket s =
                             new Socket(peer.getAddress(), peer.getServicePort());
                     DataOutputStream dos =
                             new DataOutputStream(s.getOutputStream())) {

                    dos.writeUTF("SESSION_KEY");
                    dos.writeUTF(callKey);
                    dos.write(key.getEncoded());
                    dos.flush();
                }
            }

            try (Socket socket =
                         new Socket(peer.getAddress(), peer.getServicePort());
                 DataOutputStream dos =
                         new DataOutputStream(socket.getOutputStream())) {

                dos.writeUTF("CALL_REQUEST");
                dos.writeUTF(callKey);
                dos.writeInt(videoPort);
                dos.writeInt(audioPort);
                dos.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    public static void sendCallAccept(Peer peer,
                                      int videoPort,
                                      int audioPort) {
        try (Socket socket =
                     new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos =
                     new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("CALL_ACCEPT");
            dos.writeInt(videoPort);
            dos.writeInt(audioPort);
            dos.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendCallEnd(Peer peer) {
        try (Socket socket =
                     new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos =
                     new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("CALL_END");
            dos.flush();

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
