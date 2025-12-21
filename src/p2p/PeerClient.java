package p2p;

import p2p.crypto.KeyManager;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.DataOutputStream;
import java.io.File;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class PeerClient {

    private final KeyManager keyManager;
    private final String localPeerId;
    private final int localServicePort;
    private final String localUsername;


    // 🔥 Inject KeyManager qua constructor
    public PeerClient(KeyManager keyManager, String localPeerId, int localServicePort, String localUsername) {
        this.keyManager = keyManager;
        this.localPeerId = localPeerId;
        this.localServicePort = localServicePort;
        this.localUsername = localUsername;
    }
    private void sendHello(DataOutputStream dos) throws Exception {
        dos.writeUTF("HELLO");
        dos.writeUTF(localUsername);   // ✅ ĐÚNG
        dos.writeInt(localServicePort);
        dos.flush();
    }


    /* ================= MESSAGE ================= */

    public void sendMessage(Peer peer, String message) {
        try {
            // 1️⃣ ĐẢM BẢO ĐÃ CÓ SESSION KEY
            if (!keyManager.hasKey(peer.getId())) {
                SecretKey key = keyManager.getOrCreate(peer.getId());

                try (Socket s = new Socket(peer.getAddress(), peer.getServicePort());
                     DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {

                    sendHello(dos);
                    dos.writeUTF("SESSION_KEY");
                    dos.writeUTF(peer.getId());
                    dos.write(key.getEncoded());
                    dos.flush();
                }
            }

            // 2️⃣ GỬI MESSAGE
            try (Socket socket = new Socket(peer.getAddress(), peer.getServicePort());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                sendHello(dos);
                dos.writeUTF("MSG");

                // 🔐 ENCRYPT
                byte[] ivBytes = new byte[16];
                new SecureRandom().nextBytes(ivBytes);
                IvParameterSpec iv = new IvParameterSpec(ivBytes);

                Cipher cipher = keyManager.createEncryptCipher(peer.getId(), iv);
                byte[] encrypted = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));

                // 📦 SEND
                dos.writeInt(ivBytes.length);
                dos.write(ivBytes);

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

        String callKey =
                "CALL-" + localPeerId + "-" + java.util.UUID.randomUUID();
        peer.setCallKey(callKey);



        try {
            if (!keyManager.hasKey(callKey)) {
                SecretKey key = keyManager.getOrCreate(callKey); // ✅ ĐÚNG

                try (Socket s =
                             new Socket(peer.getAddress(), peer.getServicePort());
                     DataOutputStream dos =
                             new DataOutputStream(s.getOutputStream())) {

                    sendHello(dos);                 // ✅ NÊN CÓ
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

                sendHello(dos); // ✅ BẮT BUỘC

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

        // 🚨 CHẶN PORT -1 / PORT RÁC
        if (peer.getServicePort() <= 0) {
            System.err.println(
                    "❌ Cannot send CALL_ACCEPT, servicePort not known yet for peer: "
                            + peer.getId()
            );
            return;
        }

        try (Socket socket =
                     new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos =
                     new DataOutputStream(socket.getOutputStream())) {

            sendHello(dos); // ✅ QUAN TRỌNG NHẤT

            dos.writeUTF("CALL_ACCEPT");
            dos.writeUTF(peer.getCallKey());
            dos.writeInt(videoPort);
            dos.writeInt(audioPort);
            dos.flush();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void sendCallEnd(Peer peer) {
        try (Socket socket =
                     new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos =
                     new DataOutputStream(socket.getOutputStream())) {

            sendHello(dos);
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
