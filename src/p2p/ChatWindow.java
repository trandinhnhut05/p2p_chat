package p2p;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import p2p.crypto.KeyManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * ChatWindow
 * ----------
 * Cửa sổ chat riêng cho từng Peer
 */
public class ChatWindow {

    private final Peer peer;
    private final KeyManager keyManager;
    private final PeerClient peerClient;


    private final Stage stage;
    private final TextArea txtChat = new TextArea();
    private final TextField txtInput = new TextField();
    private final Button btnSend = new Button("Send");

    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss");

    public ChatWindow(Peer peer, KeyManager keyManager, PeerClient peerClient) {
        this.peer = peer;
        this.keyManager = keyManager;
        this.peerClient = peerClient;

        stage = new Stage();
        stage.setTitle("Chat with " + peer.getUsername());

        txtChat.setEditable(false);
        txtChat.setWrapText(true);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));

        HBox bottom = new HBox(6, txtInput, btnSend);
        bottom.setPadding(new Insets(6));

        root.setCenter(txtChat);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 480, 420);
        stage.setScene(scene);

        btnSend.setOnAction(e -> send());
        txtInput.setOnAction(e -> send());

        loadHistory();
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    private void send() {
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;

        appendOutgoing("YOU", text);
        txtInput.clear();

        new Thread(() -> peerClient.sendMessage(peer, text)).start();
        appendToHistoryFileStatic(peer, "YOU", text);
        peer.setLastMessage(text);
    }

    /* ================= HISTORY ================= */

    private void loadHistory() {
        File f = getHistoryFile(peer);
        if (!f.exists()) return;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                txtChat.appendText(line + "\n");
            }
        } catch (IOException ignored) {}
    }

    public void appendIncoming(String from, String msg) {
        Platform.runLater(() -> append("[" + TS.format(new Date()) + "] " + from + ": " + msg));
    }

    private void appendOutgoing(String from, String msg) {
        append("[" + TS.format(new Date()) + "] " + from + ": " + msg);
    }

    private void append(String line) {
        txtChat.appendText(line + "\n");
    }

    /* ================= STATIC HISTORY UTILS ================= */

    public static File getHistoryFile(Peer peer) {
        File dir = new File(System.getProperty("user.home"), ".p2p-chat/history");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, peer.getId() + ".txt");
    }

    public static synchronized void appendToHistoryFileStatic(Peer peer, String from, String msg) {
        File f = getHistoryFile(peer);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
            bw.write("[" + TS.format(new Date()) + "] " + from + ": " + msg);
            bw.newLine();
        } catch (IOException ignored) {}
    }
}
