package p2p;

import javafx.application.Platform;
import p2p.crypto.KeyManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * PeerHandler
 * -----------
 * Xử lý kết nối đến từ peer khác
 * - MSG  : nhận tin nhắn
 * - FILE : nhận file
 */
public class PeerHandler implements Runnable {

    private final Socket socket;
    private final Peer peer;
    private final KeyManager keyManager;
    private final SettingsStore settings;
    private final MainUI mainUI;
    private final CallManager callManager;


    public PeerHandler(Socket socket,
                       Peer peer,
                       KeyManager keyManager,
                       SettingsStore settings,
                       MainUI mainUI, CallManager callManager) {
        this.socket = socket;
        this.peer = peer;
        this.keyManager = keyManager;
        this.settings = settings;
        this.mainUI = mainUI;
        this.callManager = callManager;
    }



    @Override
    public void run() {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            /* ===== HELLO (BẮT BUỘC) ===== */
            String hello = dis.readUTF();
            if (!"HELLO".equals(hello)) {
                socket.close();
                return;
            }

            peer.setUsername(dis.readUTF());
            peer.setServicePort(dis.readInt());

            // 🔥 BẮT BUỘC: rebuild lại ID peer
            peer.rebuildId();


            System.out.println("👋 HELLO from " + peer.getUsername()
                    + " servicePort=" + peer.getServicePort());

            /* ===== REAL TYPE ===== */
            while (true) {
                String type = dis.readUTF();

                switch (type) {
                    case "SESSION_KEY" -> handleSessionKey(dis);
                    case "MSG" -> handleMessage(dis);
                    case "CALL_REQUEST" -> handleCallRequest(dis);
                    case "CALL_ACCEPT" -> handleCallAccept(dis);
                    case "CALL_END" ->
                            Platform.runLater(() -> mainUI.stopCallFromRemote(peer));
                    case "FILE" -> handleFile();
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void handleSessionKey(DataInputStream dis) throws Exception {
        String keyId = dis.readUTF();

        byte[] keyBytes = new byte[16];
        dis.readFully(keyBytes);

        keyManager.storeSessionKey(keyId, keyBytes);
        System.out.println("🔐 Session key stored: " + keyId);

        // gửi ACK
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF("SESSION_KEY_ACK");
        dos.flush();
    }

    private void handleCallRequest(DataInputStream dis) throws Exception {
        String callKey = dis.readUTF();

        int retries = 5;
        while (!keyManager.hasKey(callKey) && retries-- > 0) {
            Thread.sleep(100);
        }

        if (!keyManager.hasKey(callKey)) {
            System.err.println("❌ Missing call key: " + callKey);
            return;
        }

        peer.setCallKey(callKey);

        int videoPort = dis.readInt();
        int audioPort = dis.readInt();

        peer.setVideoPort(videoPort);
        peer.setAudioPort(audioPort);

        Platform.runLater(() ->
                mainUI.onIncomingCall(peer, callKey, videoPort, audioPort)
        );
    }



    private void handleCallAccept(DataInputStream dis) throws Exception {
        String callKey = dis.readUTF();

        if (!keyManager.hasKey(callKey)) {
            System.err.println("❌ Missing call key: " + callKey);
            return;
        }

        peer.setCallKey(callKey);
        peer.setVideoPort(dis.readInt());
        peer.setAudioPort(dis.readInt());

        Platform.runLater(() ->
                mainUI.startCallFromRemote(peer, peer.getVideoPort(), peer.getAudioPort())
        );

    }






    /* ================= MESSAGE ================= */

    private void handleMessage(DataInputStream dis) throws Exception {

        int ivLen = dis.readInt();
        byte[] iv = new byte[ivLen];
        dis.readFully(iv);

        int len = dis.readInt();
        byte[] encrypted = new byte[len];
        dis.readFully(encrypted);

        byte[] decrypted = keyManager.createDecryptCipher(
                peer.getId(),
                new javax.crypto.spec.IvParameterSpec(iv)
        ).doFinal(encrypted);

        String msg = new String(decrypted, StandardCharsets.UTF_8);

        if (settings.isBlockedById(peer.getId())) return;

        peer.setLastMessage(msg);
        ChatWindow.appendToHistoryFileStatic(peer, peer.getUsername(), msg);

        Platform.runLater(() ->
                mainUI.onIncomingMessage(peer, msg)
        );
    }




    /* ================= FILE ================= */

    private void handleFile() {
        new Thread(new FileReceiver(socket, peer, keyManager)).start();
    }
}
