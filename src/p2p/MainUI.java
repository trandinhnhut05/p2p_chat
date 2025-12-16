package p2p;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javafx.scene.image.ImageView;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.io.*;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * MainUI - full, compatible with discovery fingerprint + ChatWindow + SettingsStore
 */
public class MainUI extends Application implements PeerServer.ConnectionListener {

    private VideoSender videoSender;
    private VideoReceiver videoReceiver;
    private boolean inVideoCall = false;
    private final int VIDEO_PORT = 8000;
    private Button btnEndVideo;
    private ImageView videoView;
    private Button btnVideoCall;

    // ===== Voice call state =====
    private boolean inCall = false;
    private VoiceSender voiceSender;
    private VoiceReceiver voiceReceiver;

    // Port voice (UDP)
    private final int voicePort = 7000;


    private final ObservableList<Peer> peerList = FXCollections.observableArrayList();
    private FilteredList<Peer> filteredPeers;

    private PeerDiscoverySender sender;
    private PeerDiscoveryListener listener;
    private PeerServer server;

    private TableView<Peer> tblPeers;
    private javafx.scene.control.TextArea txtChat;
    private javafx.scene.control.TextField txtInput;
    private Button btnSend, btnFile;

    private Label lblMe;

    private int servicePort = 60000;
    private int discoveryPort = 9999;
    private String username = "User" + (int) (Math.random() * 1000);
    private String localIP;

    private ScheduledExecutorService uiRefresher;

    private final Map<String, ChatWindow> openChats = new ConcurrentHashMap<>();
    private SettingsStore settingsStore;

    // pending pings: timestamp -> Peer
    private final ConcurrentMap<Long, Peer> pendingPings = new ConcurrentHashMap<>();

    // tray icon for notifications
    private TrayIcon trayIcon = null;
    private boolean appHasFocus = true;

    @Override
    public void start(Stage stage) throws Exception {
        localIP = getLocalAddress();
        settingsStore = new SettingsStore();
        initTrayIcon();

        // top settings & controls
        javafx.scene.control.TextField txtUsername = new javafx.scene.control.TextField(username);
        javafx.scene.control.TextField txtPort = new javafx.scene.control.TextField(String.valueOf(servicePort));
        javafx.scene.control.TextField txtDiscovery = new javafx.scene.control.TextField(String.valueOf(discoveryPort));
        Button btnApply = new Button("Apply & Start");

        javafx.scene.control.TextField txtSearch = new javafx.scene.control.TextField();
        txtSearch.setPromptText("Search name / ip / port...");
        Button btnChooseHistory = new Button("History Folder");
        Button btnOpenHistory = new Button("Open History Folder");

        HBox settings = new HBox(8,
                new Label("Name:"), txtUsername,
                new Label("TCP Port:"), txtPort,
                new Label("Discovery UDP:"), txtDiscovery,
                btnApply,
                new Label("Search:"), txtSearch,
                btnChooseHistory, btnOpenHistory
        );
        settings.setPadding(new Insets(10));

        // table
        tblPeers = new TableView<>();
        tblPeers.setPrefWidth(760);
        filteredPeers = new FilteredList<>(peerList, p -> true);
        tblPeers.setItems(filteredPeers);

        TableColumn<Peer, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().username));
        colName.setPrefWidth(140);

        TableColumn<Peer, String> colAddr = new TableColumn<>("Address");
        colAddr.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().ip + ":" + cell.getValue().port));
        colAddr.setPrefWidth(140);

        TableColumn<Peer, String> colLast = new TableColumn<>("Last seen");
        colLast.setCellValueFactory(cell -> {
            long last = cell.getValue().getLastSeen();
            String s = (last > 0) ? formatTimeAgo(System.currentTimeMillis() - last) : "unknown";
            return new ReadOnlyStringWrapper(s);
        });
        colLast.setPrefWidth(100);

        TableColumn<Peer, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cell -> {
            long last = cell.getValue().getLastSeen();
            boolean online = (last > 0) && (System.currentTimeMillis() - last <= PeerDiscoveryListener.PEER_TIMEOUT_MS);
            String st = online ? "ONLINE" : "OFFLINE";
            if (cell.getValue().isMuted()) st += " (MUTED)";
            if (cell.getValue().isBlocked()) st += " (BLOCKED)";
            return new ReadOnlyStringWrapper(st);
        });
        colStatus.setPrefWidth(120);

        TableColumn<Peer, String> colRTT = new TableColumn<>("RTT (ms)");
        colRTT.setCellValueFactory(cell -> {
            long r = cell.getValue().getLastPingMs();
            return new ReadOnlyStringWrapper(r >= 0 ? String.valueOf(r) : "-");
        });
        colRTT.setPrefWidth(80);

        TableColumn<Peer, String> colLastMsg = new TableColumn<>("Last message");
        colLastMsg.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getLastMessage()));
        colLastMsg.setPrefWidth(420);

        tblPeers.getColumns().addAll(colName, colAddr, colLast, colStatus, colRTT, colLastMsg);

        Label lblPeers = new Label("Peers (right-click for actions)");
        VBox leftPane = new VBox(6, lblPeers, tblPeers);
        leftPane.setPadding(new Insets(8));

        // chat area
        txtChat = new javafx.scene.control.TextArea();
        txtChat.setEditable(false);
        txtInput = new javafx.scene.control.TextField();
        txtInput.setPrefWidth(640);
        btnSend = new Button("Send");
        btnFile = new Button("Send File");
        videoView = new ImageView();
        videoView.setFitWidth(320);
        videoView.setFitHeight(240);
        videoView.setPreserveRatio(true);

        btnVideoCall = new Button("Video Call");
        btnEndVideo = new Button("End Video");
        btnEndVideo.setDisable(true);


        HBox sendBox = new HBox(8, txtInput, btnSend, btnFile, btnVideoCall, btnEndVideo);

        sendBox.setPadding(new Insets(8));
        VBox right = new VBox(8, txtChat, videoView, sendBox);
        right.setPadding(new Insets(8));


        BorderPane root = new BorderPane();
        root.setTop(settings);
        root.setLeft(leftPane);
        root.setCenter(right);

        lblMe = new Label("Not started");
        root.setBottom(lblMe);
        BorderPane.setMargin(lblMe, new Insets(6));

        Scene scene = new Scene(root, 1400, 720);
        stage.setScene(scene);
        stage.setTitle("P2P Chat - LAN (fingerprint ready)");
        stage.show();

        // track focus for notifications
        stage.focusedProperty().addListener((obs, oldV, newV) -> appHasFocus = newV);

        // actions wiring
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
            txtUsername.setEditable(false);
            txtPort.setEditable(false);
            txtDiscovery.setEditable(false);
        });

        txtSearch.textProperty().addListener((obs, oldV, newV) -> {
            String q = (newV == null) ? "" : newV.trim().toLowerCase();
            filteredPeers.setPredicate(peer -> {
                if (q.isEmpty()) return true;
                if (peer.username != null && peer.username.toLowerCase().contains(q)) return true;
                if (peer.ip != null && peer.ip.toLowerCase().contains(q)) return true;
                if ((peer.port + "").contains(q)) return true;
                if (peer.getFingerprint() != null && peer.getFingerprint().toLowerCase().contains(q)) return true;
                return false;
            });
        });

        btnChooseHistory.setOnAction(ev -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Choose History Folder");
            File sel = dc.showDialog(stage);
            if (sel != null) {
                settingsStore.setHistoryFolder(sel.getAbsolutePath());
                showAlert("History folder set to: " + sel.getAbsolutePath());
            }
        });

        btnOpenHistory.setOnAction(ev -> {
            try { Desktop.getDesktop().open(new File(settingsStore.getHistoryFolder())); }
            catch (Exception e) { showAlert("Cannot open history folder: " + e.getMessage()); }
        });

        btnSend.setOnAction(ev -> sendMessage());

        txtInput.setOnAction(ev -> sendMessage());


        btnFile.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(stage);
            if (f != null) {
                Peer sel = tblPeers.getSelectionModel().getSelectedItem();
                if (sel == null) showAlert("Select peer to send file.");
                else {
                    appendText("[YOU -> " + sel.username + "] Sending file: " + f.getName());
                    new Thread(() -> FileSender.sendFile(sel, f)).start();
                    ChatWindow.appendToHistoryFileStatic(sel, "YOU", "[file] " + f.getName());
                    sel.setLastMessage("[file] " + f.getName());
                    tblPeers.refresh();
                }
            }
        });
        btnVideoCall.setOnAction(e -> {
            Peer p = tblPeers.getSelectionModel().getSelectedItem();
            if (p == null || inVideoCall) {
                showAlert("Select peer or already in call");
                return;
            }

            try {
                InetAddress ip = InetAddress.getByName(p.ip);

                videoReceiver = new VideoReceiver(VIDEO_PORT, videoView);
                videoSender = new VideoSender(ip, VIDEO_PORT);

                videoReceiver.start();
                videoSender.start();

                inVideoCall = true;
                btnVideoCall.setDisable(true);
                btnEndVideo.setDisable(false);

                appendText("[VIDEO] Calling " + p.username);
            } catch (Exception ex) {
                ex.printStackTrace();
                showAlert("Video call failed: " + ex.getMessage());
            }
        });
        btnEndVideo.setOnAction(e -> {
            if (!inVideoCall) return;

            videoSender.stopSend();
            videoReceiver.stopReceive();

            inVideoCall = false;
            btnVideoCall.setDisable(false);
            btnEndVideo.setDisable(true);

            appendText("[VIDEO] Ended");
        });



        // context menu per-row
        tblPeers.setRowFactory(tv -> {
            TableRow<Peer> row = new TableRow<>();
            ContextMenu ctx = new ContextMenu();
            MenuItem miOpen = new MenuItem("Open Chat Window...");
            MenuItem miSendFile = new MenuItem("Send File...");
            MenuItem miPing = new MenuItem("Ping (RTT)");
            MenuItem miExport = new MenuItem("Export history...");
            MenuItem miMute = new MenuItem("Mute");
            MenuItem miBlock = new MenuItem("Block");
            MenuItem miRemove = new MenuItem("Remove");

            miOpen.setOnAction(ev -> { Peer p = row.getItem(); if (p != null) openChatWindow(p); });

            miSendFile.setOnAction(ev -> {
                Peer p = row.getItem(); if (p == null) return;
                FileChooser fc = new FileChooser(); File f = fc.showOpenDialog(stage);
                if (f != null) {
                    appendText("[YOU -> " + p.username + "] Sending file: " + f.getName());
                    new Thread(() -> FileSender.sendFile(p, f)).start();
                    ChatWindow.appendToHistoryFileStatic(p, "YOU", "[file] " + f.getName());
                    p.setLastMessage("[file] " + f.getName());
                    tblPeers.refresh();
                }
            });

            miPing.setOnAction(ev -> {
                Peer p = row.getItem(); if (p == null) return;
                long ts = System.currentTimeMillis();
                pendingPings.put(ts, p);
                PeerClient.sendPing(p, ts);
                appendText("[PING -> " + p.username + "]");
            });

            miExport.setOnAction(ev -> {
                Peer p = row.getItem(); if (p == null) return;
                FileChooser fc = new FileChooser();
                fc.setInitialFileName(p.getId() + ".txt");
                File out = fc.showSaveDialog(stage);
                if (out != null) {
                    try {
                        File src = ChatWindow.getHistoryFile(p);
                        java.nio.file.Files.createDirectories(out.getParentFile().toPath());
                        java.nio.file.Files.copy(src.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        showAlert("Exported history to: " + out.getAbsolutePath());
                    } catch (Exception ex) {
                        showAlert("Export failed: " + ex.getMessage());
                    }
                }
            });

            miMute.setOnAction(ev -> {
                Peer p = row.getItem(); if (p == null) return;
                String id = p.getId();
                boolean now = !p.isMuted();
                p.setMuted(now);
                settingsStore.setMutedById(id, now);
                miMute.setText(now ? "Unmute" : "Mute");
                appendText(now ? "[Muted " + p.username + "]" : "[Unmuted " + p.username + "]");
                tblPeers.refresh();
            });

            miBlock.setOnAction(ev -> {
                Peer p = row.getItem(); if (p == null) return;
                String id = p.getId();
                boolean now = !p.isBlocked();
                p.setBlocked(now);
                settingsStore.setBlockedById(id, now);
                miBlock.setText(now ? "Unblock" : "Block");
                appendText(now ? "[Blocked " + p.username + "]" : "[Unblocked " + p.username + "]");
                tblPeers.refresh();
            });

            miRemove.setOnAction(ev -> {
                Peer p = row.getItem(); if (p == null) return;
                boolean removed = (listener != null) && listener.removePeer(p.ip, p.port);
                if (removed) appendText("[Removed peer " + p.username + "]");
                Platform.runLater(() -> { peerList.remove(p); tblPeers.refresh(); });
            });

            ctx.getItems().addAll(miOpen, miSendFile, miPing, miExport, new SeparatorMenuItem(), miMute, miBlock, new SeparatorMenuItem(), miRemove);
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(ctx));

            row.setOnContextMenuRequested(ev -> {
                Peer p = row.getItem();
                if (p != null) {
                    miMute.setText(p.isMuted() ? "Unmute" : "Mute");
                    miBlock.setText(p.isBlocked() ? "Unblock" : "Block");
                }
            });

            return row;
        });

        tblPeers.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                Peer p = tblPeers.getSelectionModel().getSelectedItem();
                if (p != null) openChatWindow(p);
            }
        });

        stage.setOnCloseRequest(ev -> stopNetwork());
    }

    // ---------- tray icon ----------
    private void initTrayIcon() {
        try {
            if (!SystemTray.isSupported()) return;
            SystemTray tray = SystemTray.getSystemTray();
            Image img = Toolkit.getDefaultToolkit().createImage(new byte[0]);
            trayIcon = new TrayIcon(img, "P2P Chat");
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip("P2P Chat");
            try { tray.add(trayIcon); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private void showDesktopNotification(String title, String message) {
        try {
            if (trayIcon != null) trayIcon.displayMessage(title, message, MessageType.INFO);
        } catch (Exception ignored) {}
    }

    // ---------- network start/stop ----------
    private void startNetwork() {
        listener = new PeerDiscoveryListener(discoveryPort);
        listener.start();

        sender = new PeerDiscoverySender(username, servicePort, discoveryPort);
        sender.start();

        uiRefresher = Executors.newSingleThreadScheduledExecutor();
        uiRefresher.scheduleAtFixedRate(() -> {
            try {
                List<Peer> snap = listener.snapshot();
                Platform.runLater(() -> {
                    // merge/preserve flags
                    for (Peer p : snap) {
                        boolean exists = false;
                        for (Peer ex : peerList) {
                            // equality uses fingerprint when present, else ip+port
                            if (ex.equals(p)) {
                                ex.setLastSeen(p.getLastSeen());
                                // if new snapshot has fingerprint and existing didn't, migrate id+history
                                if ((ex.getFingerprint() == null || ex.getFingerprint().isBlank()) && (p.getFingerprint() != null && !p.getFingerprint().isBlank())) {
                                    String oldId = ex.getId();
                                    ex.setFingerprint(p.getFingerprint());
                                    migrateHistoryFile(oldId, ex.getId());
                                    // reapply persisted flags by id
                                    ex.setMuted(settingsStore.isMutedById(ex.getId()));
                                    ex.setBlocked(settingsStore.isBlockedById(ex.getId()));
                                }
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            // new peer: set persisted flags by id (fingerprint or ip_port)
                            boolean muted = settingsStore.isMutedById(p.getId());
                            boolean blocked = settingsStore.isBlockedById(p.getId());
                            p.setMuted(muted);
                            p.setBlocked(blocked);
                            peerList.add(p);
                        }
                    }
                    // remove peers not in snapshot
                    peerList.removeIf(ex -> snap.stream().noneMatch(s -> s.equals(ex)));
                    tblPeers.refresh();
                });
            } catch (Exception ignored) {}
        }, 0, 1, TimeUnit.SECONDS);

        server = new PeerServer(servicePort, this);
        server.start();
    }

    private void stopNetwork() {
        if (sender != null) sender.shutdown();
        if (listener != null) listener.shutdown();
        if (server != null) server.shutdown();
        if (uiRefresher != null) uiRefresher.shutdownNow();
        try { if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon); } catch (Exception ignored) {}
    }

    // ---------- UI actions ----------
    private void sendMessage() {
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;

        Peer sel = tblPeers.getSelectionModel().getSelectedItem();
        if (sel == null) { showAlert("Select a peer from the table to send to."); return; }
        if (sel.isBlocked()) { appendText("Cannot send — peer is blocked."); return; }

        appendText("[YOU -> " + sel.username + "] " + text);
        txtInput.clear();

        new Thread(() -> PeerClient.sendMessage(sel, text)).start();
        ChatWindow.appendToHistoryFileStatic(sel, "YOU", text);
        sel.setLastMessage(truncateForPreview(text));
        tblPeers.refresh();

        ChatWindow cw = openChats.get(sel.ip + ":" + sel.port);
        if (cw != null) cw.appendIncoming("YOU", text);
    }

    private void openChatWindow(Peer p) {
        String key = p.ip + ":" + p.port;
        ChatWindow cw = openChats.get(key);
        if (cw == null) {
            cw = new ChatWindow(p);
            openChats.put(key, cw);
        }
        cw.show();
    }

    private void appendText(String s) { txtChat.appendText(s + "\n"); }

    // ---------- PeerServer.ConnectionListener ----------
    @Override
    public void onNewConnection(java.net.Socket socket) {
        // create handler with callback that matches PeerHandler.MessageCallback (fromIp, fingerprint, message)
        PeerHandler handler = new PeerHandler(socket, new PeerHandler.MessageCallback() {
            @Override
            public void onMessage(String fromIp, String fingerprint, String message) {
                handleIncomingMessage(fromIp, fingerprint, message);
            }

            @Override
            public void onFileReceived(String fromIp, String filename, File file) {
                handleIncomingFile(fromIp, filename, file);
            }
        });
        handler.start();
    }

    // ---------- Incoming handling ----------
    private void handleIncomingMessage(String fromIp, String fingerprint, String message) {
        Platform.runLater(() -> {
            // RTT handling for /PONG:<ts>
            if (message != null && message.startsWith("/PONG:")) {
                String tsStr = message.substring("/PONG:".length());
                try {
                    long ts = Long.parseLong(tsStr);
                    Peer p = pendingPings.remove(ts);
                    if (p != null) {
                        long rtt = System.currentTimeMillis() - ts;
                        p.setLastPingMs(rtt);
                        appendText("[RTT " + p.username + "]: " + rtt + " ms");
                        tblPeers.refresh();
                        return;
                    }
                } catch (Exception ignored) {}
            }

            // normal message
            Peer matched = findOrCreatePeerByIp(fromIp, fingerprint);

            // if matched gained fingerprint just now -> migrate history & reapply settings
            if (fingerprint != null && !fingerprint.isBlank() && (matched.getFingerprint() == null || matched.getFingerprint().isBlank())) {
                String oldId = matched.getId();
                matched.setFingerprint(fingerprint);
                migrateHistoryFile(oldId, matched.getId());
                matched.setMuted(settingsStore.isMutedById(matched.getId()));
                matched.setBlocked(settingsStore.isBlockedById(matched.getId()));
            }

            // save to history
            ChatWindow.appendToHistoryFileStatic(matched, matched.username != null ? matched.username : fromIp, message);

            // respect block/mute by id
            String id = matched.getId();
            boolean blocked = settingsStore.isBlockedById(id);
            boolean muted = settingsStore.isMutedById(id);

            matched.setLastMessage(truncateForPreview(message));
            matched.setLastSeen(System.currentTimeMillis());

            if (!blocked) {
                if (!muted) {
                    appendText("[" + matched.username + "@" + fromIp + "] " + message);
                    if (!appHasFocus) showDesktopNotification("Message from " + matched.username, truncateForPreview(message));
                }
                ChatWindow cw = openChats.get(matched.ip + ":" + matched.port);
                if (cw != null) cw.appendIncoming(matched.username, message);
            }

            tblPeers.refresh();
        });
    }

    private void handleIncomingFile(String fromIp, String filename, File file) {
        Platform.runLater(() -> {
            Peer matched = findOrCreatePeerByIp(fromIp, null);
            matched.setLastMessage("[file] " + file.getName());
            matched.setLastSeen(System.currentTimeMillis());
            ChatWindow.appendToHistoryFileStatic(matched, matched.username != null ? matched.username : fromIp, "[file] " + file.getName());

            String id = matched.getId();
            boolean blocked = settingsStore.isBlockedById(id);
            boolean muted = settingsStore.isMutedById(id);

            if (!blocked && !muted) {
                appendText("[" + matched.username + "@" + fromIp + "] Received file: " + file.getAbsolutePath());
                if (!appHasFocus) showDesktopNotification("File from " + matched.username, file.getName());
            }

            ChatWindow cw = openChats.get(matched.ip + ":" + matched.port);
            if (cw != null) cw.appendIncoming(matched.username, "[file] " + file.getName());

            tblPeers.refresh();
        });
    }

    // find existing peer by ip (and port if same), else create temporary with username=ip
    private Peer findOrCreatePeerByIp(String ip, String fingerprint) {
        for (Peer p : peerList) {
            if (p.ip.equals(ip)) return p;
        }
        // not found -> create temp peer (port unknown 0)
        Peer p = new Peer(ip, ip, 0);
        p.setLastSeen(System.currentTimeMillis());
        if (fingerprint != null && !fingerprint.isBlank()) p.setFingerprint(fingerprint);
        // apply persisted flags if available
        p.setMuted(settingsStore.isMutedById(p.getId()));
        p.setBlocked(settingsStore.isBlockedById(p.getId()));
        peerList.add(p);
        return p;
    }

    // migrate history file from oldId -> newId (merge if new exists)
    private void migrateHistoryFile(String oldId, String newId) {
        try {
            if (oldId == null || newId == null || oldId.equals(newId)) return;
            Peer dummyOld = new Peer(oldId, oldId, 0);
            Peer dummyNew = new Peer(newId, newId, 0);
            File fOld = ChatWindow.getHistoryFile(dummyOld);
            File fNew = ChatWindow.getHistoryFile(dummyNew);
            if (!fOld.exists()) return;
            if (!fNew.exists()) {
                java.nio.file.Files.createDirectories(fNew.getParentFile().toPath());
                java.nio.file.Files.move(fOld.toPath(), fNew.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                // append old into new then delete old
                try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fNew, true), java.nio.charset.StandardCharsets.UTF_8));
                     BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fOld), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        bw.write(line);
                        bw.newLine();
                    }
                }
                fOld.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- utils ----------
    private static String truncateForPreview(String msg) {
        if (msg == null) return "";
        String t = msg.replaceAll("\\s+", " ").trim();
        if (t.length() <= 80) return t;
        return t.substring(0, 77) + "...";
    }

    static String formatTimeAgo(long deltaMs) {
        long s = TimeUnit.MILLISECONDS.toSeconds(deltaMs);
        if (s < 2) return "just now";
        if (s < 60) return s + "s ago";
        long m = TimeUnit.MILLISECONDS.toMinutes(deltaMs);
        if (m < 60) return m + "m ago";
        long h = TimeUnit.MILLISECONDS.toHours(deltaMs);
        return h + "h ago";
    }

    private void showAlert(String t) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, t, ButtonType.OK).showAndWait()); }

    private String getLocalAddress() {
        try { return InetAddress.getLocalHost().getHostAddress(); } catch (Exception e) { return "127.0.0.1"; }
    }

    @Override
    public void stop() throws Exception {
        stopNetwork();
        super.stop();
    }
}
