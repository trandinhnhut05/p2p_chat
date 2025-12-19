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

            if ("MSG".equals(type)) {
                handleMessage(dis);
            } else if ("FILE".equals(type)) {
                handleFile();
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
