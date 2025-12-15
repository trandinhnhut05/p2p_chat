package p2p;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Chat window popup per peer. Loads history, appends messages, allows send.
 */
public class ChatWindow {
    private final Peer peer;
    private final Stage stage;
    private final TextArea txtHistory;
    private final TextField txtInput;
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ChatWindow(Peer peer) {
        this.peer = peer;
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

        new Thread(() -> PeerClient.sendMessage(peer, text)).start();

        String entry = formatLine("YOU", text);
        appendToView(entry);
        appendToHistoryFile("YOU", text);
        peer.setLastMessage(truncate(text));
        txtInput.clear();
    }

    private void loadHistoryToView() {
        File f = getHistoryFile(peer);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line; StringBuilder sb = new StringBuilder();
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

    // used by MainUI to show incoming messages in open chat windows
    public void appendIncoming(String who, String message) {
        String entry = formatLine(who, message);
        appendToView(entry);
        appendToHistoryFile(who, message);
        peer.setLastMessage(truncate(message));
    }
}
