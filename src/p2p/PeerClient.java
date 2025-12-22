package p2p;

import p2p.crypto.KeyManager;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.DataInputStream;
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
    private String currentCallId;
    public String getCurrentCallId() {
        return currentCallId;
    }

    public void setCurrentCallId(String callId) {
        this.currentCallId = callId;
    }


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
                                int localVideoPortSend,
                                int localAudioPortSend,
                                String callKey) {

        try {
            // 1️⃣ Tạo session key và đảm bảo remote peer có key
            SecretKey key = keyManager.getOrCreate(callKey);
            ensureSessionKeyOnRemote(peer, callKey);

            peer.setCallKey(callKey);

            // 2️⃣ Gửi CALL_REQUEST
            try (Socket socket = new Socket(peer.getAddress(), peer.getServicePort());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                sendHello(dos);
                dos.writeUTF("CALL_REQUEST");
                dos.writeUTF(callKey);
                dos.writeInt(localVideoPortSend);
                dos.writeInt(localAudioPortSend);
                dos.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ================= CALL ================= */

    // Gửi CALL_ACCEPT kèm callKey, videoPort, audioPort
    public void sendCallAccept(Peer peer,
                               int localVideoPort,
                               int localAudioPort,
                               String callKey) {

        if (peer.getServicePort() <= 0 || callKey == null) {
            System.err.println("❌ Cannot send CALL_ACCEPT, missing servicePort or callKey");
            return;
        }

        try (Socket socket = new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            sendHello(dos);
            dos.writeUTF("CALL_ACCEPT");
            dos.writeUTF(callKey);            // 🔹 truyền callKey
            dos.writeInt(localVideoPort);
            dos.writeInt(localAudioPort);
            dos.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }






    private void ensureSessionKeyOnRemote(Peer peer, String keyId) throws Exception {
        if (keyManager.hasKey(keyId)) {
            SecretKey key = keyManager.getOrCreate(keyId);

            try (Socket s = new Socket(peer.getAddress(), peer.getServicePort());
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                 DataInputStream dis = new DataInputStream(s.getInputStream())) {

                sendHello(dos);
                dos.writeUTF("SESSION_KEY");
                dos.writeUTF(keyId);
                dos.write(key.getEncoded());
                dos.flush();

                // 🔹 chờ ACK
                String ack = dis.readUTF();
                if (!"SESSION_KEY_ACK".equals(ack)) {
                    throw new Exception("❌ Peer did not ack session key");
                }
            }
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
        FileSender.sendFile(
                peer,
                file,
                keyManager,
                localUsername,     // 🔥 QUAN TRỌNG
                localServicePort   // 🔥 QUAN TRỌNG
        );
    }

}
