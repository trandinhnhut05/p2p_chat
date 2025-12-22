package p2p;

import javafx.application.Platform;
import p2p.crypto.KeyManager;

import javax.crypto.spec.IvParameterSpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.Socket;

/**
 * PeerHandler
 * ----------
 * 1 connection = 1 command
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
                       MainUI mainUI,
                       CallManager callManager) {
        this.socket = socket;
        this.peer = peer;
        this.keyManager = keyManager;
        this.settings = settings;
        this.mainUI = mainUI;
        this.callManager = callManager;
    }

    @Override
    public void run() {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            String hello = dis.readUTF();
            if (!"HELLO".equals(hello)) return;

            peer.setUsername(dis.readUTF());
            peer.setServicePort(dis.readInt());
            peer.rebuildId();

            String type = dis.readUTF();

            switch (type) {
                case "SESSION_KEY" -> handleSessionKey(dis, dos);
                case "MSG" -> handleMessage(dis);
                case "CALL_REQUEST" -> handleCallRequest(dis);
                case "CALL_ACCEPT" -> handleCallAccept(dis);
                case "CALL_END" ->
                        Platform.runLater(() -> mainUI.stopCallFromRemote(peer));
                case "FILE" -> handleFile(dis);
            }

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* ================= SESSION KEY ================= */
    private void handleSessionKey(DataInputStream dis, DataOutputStream dos) throws Exception {
        String keyId = dis.readUTF(); // bỏ dùng

        byte[] keyBytes = new byte[16];
        dis.readFully(keyBytes);

        peer.rebuildId();
        keyManager.storeSessionKey(peer.getId(), keyBytes);

        System.out.println("🔐 Session key stored for peer: " + peer.getId());

        dos.writeUTF("SESSION_KEY_ACK");
        dos.flush();
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
                new IvParameterSpec(iv)
        ).doFinal(encrypted);

        String msg = new String(decrypted);

        if (settings.isBlockedById(peer.getId())) return;

        peer.setLastMessage(msg);
        ChatWindow.appendToHistoryFileStatic(peer, peer.getUsername(), msg);

        Platform.runLater(() ->
                mainUI.onIncomingMessage(peer, msg)
        );
    }

    /* ================= CALL ================= */
    private void handleCallRequest(DataInputStream dis) throws Exception {
        String callKey = dis.readUTF();
        peer.setCallKey(callKey);

        int callerVideoPort = dis.readInt();
        int callerAudioPort = dis.readInt();

        // Tạo local ports để gửi dữ liệu
        int localVideoPort = PeerServer.findAvailablePort(7000);
        int localAudioPort = PeerServer.findAvailablePort(8000);
        peer.setVideoPort(localVideoPort);
        peer.setAudioPort(localAudioPort);

        Platform.runLater(() -> {
            mainUI.onIncomingCall(peer, callKey, callerVideoPort, callerAudioPort);

            // Gửi CALL_ACCEPT kèm callKey, localVideoPort, localAudioPort
            new Thread(() ->
                    callManager.getPeerClient().sendCallAccept(peer, localVideoPort, localAudioPort, callKey)
            ).start();
        });

    }

//    private void handleCallAccept(DataInputStream dis) throws Exception {
//        String callKey = dis.readUTF();
//        peer.setCallKey(callKey);
//
//        int remoteVideoPort = dis.readInt();
//        int remoteAudioPort = dis.readInt();
//
//        Platform.runLater(() -> mainUI.onCallAccepted(peer, remoteVideoPort, remoteAudioPort));
//    }


    private void handleCallAccept(DataInputStream dis) throws Exception {
        String callKey = dis.readUTF();

        if (!keyManager.hasKey(callKey)) {
            keyManager.getOrCreate(callKey);
        }

        peer.setCallKey(callKey);

        int remoteVideoPort = dis.readInt();
        int remoteAudioPort = dis.readInt();

        // Delegate lên MainUI
        Platform.runLater(() -> mainUI.onCallAccepted(peer, remoteVideoPort, remoteAudioPort, callKey));

    }


    /* ================= FILE ================= */
    private void handleFile(DataInputStream dis) {
        try {
            String keyId = dis.readUTF();
            String fileName = dis.readUTF();

            if (settings.isBlockedById(keyId)) return;

            int ivLen = dis.readInt();
            byte[] iv = new byte[ivLen];
            dis.readFully(iv);

            int dataLen = dis.readInt();
            byte[] encrypted = new byte[dataLen];
            dis.readFully(encrypted);

            byte[] plain = keyManager.createDecryptCipher(
                    keyId,
                    new IvParameterSpec(iv)
            ).doFinal(encrypted);

            File dir = new File(System.getProperty("user.home"), "Downloads/p2p-chat");
            if (!dir.exists()) dir.mkdirs();

            File outFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(plain);
            }

            Platform.runLater(() ->
                    mainUI.onIncomingMessage(peer, "[FILE] Đã nhận: " + outFile.getName())
            );

            System.out.println("📥 File received: " + outFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
