package p2p;

import javafx.application.Platform;
import p2p.crypto.KeyManager;

import java.io.DataInputStream;
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

    public PeerHandler(Socket socket,
                       Peer peer,
                       KeyManager keyManager,
                       SettingsStore settings,
                       MainUI mainUI) {
        this.socket = socket;
        this.peer = peer;
        this.keyManager = keyManager;
        this.settings = settings;
        this.mainUI = mainUI;
    }



    @Override
    public void run() {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            String type = dis.readUTF();

            switch (type) {

                /* ========== SESSION KEY ========== */
                case "SESSION_KEY" -> {
                    String peerId = dis.readUTF();
                    byte[] keyBytes = new byte[16];
                    dis.readFully(keyBytes);

                    keyManager.storeSessionKey(peerId, keyBytes);

                    System.out.println("🔐 Session key stored from " + peerId);
                }
                case "HELLO" -> {
                    peer.setUsername(dis.readUTF());
                    peer.setServicePort(dis.readInt());

                    System.out.println("👋 HELLO from " + peer.getUsername()
                            + " servicePort=" + peer.getServicePort());
                }


                /* ========== CALL REQUEST ========== */
                case "CALL_REQUEST" -> {
                    String callKey = dis.readUTF();
                    int videoPort = dis.readInt();
                    int audioPort = dis.readInt();

                    peer.setCallKey(callKey);
                    peer.setVideoPort(videoPort);
                    peer.setAudioPort(audioPort);

                    Platform.runLater(() -> mainUI.onIncomingCall(peer));
                }


                /* ========== CALL ACCEPT ========== */
                case "CALL_ACCEPT" -> {
                    String callKey = dis.readUTF();
                    int videoPort = dis.readInt();
                    int audioPort = dis.readInt();

                    peer.setCallKey(callKey);
                    peer.setVideoPort(videoPort);
                    peer.setAudioPort(audioPort);

                    Platform.runLater(() -> mainUI.startCallFromRemote(peer));
                }


                /* ========== CALL END ========== */
                case "CALL_END" -> {
                    Platform.runLater(() ->
                            mainUI.stopCallFromRemote(peer)
                    );
                }

                /* ========== MESSAGE ========== */
                case "MSG" -> handleMessage(dis);

                /* ========== FILE ========== */
                case "FILE" -> handleFile();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    /* ================= MESSAGE ================= */

    private void handleMessage(DataInputStream dis) throws Exception {

        int length = dis.readInt();
        byte[] encrypted = new byte[length];
        dis.readFully(encrypted);

        byte[] decrypted = keyManager.decrypt(peer.getId(), encrypted);
        String msg = new String(decrypted, StandardCharsets.UTF_8);

        if (settings.isBlockedById(peer.getId())) return;

        peer.setLastMessage(msg);
        ChatWindow.appendToHistoryFileStatic(peer, peer.getUsername(), msg);

        Platform.runLater(() -> mainUI.onIncomingMessage(peer, msg));
    }



    /* ================= FILE ================= */

    private void handleFile() {
        new Thread(new FileReceiver(socket, peer, keyManager)).start();
    }
}
