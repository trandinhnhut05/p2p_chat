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

        session.startReceiving(); // luôn nhận video/voice trước
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

        session.startReceiving(); // chuẩn bị receiver trước
    }

    /* ========== Khi peer chấp nhận call ========== */
    public void onCallAccepted(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort,
                               int localVideoPort, int localAudioPort,
                               ImageView localPreview) {
        CallSession session = activeCalls.get(callId);
        if (session != null) {
            session.setLocalPorts(localVideoPort, localAudioPort);
            session.setRemotePorts(remoteVideoPort, remoteAudioPort);
            session.setLocalPreview(localPreview);
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
        private ImageView videoView; // dùng cho local preview hoặc remote view

        private int localVideoPort;
        private int localAudioPort;
        private int remoteVideoPort;
        private int remoteAudioPort;

        private VideoSender videoSender;
        private VideoReceiver videoReceiver;
        private VoiceSender voiceSender;
        private VoiceReceiver voiceReceiver;

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

        public void setLocalPreview(ImageView preview) {
            this.videoView = preview;
        }

        /* ================= START / SEND ================= */
        public void startSending() {
            try {
                InetAddress target = remotePeer.getAddress();

                // VideoSender
                if (videoSender == null && remoteVideoPort > 0 && videoView != null) {
                    videoSender = new VideoSender(target, remoteVideoPort, keyManager, callId, videoView);
                    videoSender.start();
                }

                // VoiceSender
                if (voiceSender == null && remoteAudioPort > 0) {
                    voiceSender = new VoiceSender(target, remoteAudioPort, keyManager, callId);
                    voiceSender.start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /* ================= START / RECEIVE ================= */
        public void startReceiving() {
            try {
                // VideoReceiver
                if (videoReceiver == null && localVideoPort > 0 && videoView != null) {
                    videoReceiver = new VideoReceiver(localVideoPort, keyManager, videoView, callId);
                    videoReceiver.start();
                }

                // VoiceReceiver
                if (voiceReceiver == null && localAudioPort > 0) {
                    voiceReceiver = new VoiceReceiver(localAudioPort, keyManager, callId);
                    voiceReceiver.start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /* ================= STOP ================= */
        public void stop() {
            if (videoSender != null) videoSender.stopSend();
            if (videoReceiver != null) videoReceiver.stopReceive();
            if (voiceSender != null) voiceSender.stopSend();
            if (voiceReceiver != null) voiceReceiver.stopReceive();
        }
    }
}
