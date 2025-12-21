package p2p;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import p2p.crypto.KeyManager;

import java.io.File;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class MainUI extends Application implements PeerServer.ConnectionListener {

    private KeyManager keyManager;
    private PeerClient peerClient;

    private SettingsStore settingsStore;

    private ImageView videoViewLocal;  // self-preview
    private ImageView videoViewRemote;

    /* ================= CHAT ================= */
    private TableView<Peer> tblPeers;
    private TextArea txtChat;
    private TextField txtInput;
    private final Map<String, ChatWindow> openChats = new ConcurrentHashMap<>();

    /* ================= VIDEO & VOICE ================= */
    private VoiceSender voiceSender;
    private VoiceReceiver voiceReceiver;
    private VideoSender videoSender;
    private VideoReceiver videoReceiver;
    private ImageView videoView;

    private boolean inCall = false;
    private Peer currentCallPeer = null;
    private String currentCallKey = null;
    private String currentCallId = null;

    private Button btnVideoCall, btnEndVideo;

    private CallManager callManager;

    /* ================= NETWORK ================= */
    private PeerDiscoverySender discoverySender;
    private PeerDiscoveryListener discoveryListener;
    private PeerServer peerServer;

    private final ObservableList<Peer> peerList = FXCollections.observableArrayList();
    private FilteredList<Peer> filteredPeers;

    private ScheduledExecutorService uiRefresher;

    private String username = "User" + (int)(Math.random() * 1000);
    private int servicePort = 60000;
    private int discoveryPort = 9999;
    private String localIP;

    private int localVideoPort;   // port gửi/nhận video local
    private int localAudioPort;   // port gửi/nhận voice local
    private int remoteVideoPort;
    private int remoteAudioPort;


    @Override
    public void start(Stage stage) throws Exception {
        localIP = InetAddress.getLocalHost().getHostAddress();
        keyManager = new KeyManager();
        callManager = new CallManager(keyManager);
        settingsStore = new SettingsStore();

        // ========== UI ========== //
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

        tblPeers = new TableView<>();
        filteredPeers = new FilteredList<>(peerList, p -> true);
        tblPeers.setItems(filteredPeers);

        TableColumn<Peer, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getUsername()));
        TableColumn<Peer, String> colIp = new TableColumn<>("IP");
        colIp.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getIp()));
        TableColumn<Peer, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> {
            boolean online = System.currentTimeMillis() - c.getValue().getLastSeen()
                    < PeerDiscoveryListener.PEER_TIMEOUT_MS;
            return new ReadOnlyStringWrapper(online ? "ONLINE" : "OFFLINE");
        });

        tblPeers.getColumns().addAll(colName, colIp, colStatus);
        VBox left = new VBox(6, new Label("Peers"), tblPeers);
        left.setPadding(new Insets(8));

        // ================= CHAT & VIDEO =================
        txtChat = new TextArea();
        txtChat.setEditable(false);
        txtInput = new TextField();
        Button btnSend = new Button("Send");
        Button btnFile = new Button("Send File");

        btnVideoCall = new Button("Video Call");
        btnEndVideo = new Button("End Video");
        btnEndVideo.setDisable(true);

// Video views
        videoViewLocal = new ImageView();
        videoViewLocal.setFitWidth(160);
        videoViewLocal.setFitHeight(120);
        videoViewLocal.setPreserveRatio(true);

        videoViewRemote = new ImageView();
        videoViewRemote.setFitWidth(320);
        videoViewRemote.setFitHeight(240);
        videoViewRemote.setPreserveRatio(true);


        HBox videoBox = new HBox(8, videoViewRemote, videoViewLocal);
        videoBox.setPadding(new Insets(8));

        HBox sendBox = new HBox(8, txtInput, btnSend, btnFile, btnVideoCall, btnEndVideo);
        VBox right = new VBox(8, txtChat, videoBox, sendBox);
        right.setPadding(new Insets(8));


        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setLeft(left);
        root.setCenter(right);

        stage.setScene(new Scene(root, 1200, 700));
        stage.setTitle("P2P Chat – Synced");
        stage.show();

        // ========== EVENTS ========== //
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
        });

        btnSend.setOnAction(e -> sendMessage());
        txtInput.setOnAction(e -> sendMessage());
        btnFile.setOnAction(e -> sendFile(stage));
        btnVideoCall.setOnAction(e -> startCall());
        btnEndVideo.setOnAction(e -> stopCall());

        tblPeers.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                Peer p = tblPeers.getSelectionModel().getSelectedItem();
                if (p != null) openChat(p);
            }
        });


        stage.setOnCloseRequest(e -> stopNetwork());
    }

    /* ================= NETWORK ================= */
    private void startNetwork() {
        String localPeerId = username + "@" + localIP;
        peerClient = new PeerClient(keyManager, localPeerId, servicePort, username);

        discoveryListener = new PeerDiscoveryListener(servicePort, discoveryPort);
        discoveryListener.start();
        discoverySender = new PeerDiscoverySender(username, servicePort, discoveryPort);
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
        if (peerClient == null) {
            alert("Please start network first");
            return;
        }
        if (p == null) {
            alert("Select a peer");
            return;
        }

        txtChat.appendText("[YOU -> " + p.getUsername() + "] " + msg + "\n");
        txtInput.clear();
        new Thread(() -> peerClient.sendMessage(p, msg)).start();

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
        openChats.computeIfAbsent(p.getId(),
                        k -> new ChatWindow(p, keyManager, peerClient))
                .show();
    }

    /* ================= CALL ================= */
    /* ================= CALL ================= */
    private void startCall() {
        Peer peer = tblPeers.getSelectionModel().getSelectedItem();
        currentCallKey = UUID.randomUUID().toString();

        // ==== 1 máy: self-preview ====
        if (peer == null) {
            System.out.println("🎥 Self preview mode");

            videoReceiver = new VideoReceiver(-1, keyManager, videoViewLocal, currentCallKey);
            videoReceiver.start();

            voiceReceiver = null;
            videoSender = null;
            voiceSender = null;

            inCall = true;
            Platform.runLater(() -> {
                btnVideoCall.setDisable(true);
                btnEndVideo.setDisable(false);
            });
            return;
        }

        // ==== 2 máy: peer-to-peer ====
        if (inCall) {
            alert("Already in a call");
            return;
        }

        currentCallPeer = peer;

        // Ports local và remote
        int localVideoSendPort = getFreePort();   // gửi video của bạn
        int localAudioSendPort = getFreePort();   // gửi voice của bạn
        int localVideoRecvPort = getFreePort();   // nhận video peer
        int localAudioRecvPort = getFreePort();   // nhận voice peer

        peer.setVideoPort(localVideoRecvPort);
        peer.setAudioPort(localAudioRecvPort);

        // Gửi CALL_REQUEST tới peer
        new Thread(() -> peerClient.sendCallRequest(
                peer,
                localVideoSendPort,
                localAudioSendPort,
                localVideoRecvPort,
                localAudioRecvPort,
                currentCallKey
        )).start();

        // ==== Receiver cho remote video + voice ====
        VideoReceiver selfPreview = new VideoReceiver(-1, keyManager, videoViewLocal, currentCallKey);
        selfPreview.start();

        videoReceiver = new VideoReceiver(localVideoRecvPort, keyManager, videoViewRemote, currentCallKey);
        videoReceiver.start();


        voiceReceiver = new VoiceReceiver(localAudioRecvPort, keyManager, currentCallKey);
        voiceReceiver.start();

        // ==== Sender gửi video + voice self tới peer ====
        videoSender = new VideoSender(peer.getAddress(), localVideoSendPort, keyManager, currentCallKey);
        videoSender.start();

        voiceSender = new VoiceSender(peer.getAddress(), localAudioSendPort, keyManager, currentCallKey);
        voiceSender.start();

        // ==== Self-preview luôn bật ====
//        VideoReceiver selfPreview = new VideoReceiver(-1, keyManager, videoViewLocal, currentCallKey);
//        selfPreview.start();

        inCall = true;
        Platform.runLater(() -> {
            btnVideoCall.setDisable(true);
            btnEndVideo.setDisable(false);
        });

        System.out.println("📤 CALL_REQUEST sent to " + peer.getUsername());
    }


    public void onIncomingCall(Peer peer, String callKey, int remoteVideoPort, int remoteAudioPort) {
        if (inCall) {
            peerClient.sendCallEnd(peer);
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
                currentCallPeer = peer;
                currentCallKey = callKey;
                peer.setCallKey(callKey);

                int localVideoPort = getFreePort();
                int localAudioPort = getFreePort();

                peerClient.sendCallAccept(peer, localVideoPort, localAudioPort);

                // Remote video -> videoViewRemote
                videoReceiver = new VideoReceiver(remoteVideoPort, keyManager, videoViewRemote, currentCallKey);
                videoReceiver.start();

                voiceReceiver = new VoiceReceiver(remoteAudioPort, keyManager, currentCallKey);
                voiceReceiver.start();

                // Sender (gửi video + voice của bạn) -> peer
                videoSender = new VideoSender(peer.getAddress(), localVideoPort, keyManager, currentCallKey);
                videoSender.start();

                voiceSender = new VoiceSender(peer.getAddress(), localAudioPort, keyManager, currentCallKey);
                voiceSender.start();

                inCall = true;
                Platform.runLater(() -> {
                    btnVideoCall.setDisable(true);
                    btnEndVideo.setDisable(false);
                });

                System.out.println("📞 Call accepted from " + peer.getUsername());
            } else {
                peerClient.sendCallEnd(peer);
            }
        });
    }


    public void onCallAccepted(Peer peer, int remoteVideoPort, int remoteAudioPort) {
        if (!inCall || peer != currentCallPeer) return;

        // Receiver
        voiceReceiver = new VoiceReceiver(remoteAudioPort, keyManager, currentCallKey);
        voiceReceiver.start();

        videoReceiver = new VideoReceiver(remoteVideoPort, keyManager, videoViewRemote, currentCallKey);
        videoReceiver.start();

        // Sender
        voiceSender = new VoiceSender(peer.getAddress(), remoteAudioPort, keyManager, currentCallKey);
        voiceSender.start();

        videoSender = new VideoSender(peer.getAddress(), remoteVideoPort, keyManager, currentCallKey);
        videoSender.start();

        System.out.println("📞 Call started with " + peer.getUsername());
    }





    public void stopCall() {
        if (!inCall) return;
        if (currentCallPeer != null) peerClient.sendCallEnd(currentCallPeer);
        stopCallInternal();
    }

    private void stopCallInternal() {
        try {
            if (voiceSender != null) { voiceSender.stopSend(); voiceSender = null; }
            if (voiceReceiver != null) { voiceReceiver.stopReceive(); voiceReceiver = null; }
            if (videoSender != null) { videoSender.stopSend(); videoSender = null; }
            if (videoReceiver != null) { videoReceiver.stopReceive(); videoReceiver = null; }
        } catch (Exception e) { e.printStackTrace(); }

        Platform.runLater(() -> {
            videoViewLocal.setImage(null);
            videoViewRemote.setImage(null);
            btnVideoCall.setDisable(false);
            btnEndVideo.setDisable(true);
        });

        currentCallPeer = null;
        currentCallKey = null;
        currentCallId = null;
        inCall = false;
    }


    public void startCallFromRemote(Peer peer, int remoteVideoPort, int remoteAudioPort) {
        if (inCall) return;

        currentCallPeer = peer;
        currentCallKey = peer.getCallKey();
        currentCallId = UUID.randomUUID().toString();

        // Receiver
        voiceReceiver = new VoiceReceiver(remoteAudioPort, keyManager, currentCallKey);
        voiceReceiver.start();
        videoReceiver = new VideoReceiver(
                remoteVideoPort,   // cổng nhận video từ peer
                keyManager,        // KeyManager để giải mã video
                videoViewRemote,         // ImageView hiển thị
                currentCallKey     // callKey dùng chung giữa 2 bên
        );

        videoReceiver.start();

        // Sender
        voiceSender = new VoiceSender(peer.getAddress(), remoteAudioPort, keyManager, currentCallKey);
        voiceSender.start();
        videoSender = new VideoSender(
                peer.getAddress(),  // địa chỉ IP của peer
                remoteVideoPort,    // cổng nhận video
                keyManager,         // KeyManager để mã hóa video
                currentCallKey      // callKey dùng chung giữa 2 bên
        );

        videoSender.start();

        inCall = true;
        btnVideoCall.setDisable(true);
        btnEndVideo.setDisable(false);
    }

    /* ================= SERVER CALLBACK ================= */
    @Override
    public void onNewConnection(Socket socket) {
        Peer peer = new Peer(socket.getInetAddress(), -1, "Unknown", socket.getInetAddress().getHostAddress());
        new Thread(new PeerHandler(socket, peer, keyManager, settingsStore, this, callManager)).start();
    }

    public void onIncomingMessage(Peer peer, String message) {
        txtChat.appendText(peer.getUsername() + ": " + message + "\n");
        ChatWindow cw = openChats.get(peer.getId());
        if (cw != null) cw.appendIncoming(peer.getUsername(), message);
    }

//    public void onIncomingCall(Peer peer, String callId, int peerVideoPort, int peerAudioPort) {
//        if (inCall) {
//            // Nếu bận, từ chối
//            peerClient.sendCallEnd(peer);
//            return;
//        }
//
//        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
//        alert.setTitle("Incoming Call");
//        alert.setHeaderText("📞 " + peer.getUsername());
//        alert.setContentText("Accept call?");
//        ButtonType accept = new ButtonType("Accept");
//        ButtonType reject = new ButtonType("Reject");
//        alert.getButtonTypes().setAll(accept, reject);
//
//        alert.showAndWait().ifPresent(btn -> {
//            if (btn == accept) {
//                currentCallPeer = peer;
//                currentCallKey = callId;
//                peer.setCallKey(callId);
//
//                // Tạo các port local trống để gửi video/voice
//                remoteVideoPort = peerVideoPort; // peer muốn nhận video ở port này
//                remoteAudioPort = peerAudioPort; // peer muốn nhận voice ở port này
//                localVideoPort = getFreePort();  // nhận video từ peer
//                localAudioPort = getFreePort();  // nhận voice từ peer
//
//                // Receiver luôn sẵn sàng trước
//                voiceReceiver = new VoiceReceiver(localAudioPort, keyManager, currentCallKey);
//                voiceReceiver.start();
//
//                videoReceiver = new VideoReceiver(localVideoPort, keyManager, videoView, currentCallKey);
//                videoReceiver.start();
//
//                // Gửi CALL_ACCEPT tới peer kèm port mình nhận
//                peerClient.sendCallAccept(peer, localVideoPort, localAudioPort);
//
//                // Sender gửi đến peer
//                voiceSender = new VoiceSender(peer.getAddress(), remoteAudioPort, keyManager, currentCallKey);
//                voiceSender.start();
//
//                videoSender = new VideoSender(peer.getAddress(), remoteVideoPort, keyManager, currentCallKey);
//                videoSender.start();
//
//                inCall = true;
//                Platform.runLater(() -> {
//                    btnVideoCall.setDisable(true);
//                    btnEndVideo.setDisable(false);
//                });
//
//                System.out.println("📞 Call accepted from " + peer.getUsername());
//            } else {
//                peerClient.sendCallEnd(peer);
//            }
//        });
//    }

//    public void onCallAccepted(Peer peer, int peerVideoPort, int peerAudioPort) {
//        if (!inCall || peer != currentCallPeer) return;
//
//        // Peer gửi port mà họ muốn nhận
//        remoteVideoPort = peerVideoPort;
//        remoteAudioPort = peerAudioPort;
//
//        // Receiver luôn sẵn sàng (nếu chưa tạo)
//        if (voiceReceiver == null) {
//            localAudioPort = getFreePort();
//            voiceReceiver = new VoiceReceiver(localAudioPort, keyManager, currentCallKey);
//            voiceReceiver.start();
//        }
//
//        if (videoReceiver == null) {
//            localVideoPort = getFreePort();
//            videoReceiver = new VideoReceiver(localVideoPort, keyManager, videoView, currentCallKey);
//            videoReceiver.start();
//        }
//
//        // Sender gửi dữ liệu đến peer
//        if (voiceSender == null) {
//            voiceSender = new VoiceSender(peer.getAddress(), remoteAudioPort, keyManager, currentCallKey);
//            voiceSender.start();
//        }
//
//        if (videoSender == null) {
//            videoSender = new VideoSender(peer.getAddress(), remoteVideoPort, keyManager, currentCallKey);
//            videoSender.start();
//        }
//
//        inCall = true;
//        Platform.runLater(() -> {
//            btnVideoCall.setDisable(true);
//            btnEndVideo.setDisable(false);
//        });
//
//        System.out.println("📞 Call started with " + peer.getUsername());
//    }


    public void onCallEnded(Peer peer) {
        if (peer != currentCallPeer) return;

        System.out.println("📴 Call ended by " + peer.getUsername());
        stopCallInternal();
    }

    public void stopCallFromRemote(Peer peer) {
        if (inCall && currentCallPeer != null && currentCallPeer.equals(peer)) {
            System.out.println("📴 Call ended by remote: " + peer.getUsername());
            stopCallInternal();
        }
    }





    /* ================= UTILS ================= */
    private int getFreePort() {
        try (DatagramSocket ds = new DatagramSocket(0)) { return ds.getLocalPort(); }
        catch (Exception e) { e.printStackTrace(); return -1; }
    }

    private void alert(String s) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, s).showAndWait()); }
}
