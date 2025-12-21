package p2p;

import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;

public class PeerClient {

    private final KeyManager keyManager;
    private final String localPeerId;

    // 🔥 Inject KeyManager qua constructor
    public PeerClient(KeyManager keyManager, String localPeerId) {
        this.keyManager = keyManager;
        this.localPeerId = localPeerId;
    }

    /* ================= MESSAGE ================= */

    public void sendMessage(Peer peer, String message) {
        try {
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

    public void sendCallRequest(Peer peer,
                                int videoPort,
                                int audioPort) {

        String callKey = "CALL-" + localPeerId; // ✅ KHAI BÁO ĐẦU TIÊN
        peer.setCallKey(callKey);


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


    public void sendCallAccept(Peer peer,
                               int videoPort,
                               int audioPort) {
        try (Socket socket =
                     new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos =
                     new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("CALL_ACCEPT");
            dos.writeUTF(peer.getCallKey());
            dos.writeInt(videoPort);
            dos.writeInt(audioPort);

            dos.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendCallEnd(Peer peer) {
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

    public void sendFile(Peer peer, File file) {
        FileSender.sendFile(peer, file, keyManager);
    }
}
