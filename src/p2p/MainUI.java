package p2p;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.image.ImageView;
import javafx.stage.*;

import p2p.crypto.KeyManager;

import java.io.File;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * MainUI – FINAL SYNC VERSION
 */
public class MainUI extends Application
        implements PeerServer.ConnectionListener {

    private KeyManager keyManager;
    private SettingsStore settingsStore;

    private VoiceSender voiceSender;
    private VoiceReceiver voiceReceiver;

    private int localVideoPort;
    private int localAudioPort;




    /* ================= NETWORK ================= */
    private PeerDiscoverySender discoverySender;
    private PeerDiscoveryListener discoveryListener;
    private PeerServer peerServer;

    private final ObservableList<Peer> peerList = FXCollections.observableArrayList();
    private FilteredList<Peer> filteredPeers;

    private ScheduledExecutorService uiRefresher;

    /* ================= UI ================= */
    private TableView<Peer> tblPeers;
    private TextArea txtChat;
    private TextField txtInput;

    private ImageView videoView;
    private Button btnVideoCall, btnEndVideo;

    private Label lblMe;

    private final Map<String, ChatWindow> openChats = new ConcurrentHashMap<>();

    /* ================= CONFIG ================= */
    private int servicePort = 60000;
    private int discoveryPort = 9999;
    private String username = "User" + (int)(Math.random() * 1000);
    private String localIP;

    /* ================= VIDEO ================= */
    private VideoSender videoSender;
    private VideoReceiver videoReceiver;
    private boolean inCall = false;

    /* ===================================================== */

    @Override
    public void start(Stage stage) throws Exception {
        localIP = InetAddress.getLocalHost().getHostAddress();
        keyManager = new KeyManager();
        settingsStore = new SettingsStore();
        localVideoPort = servicePort + 100;
        localAudioPort = servicePort + 200;


        /* ---------- TOP BAR ---------- */
        TextField txtName = new TextField(username);
        TextField txtTcp = new TextField(String.valueOf(servicePort));
        TextField txtUdp = new TextField(String.valueOf(discoveryPort));
        Button btnStart = new Button("Start");

        TextField txtSearch = new TextField();
        txtSearch.setPromptText("Search peer...");

        HBox top = new HBox(8,
                new Label("Name"), txtName,
                new Label("TCP"), txtTcp,
                new Label("UDP"), txtUdp,
                btnStart,
                new Label("Search"), txtSearch
        );
        top.setPadding(new Insets(10));

        /* ---------- PEER TABLE ---------- */
        tblPeers = new TableView<>();
        filteredPeers = new FilteredList<>(peerList, p -> true);
        tblPeers.setItems(filteredPeers);

        TableColumn<Peer, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getUsername()));

        TableColumn<Peer, String> colIp = new TableColumn<>("IP");
        colIp.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(c.getValue().getIp()));

        TableColumn<Peer, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> {
            boolean online =
                    System.currentTimeMillis() - c.getValue().getLastSeen()
                            < PeerDiscoveryListener.PEER_TIMEOUT_MS;
            return new ReadOnlyStringWrapper(online ? "ONLINE" : "OFFLINE");
        });

        tblPeers.getColumns().addAll(colName, colIp, colStatus);

        VBox left = new VBox(6, new Label("Peers"), tblPeers);
        left.setPadding(new Insets(8));

        /* ---------- CHAT ---------- */
        txtChat = new TextArea();
        txtChat.setEditable(false);

        txtInput = new TextField();
        Button btnSend = new Button("Send");
        Button btnFile = new Button("Send File");

        btnVideoCall = new Button("Video Call");
        btnEndVideo = new Button("End Video");
        btnEndVideo.setDisable(true);

        videoView = new ImageView();
        videoView.setFitWidth(320);
        videoView.setFitHeight(240);
        videoView.setPreserveRatio(true);

        HBox sendBox = new HBox(8,
                txtInput, btnSend, btnFile, btnVideoCall, btnEndVideo);

        VBox right = new VBox(8, txtChat, videoView, sendBox);
        right.setPadding(new Insets(8));

        /* ---------- ROOT ---------- */
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setLeft(left);
        root.setCenter(right);

        lblMe = new Label("Not started");
        root.setBottom(lblMe);

        stage.setScene(new Scene(root, 1200, 700));
        stage.setTitle("P2P Chat – Synced");
        stage.show();

        /* ================= EVENTS ================= */

        btnStart.setOnAction(e -> {
            try {
                username = txtName.getText().trim();
                servicePort = Integer.parseInt(txtTcp.getText().trim());
                discoveryPort = Integer.parseInt(txtUdp.getText().trim());
            } catch (Exception ex) {
                alert("Invalid port");
                return;
            }
            startNetwork();
            lblMe.setText(username + " @ " + localIP);
            btnStart.setDisable(true);
        });

        btnSend.setOnAction(e -> sendMessage());
        txtInput.setOnAction(e -> sendMessage());

        btnFile.setOnAction(e -> sendFile(stage));

        btnVideoCall.setOnAction(e -> startCall());
        btnEndVideo.setOnAction(e -> stopCall());



        tblPeers.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 &&
                    e.getButton() == MouseButton.PRIMARY) {
                Peer p = tblPeers.getSelectionModel().getSelectedItem();
                if (p != null) openChat(p);
            }
        });

        stage.setOnCloseRequest(e -> stopNetwork());
    }

    /* ================= NETWORK ================= */

    private void startNetwork() {
        discoveryListener = new PeerDiscoveryListener(discoveryPort);
        discoveryListener.start();

        discoverySender =
                new PeerDiscoverySender(username, servicePort, discoveryPort);
        discoverySender.start();

        peerServer = new PeerServer(servicePort, this);
        peerServer.start();

        uiRefresher = Executors.newSingleThreadScheduledExecutor();
        uiRefresher.scheduleAtFixedRate(() -> {
            List<Peer> snap = discoveryListener.snapshot();
            Platform.runLater(() -> {
                peerList.setAll(snap);
                tblPeers.refresh();
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopNetwork() {
        if (discoverySender != null) discoverySender.shutdown();
        if (discoveryListener != null) discoveryListener.shutdown();
        if (peerServer != null) peerServer.shutdown();
        if (uiRefresher != null) uiRefresher.shutdownNow();
        stopCall();
    }

    /* ================= CHAT ================= */

    private void sendMessage() {
        String msg = txtInput.getText().trim();
        if (msg.isEmpty()) return;

        Peer p = tblPeers.getSelectionModel().getSelectedItem();
        if (p == null) {
            alert("Select a peer");
            return;
        }

        txtChat.appendText("[YOU -> " + p.getUsername() + "] " + msg + "\n");
        txtInput.clear();

        new Thread(() -> PeerClient.sendMessage(p, msg)).start();

        ChatWindow cw = openChats.get(p.getId());
        if (cw != null) cw.appendIncoming("YOU", msg);
    }

    private void sendFile(Stage stage) {
        Peer p = tblPeers.getSelectionModel().getSelectedItem();
        if (p == null) return;

        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(stage);
        if (f == null) return;

        new Thread(() -> FileSender.sendFile(p, f, keyManager)).start();

        txtChat.appendText("[YOU -> " + p.getUsername() + "] [file] " + f.getName() + "\n");
    }

    private void openChat(Peer p) {
        openChats
                .computeIfAbsent(p.getId(),
                        k -> new ChatWindow(p, keyManager))
                .show();
    }

    /* ================= VIDEO ================= */

    private void startCall() {
        Peer p = tblPeers.getSelectionModel().getSelectedItem();
        if (p == null || inCall) return;

        PeerClient.sendCallRequest(
                p,
                localVideoPort,
                localAudioPort
        );

        System.out.println("📤 CALL_REQUEST sent to " + p.getUsername());
    }




    private void stopCall() {
        if (!inCall) return;

        Peer p = tblPeers.getSelectionModel().getSelectedItem();
        if (p != null) {
            PeerClient.sendCallEnd(p);
        }

        stopCallInternal();
    }
    public void stopCallFromRemote(Peer peer) {
        stopCallInternal();
        System.out.println("📴 Call ended by " + peer.getUsername());
    }

    private void stopCallInternal() {

        if (videoSender != null) videoSender.stopSend();
        if (videoReceiver != null) videoReceiver.stopReceive();
        if (voiceSender != null) voiceSender.stopSend();
        if (voiceReceiver != null) voiceReceiver.stopReceive();

        Platform.runLater(() -> {
            videoView.setImage(null);
            btnVideoCall.setDisable(false);
            btnEndVideo.setDisable(true);
        });

        inCall = false;
    }






    /* ================= SERVER CALLBACK ================= */

    @Override
    public void onNewConnection(Socket socket) {

        Peer peer = new Peer(
                socket.getInetAddress(),
                socket.getPort(),
                "Unknown",
                socket.getInetAddress().getHostAddress()
        );

        new Thread(new PeerHandler(
                socket,
                peer,
                keyManager,
                settingsStore,
                this
        )).start();
    }


    /* ================= UTILS ================= */

    private void alert(String s) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.INFORMATION, s).showAndWait());
    }
    public void onIncomingMessage(Peer peer, String message) {
        txtChat.appendText(peer.getUsername() + ": " + message + "\n");

        ChatWindow cw = openChats.get(peer.getId());
        if (cw != null) {
            cw.appendIncoming(peer.getUsername(), message);
        }
    }
    public void startCallFromRemote(Peer peer) {

        if (inCall) return;

        try {
            String callKey = "CALL-" + peer.getId();

            videoReceiver = new VideoReceiver(
                    localVideoPort,
                    keyManager,
                    videoView,
                    callKey
            );

            videoReceiver = new VideoReceiver(
                    localVideoPort,
                    keyManager,
                    videoView,
                    callKey
            );

            videoSender = new VideoSender(
                    peer.getAddress(),
                    peer.getVideoPort(),   // 🔥 PORT CỦA PEER
                    keyManager,
                    callKey
            );

            voiceReceiver = new VoiceReceiver(
                    localAudioPort,
                    keyManager,
                    callKey
            );
            voiceSender = new VoiceSender(
                    peer.getAddress(),
                    peer.getAudioPort(),   // 🔥 PORT CỦA PEER
                    keyManager,
                    callKey
            );

            videoSender.start();
            videoReceiver.start();
            voiceSender.start();
            voiceReceiver.start();

            inCall = true;

            btnVideoCall.setDisable(true);
            btnEndVideo.setDisable(false);

            System.out.println("📞 Incoming call from " + peer.getUsername());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onIncomingCall(Peer peer) {

        if (inCall) {
            PeerClient.sendCallEnd(peer);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Incoming Call");
        alert.setHeaderText("📞 " + peer.getUsername());
        alert.setContentText("Accept call?");

        ButtonType accept = new ButtonType("Accept");
        ButtonType reject = new ButtonType("Reject");

        alert.getButtonTypes().setAll(accept, reject);

        alert.showAndWait().ifPresent(btn -> {
            if (btn == accept) {
                PeerClient.sendCallAccept(
                        peer,
                        localVideoPort,
                        localAudioPort
                );
                startCallFromRemote(peer);
            } else {
                PeerClient.sendCallEnd(peer);
            }
        });
    }



}
