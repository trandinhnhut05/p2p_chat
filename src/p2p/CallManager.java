package p2p;

import javafx.application.Platform;
import javafx.scene.image.ImageView;
import p2p.crypto.KeyManager;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class CallManager {

    private final KeyManager keyManager;
    private final Map<String, CallSession> activeCalls = new HashMap<>();
    private PeerClient peerClient;

    public CallManager(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public void setPeerClient(PeerClient peerClient) {
        this.peerClient = peerClient;
    }

    public PeerClient getPeerClient() {
        return peerClient;
    }

    /* ========== Caller tạo outgoing call ========== */
    public void createOutgoingCall(Peer remotePeer, String callId,
                                   int localVideoPort, int localAudioPort,
                                   ImageView localPreview) {

        remotePeer.setVideoPort(localVideoPort);
        remotePeer.setAudioPort(localAudioPort);

        keyManager.getOrCreate(callId);

        CallSession session = new CallSession(remotePeer, callId,
                localVideoPort, localAudioPort, 0, 0,
                keyManager, localPreview);

        activeCalls.put(callId, session);

        session.startReceiving();
    }

    /* ========== Callee nhận incoming call ========== */
    public void onIncomingCall(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort,
                               ImageView remoteView) {

        keyManager.getOrCreate(callId);

        CallSession session = new CallSession(fromPeer, callId,
                0, 0, remoteVideoPort, remoteAudioPort,
                keyManager, remoteView);

        activeCalls.put(callId, session);

        session.startReceiving();
    }

    public void onCallAccepted(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort) {
        CallSession session = activeCalls.get(callId);
        if (session != null) {
            session.setRemotePorts(remoteVideoPort, remoteAudioPort);
            session.startSending();
        }
    }

    public void endCall(String callId) {
        CallSession session = activeCalls.remove(callId);
        if (session != null) session.stop();
    }

    /* ===================== CallSession ===================== */
    private static class CallSession {
        private final Peer remotePeer;
        private final String callId;
        private final KeyManager keyManager;
        private final ImageView videoView;

        private int localVideoPort;
        private int localAudioPort;
        private int remoteVideoPort;
        private int remoteAudioPort;

        private VideoSender videoSender;
        private VideoReceiver videoReceiver;

        public CallSession(Peer remotePeer, String callId,
                           int localVideoPort, int localAudioPort,
                           int remoteVideoPort, int remoteAudioPort,
                           KeyManager keyManager, ImageView videoView) {
            this.remotePeer = remotePeer;
            this.callId = callId;
            this.localVideoPort = localVideoPort;
            this.localAudioPort = localAudioPort;
            this.remoteVideoPort = remoteVideoPort;
            this.remoteAudioPort = remoteAudioPort;
            this.keyManager = keyManager;
            this.videoView = videoView;
        }

        public void setLocalPorts(int videoPort, int audioPort) {
            this.localVideoPort = videoPort;
            this.localAudioPort = audioPort;
        }

        public void setRemotePorts(int videoPort, int audioPort) {
            this.remoteVideoPort = videoPort;
            this.remoteAudioPort = audioPort;
        }

        public void startSending() {
            try {
                if (!OpenCVLoader.init()) {
                    System.err.println("❌ Cannot start sending: OpenCV not loaded");
                    return;
                }
                InetAddress target = remotePeer.getAddress();
                if (videoSender == null && remoteVideoPort > 0) {
                    videoSender = new VideoSender(target, remoteVideoPort, keyManager, callId, videoView);
                    videoSender.start();
                }
                // Tương tự với VoiceSender nếu có
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void startReceiving() {
            try {
                if (!OpenCVLoader.init()) {
                    System.err.println("❌ Cannot start receiving: OpenCV not loaded");
                    return;
                }

                if (videoReceiver == null && localVideoPort > 0) {
                    videoReceiver = new VideoReceiver(localVideoPort, keyManager, videoView, callId);
                    videoReceiver.start();
                }
                // Tương tự với VoiceReceiver nếu có
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void stop() {
            if (videoSender != null) videoSender.stopSend();
            if (videoReceiver != null) videoReceiver.stopReceive();
        }
    }
}
