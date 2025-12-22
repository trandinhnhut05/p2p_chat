package p2p;

import javafx.scene.image.ImageView;
import p2p.crypto.KeyManager;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Quản lý các session call P2P video/audio
 * Hỗ trợ 2 chiều: gửi và nhận video/voice đồng thời
 */
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

    /** Caller tạo session outgoing call */
    public void createOutgoingCall(Peer remotePeer, String callId,
                                   int localVideoPort, int localAudioPort,
                                   ImageView localVideoView) {

        keyManager.getOrCreate(callId);

        CallSession session = new CallSession(remotePeer, callId,
                localVideoPort, localAudioPort,
                0, 0, keyManager, localVideoView);

        activeCalls.put(callId, session);

        // Luôn start receiver trước sender
        session.startReceiving();
    }

    /** Callee nhận incoming call */
    public void onIncomingCall(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort,
                               ImageView remoteVideoView) {

        keyManager.getOrCreate(callId);

        CallSession session = new CallSession(fromPeer, callId,
                0, 0, remoteVideoPort, remoteAudioPort,
                keyManager, remoteVideoView);

        activeCalls.put(callId, session);

        // Chỉ start receiver trước sender
        session.startReceiving();
    }

    /** Khi peer gửi CALL_ACCEPT */
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

    /** ==================== CallSession ==================== */
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

        /** Start sender để gửi dữ liệu tới remote peer */
        public void startSending() {
            try {
                InetAddress target = remotePeer.getAddress();
                if (videoSender == null && remoteVideoPort > 0) {
                    videoSender = new VideoSender(target, remoteVideoPort, keyManager, callId, videoView);
                    videoSender.start();
                }
                if (voiceSender == null && remoteAudioPort > 0) {
                    voiceSender = new VoiceSender(target, remoteAudioPort, keyManager, callId);
                    voiceSender.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /** Start receiver để nhận dữ liệu từ remote peer */
        public void startReceiving() {
            try {
                int tries = 0;
                while (!keyManager.hasKey(callId) && tries < 20) {
                    Thread.sleep(50);
                    tries++;
                }
                if (!keyManager.hasKey(callId)) {
                    System.err.println("❌ Cannot start receiver, missing key: " + callId);
                    return;
                }

                if (videoReceiver == null && localVideoPort > 0) {
                    videoReceiver = new VideoReceiver(localVideoPort, keyManager, videoView, callId);
                    videoReceiver.start();
                }
                if (voiceReceiver == null && localAudioPort > 0) {
                    voiceReceiver = new VoiceReceiver(localAudioPort, keyManager, callId);
                    voiceReceiver.start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /** Stop tất cả sender + receiver */
        public void stop() {
            if (videoSender != null) { videoSender.stopSend(); videoSender = null; }
            if (voiceSender != null) { voiceSender.stopSend(); voiceSender = null; }
            if (videoReceiver != null) { videoReceiver.stopReceive(); videoReceiver = null; }
            if (voiceReceiver != null) { voiceReceiver.stopReceive(); voiceReceiver = null; }
        }
    }
}
