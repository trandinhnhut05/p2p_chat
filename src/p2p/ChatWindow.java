package p2p;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Chat window per peer. Supports E2EE (AES session) for messages.
 */
public class ChatWindow {
    private final Peer peer;
    private final Stage stage;
    private final TextArea txtHistory;
    private final TextField txtInput;
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final KeyManager keyManager;

    public ChatWindow(Peer peer, KeyManager keyManager) {
        this.peer = peer;
        this.keyManager = keyManager;
        this.stage = new Stage();
        this.stage.setTitle("Chat with " + peer.username + " (" + peer.ip + ":" + peer.port + ")");

        txtHistory = new TextArea();
        txtHistory.setEditable(false);
        txtHistory.setWrapText(true);

        txtInput = new TextField();
        Button btnSend = new Button("Send");
        Button btnClose = new Button("Close");

        HBox bottom = new HBox(8, txtInput, btnSend, btnClose);
        bottom.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setCenter(txtHistory);
        root.setBottom(bottom);
        BorderPane.setMargin(txtHistory, new Insets(8));

        Scene scene = new Scene(root, 700, 500);
        stage.setScene(scene);

        loadHistoryToView();

        btnSend.setOnAction(ev -> doSend());
        txtInput.setOnAction(ev -> doSend());
        btnClose.setOnAction(ev -> stage.close());
    }

    public void show() {
        stage.show();
        stage.toFront();
    }

    private void doSend() {
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;

        if (peer.isBlocked()) {
            alert("Cannot send — peer is blocked.");
            return;
        }

        try {
            // ---- E2EE ----
            String peerId = peer.getId();
            SecretKey aes = keyManager.getSessionKey(peerId);
            if (aes == null) {
                aes = keyManager.createAndSendSessionKey(peerId); // gửi AES qua RSA nếu chưa có
            }
            IvParameterSpec iv = CryptoUtils.generateIv();
            byte[] encrypted = CryptoUtils.encryptAES(text.getBytes(StandardCharsets.UTF_8), aes, iv);

            // gửi iv + encrypted
            new Thread(() -> PeerClient.sendEncryptedMessage(peer, iv.getIV(), encrypted)).start();

            // ---- UI & history ----
            String entry = formatLine("YOU", text);
            appendToView(entry);
            appendToHistoryFile("YOU", text);
            peer.setLastMessage(truncate(text));
            txtInput.clear();
        } catch (Exception e) {
            e.printStackTrace();
            alert("Failed to send encrypted message: " + e.getMessage());
        }
    }

    private void loadHistoryToView() {
        File f = getHistoryFile(peer);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            String content = sb.toString();
            Platform.runLater(() -> txtHistory.setText(content));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void appendToView(String s) { Platform.runLater(() -> txtHistory.appendText(s + "\n")); }

    public static void appendToHistoryFileStatic(Peer peer, String who, String msg) {
        File f = getHistoryFile(peer);
        try {
            f.getParentFile().mkdirs();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                bw.write("[" + time + "] " + who + ": " + msg);
                bw.newLine();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void appendToHistoryFile(String who, String msg) { appendToHistoryFileStatic(peer, who, msg); }

    private String formatLine(String who, String text) {
        String time = df.format(new Date());
        return "[" + time + "] " + who + ": " + text;
    }

    private String truncate(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() <= 80) return s;
        return s.substring(0, 77) + "...";
    }

    private void alert(String t) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, t, ButtonType.OK).showAndWait()); }

    // History file location uses SettingsStore
    public static File getHistoryFile(Peer p) {
        SettingsStore ss = new SettingsStore();
        String folder = ss.getHistoryFolder();
        String safeName = p.ip.replace(':', '_') + "_" + p.port;
        File dir = new File(folder);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, safeName + ".txt");
    }

    // ------------------ Incoming encrypted message ------------------
    public void appendIncomingEncrypted(byte[] iv, byte[] encrypted) {
        try {
            SecretKey aes = keyManager.getSessionKey(peer.getId());
            if (aes == null) return; // chưa có AES, không giải mã được
            byte[] decrypted = CryptoUtils.decryptAES(encrypted, aes, new IvParameterSpec(iv));
            String msg = new String(decrypted, StandardCharsets.UTF_8);
            appendIncoming(peer.username != null ? peer.username : peer.ip, msg);
        } catch (Exception e) {
            e.printStackTrace();
            appendIncoming(peer.username != null ? peer.username : peer.ip, "[Failed to decrypt message]");
        }
    }

    // used by MainUI to show incoming plaintext message (after decryption or non-E2EE)
    public void appendIncoming(String who, String message) {
        String entry = formatLine(who, message);
        appendToView(entry);
        appendToHistoryFile(who, message);
        peer.setLastMessage(truncate(message));
    }
}
