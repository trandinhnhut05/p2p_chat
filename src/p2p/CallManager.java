package p2p;

import javafx.application.Platform;
import javafx.scene.image.ImageView;
import p2p.crypto.KeyManager;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CallManager {

    private final KeyManager keyManager;
    private final Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();

    private PeerClient peerClient;

    public CallManager(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public void setPeerClient(PeerClient peerClient) {
        this.peerClient = peerClient;
    }
    public CallSession getSession(String callId) {
        return activeCalls.get(callId);
    }

    // Tạo cuộc gọi đi (outgoing)
    public void createOutgoingCall(Peer remotePeer, String callId,
                                   int localVideoPort, int localAudioPort,
                                   ImageView localPreview) {
        keyManager.getOrCreate(callId);

        CallSession session = new CallSession(
                remotePeer, callId,
                localVideoPort, localAudioPort,
                0, 0,
                keyManager,
                localPreview,
                null
        );

        activeCalls.put(callId, session);

        // 🔥 Luôn start receiver ngay cho local
        session.startReceiving();
    }

    // Khi nhận cuộc gọi đến (incoming)
    public void onIncomingCall(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort,
                               ImageView remoteView) {
        keyManager.getOrCreate(callId);

        CallSession session = new CallSession(
                fromPeer, callId,
                0, 0,
                remoteVideoPort, remoteAudioPort,
                keyManager,
                null,
                remoteView
        );

        activeCalls.put(callId, session);

        // 🔥 Start receiver ngay
        session.startReceiving();
    }

    // Khi cuộc gọi được chấp nhận
    public void onCallAccepted(Peer peer, String callId,
                               int remoteVideoPort, int remoteAudioPort,
                               int localVideoPort, int localAudioPort,
                               ImageView localPreview, ImageView remoteView) {

        CallSession session = activeCalls.get(callId);
        if (session != null) {
            session.setLocalPorts(localVideoPort, localAudioPort);
            session.setRemotePorts(remoteVideoPort, remoteAudioPort);
            session.setLocalPreview(localPreview);
            session.setRemoteView(remoteView);

            // Start receiver (nếu chưa)
            session.startReceiving();

            // 🔥 Start sending ngay
            new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                session.startSending();
            }).start();
        }
    }


    // Kết thúc cuộc gọi
    public void endCall(String callId) {
        CallSession session = activeCalls.remove(callId);
        if (session != null) session.stop();
    }

    // ------------------- CallSession -------------------
    public static class CallSession {
        private final Peer remotePeer;
        private final String callId;
        private final KeyManager keyManager;

        private int localVideoPort, localAudioPort;
        private int remoteVideoPort, remoteAudioPort;

        private ImageView localPreview, remoteView;
        private boolean micEnabled = true, videoEnabled = true;

        private VideoSender videoSender;
        private VideoReceiver videoReceiver;
        private VoiceSender voiceSender;
        private VoiceReceiver voiceReceiver;

        public CallSession(Peer remotePeer, String callId,
                           int localVideoPort, int localAudioPort,
                           int remoteVideoPort, int remoteAudioPort,
                           KeyManager keyManager,
                           ImageView localPreview, ImageView remoteView) {
            this.remotePeer = remotePeer;
            this.callId = callId;
            this.localVideoPort = localVideoPort;
            this.localAudioPort = localAudioPort;
            this.remoteVideoPort = remoteVideoPort;
            this.remoteAudioPort = remoteAudioPort;
            this.keyManager = keyManager;
            this.localPreview = localPreview;
            this.remoteView = remoteView;
        }

        public void setLocalPorts(int videoPort, int audioPort) {
            this.localVideoPort = videoPort;
            this.localAudioPort = audioPort;
        }

        public void setRemotePorts(int videoPort, int audioPort) {
            this.remoteVideoPort = videoPort;
            this.remoteAudioPort = audioPort;
        }

        public void setLocalPreview(ImageView preview) { this.localPreview = preview; }
        public void setRemoteView(ImageView remoteView) { this.remoteView = remoteView; }

        public void startReceiving() {
            try {
                if (videoReceiver == null && localVideoPort > 0 && remoteView != null) {
                    Platform.runLater(() -> {
                        remoteView.setImage(null);
                        remoteView.setVisible(true);
                    });
                    videoReceiver = new VideoReceiver(localVideoPort, keyManager, remoteView, callId);
                    videoReceiver.start();
                }

                if (voiceReceiver == null && localAudioPort > 0) {
                    voiceReceiver = new VoiceReceiver(localAudioPort, keyManager, callId);
                    voiceReceiver.start();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        public void startSending() {
            try {
                InetAddress target = remotePeer.getAddress();

                if (videoSender == null && remoteVideoPort > 0 && localPreview != null && videoEnabled) {
                    videoSender = new VideoSender(target, remoteVideoPort, keyManager, callId, localPreview);
                    videoSender.start();
                }

                if (voiceSender == null && remoteAudioPort > 0) {
                    voiceSender = new VoiceSender(target, remoteAudioPort, keyManager, callId);
                    voiceSender.setEnabled(micEnabled);
                    voiceSender.start();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        public void stop() {
            if (videoSender != null) videoSender.stopSend();
            if (videoReceiver != null) videoReceiver.stopReceive();
            if (voiceSender != null) voiceSender.stopSend();
            if (voiceReceiver != null) voiceReceiver.stopReceive();
        }
    }
}
