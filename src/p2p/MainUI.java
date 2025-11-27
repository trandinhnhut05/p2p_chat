package p2p;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainUI extends Application implements PeerServer.ConnectionListener, PeerHandler.MessageCallback {

    private final ObservableList<Peer> peerList = FXCollections.observableArrayList();
    private PeerDiscoverySender sender;
    private PeerDiscoveryListener listener;
    private PeerServer server;

    private ListView<Peer> lstPeers;
    private TextArea txtChat;
    private TextField txtInput;
    private Button btnSend, btnFile;
    private Label lblMe;

    private int servicePort = 60000;
    private int discoveryPort = 9999;
    private String username = "User" + (int)(Math.random() * 1000);
    private String localIP;

    @Override
    public void start(Stage stage) throws Exception {
        localIP = getLocalAddress();

        // Settings UI
        TextField txtUsername = new TextField(username);
        TextField txtPort = new TextField(String.valueOf(servicePort));
        TextField txtDiscovery = new TextField(String.valueOf(discoveryPort));
        Button btnApply = new Button("Apply & Start");

        HBox settings = new HBox(10,
                new Label("Name:"), txtUsername,
                new Label("TCP Port:"), txtPort,
                new Label("Discovery UDP:"), txtDiscovery,
                btnApply);
        settings.setPadding(new Insets(10));

        // Peer list
        lstPeers = new ListView<>(peerList);
        lstPeers.setPrefWidth(320);
        Label lblPeers = new Label("Peers (double click để reply riêng)");
        VBox leftPane = new VBox(6, lblPeers, lstPeers);
        leftPane.setPadding(new Insets(8));

        // Cell factory displays last-seen/time-ago
        lstPeers.setCellFactory(lv -> new ListCell<Peer>() {
            @Override
            protected void updateItem(Peer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    long now = System.currentTimeMillis();
                    long last = item.getLastSeen();
                    String timeAgo = last > 0 ? formatTimeAgo(now - last) : "unknown";
                    setText(item.username + " (" + item.ip + ":" + item.port + ")  •  " + timeAgo);
                    if (isSelected()) setStyle("-fx-background-color: lightblue;");
                    else setStyle("");
                }
            }

            private String formatTimeAgo(long deltaMs) {
                long s = TimeUnit.MILLISECONDS.toSeconds(deltaMs);
                if (s < 2) return "just now";
                if (s < 60) return s + "s ago";
                long m = TimeUnit.MILLISECONDS.toMinutes(deltaMs);
                if (m < 60) return m + "m ago";
                long h = TimeUnit.MILLISECONDS.toHours(deltaMs);
                return h + "h ago";
            }
        });

        // Chat UI
        txtChat = new TextArea(); txtChat.setEditable(false);
        txtInput = new TextField(); txtInput.setPrefWidth(420);
        btnSend = new Button("Send"); btnFile = new Button("Send File");
        HBox sendBox = new HBox(8, txtInput, btnSend, btnFile); sendBox.setPadding(new Insets(8));
        VBox right = new VBox(8, txtChat, sendBox); right.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setTop(settings); root.setLeft(leftPane); root.setCenter(right);
        lblMe = new Label("Not started"); root.setBottom(lblMe);
        BorderPane.setMargin(lblMe, new Insets(6));

        Scene scene = new Scene(root, 1000, 620);
        stage.setScene(scene);
        stage.setTitle("P2P Chat - LAN (with online indicator)");
        stage.show();

        // Actions
        btnApply.setOnAction(ev -> {
            username = txtUsername.getText().trim();
            try { servicePort = Integer.parseInt(txtPort.getText().trim()); }
            catch (Exception ex) { showAlert("Invalid TCP port"); return; }
            try { discoveryPort = Integer.parseInt(txtDiscovery.getText().trim()); }
            catch (Exception ex) { showAlert("Invalid UDP discovery port"); return; }

            if (!PeerServer.isPortAvailable(servicePort)) {
                showAlert("TCP port " + servicePort + " is in use. Choose another.");
                return;
            }

            startNetwork();
            lblMe.setText("Running as: " + username + " @ " + localIP + " TCP:" + servicePort + " UDP:" + discoveryPort);
            btnApply.setDisable(true);
            txtUsername.setEditable(false); txtPort.setEditable(false); txtDiscovery.setEditable(false);
        });

        btnSend.setOnAction(ev -> sendMessage());
        txtInput.setOnAction(ev -> sendMessage());

        btnFile.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(stage);
            if (f != null) sendFile(f);
        });

        lstPeers.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                Peer p = lstPeers.getSelectionModel().getSelectedItem();
                if (p != null) {
                    txtInput.setText("@" + p.username + " ");
                    txtInput.requestFocus();
                }
            }
        });

        stage.setOnCloseRequest(ev -> stopNetwork());
    }

    private void startNetwork() {
        // start discovery listener & sender
        listener = new PeerDiscoveryListener(discoveryPort);
        listener.start();

        sender = new PeerDiscoverySender(username, servicePort, discoveryPort);
        sender.start();

        // refresher: update list every second (snapshot already removes timed-out peers)
        Thread refresher = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                    List<Peer> snap = listener.snapshot();
                    Platform.runLater(() -> peerList.setAll(snap));
                } catch (InterruptedException ignored) { break; }
            }
        });
        refresher.setDaemon(true);
        refresher.start();

        // start TCP server
        server = new PeerServer(servicePort, this);
        server.start();
    }

    private void stopNetwork() {
        if (sender != null) sender.shutdown();
        if (listener != null) listener.shutdown();
        if (server != null) server.shutdown();
    }

    private void sendMessage() {
        String text = txtInput.getText().trim(); if (text.isEmpty()) return;
        Peer sel = lstPeers.getSelectionModel().getSelectedItem();
        if (sel != null) {
            appendText("[YOU -> " + sel.username + "] " + text);
            txtInput.clear();
            new Thread(() -> PeerClient.sendMessage(sel, text)).start();
        } else {
            appendText("[YOU -> ALL] " + text);
            txtInput.clear();
            for (Peer p : peerList) new Thread(() -> PeerClient.sendMessage(p, text)).start();
        }
    }

    private void sendFile(File f) {
        Peer sel = lstPeers.getSelectionModel().getSelectedItem();
        if (sel != null) {
            appendText("[YOU -> " + sel.username + "] Sending file: " + f.getName());
            new Thread(() -> FileSender.sendFile(sel, f)).start();
        } else {
            appendText("[YOU -> ALL] Sending file: " + f.getName());
            for (Peer p : peerList) new Thread(() -> FileSender.sendFile(p, f)).start();
        }
    }

    private void appendText(String s) { txtChat.appendText(s + "\n"); }

    private void showAlert(String t) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, t, ButtonType.OK).showAndWait()); }

    private String getLocalAddress() { try { return InetAddress.getLocalHost().getHostAddress(); } catch (Exception e) { return "127.0.0.1"; } }

    @Override
    public void onNewConnection(java.net.Socket socket) {
        PeerHandler handler = new PeerHandler(socket, this);
        handler.start();
    }

    @Override
    public void onMessage(String from, String message) {
        appendText("[" + from + "] " + message);
    }

    @Override
    public void onFileReceived(String from, File file) {
        appendText("[" + from + "] Received file: " + file.getAbsolutePath());
    }
}
