package p2p;

import javafx.application.Platform;
import javafx.scene.image.ImageView;
import p2p.crypto.KeyManager;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Quản lý các session call P2P video/audio
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

    /**
     * Tạo session outgoing call
     */
    public void createOutgoingCall(Peer remotePeer, String callId,
                                   int localVideoPort, int localAudioPort,
                                   ImageView localVideoView) {

        remotePeer.setVideoPort(localVideoPort);
        remotePeer.setAudioPort(localAudioPort);

        CallSession session = new CallSession(remotePeer, callId,
                localVideoPort, localAudioPort, 0, 0,
                keyManager, localVideoView);

        activeCalls.put(callId, session);

        // Receiver luôn start trước để nhận UDP
        session.startReceiving();
    }


    public void onIncomingCall(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort,
                               ImageView remoteVideoView) {

        CallSession session = new CallSession(fromPeer, callId,
                fromPeer.getVideoPort(), fromPeer.getAudioPort(),
                remoteVideoPort, remoteAudioPort,
                keyManager, remoteVideoView);

        activeCalls.put(callId, session);

        // Receiver luôn start trước
        session.startReceiving();
        // Sender sẽ start sau khi gửi CALL_ACCEPT (được gọi ở MainUI.onIncomingCall)
    }



    public void onCallAccepted(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort) {
        CallSession session = activeCalls.get(callId);
        if (session != null) {
            session.setRemotePorts(remoteVideoPort, remoteAudioPort);
            // Sender + Receiver
            session.startSending();
            session.startReceiving();
        }
    }


    public void endCall(String callId) {
        CallSession session = activeCalls.remove(callId);
        if (session != null) session.stop();
    }

    private static class CallSession {
        private final Peer remotePeer;
        private final String callId;
        private final int localVideoPort;
        private final int localAudioPort;
        private int remoteVideoPort;
        private int remoteAudioPort;
        private final KeyManager keyManager;
        private final ImageView videoView;

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

        public void setRemotePorts(int videoPort, int audioPort) {
            this.remoteVideoPort = videoPort;
            this.remoteAudioPort = audioPort;
        }

        public void startSending() {
            try {
                InetAddress target = remotePeer.getAddress();
                if (videoSender == null && remoteVideoPort > 0) {
                    System.out.println("🎥 Starting VideoSender to " + target + ":" + remoteVideoPort);
                    videoSender = new VideoSender(target, remoteVideoPort, keyManager, callId, videoView);
                    videoSender.start();
                }
                if (voiceSender == null && remoteAudioPort > 0) {
                    System.out.println("🎤 Starting VoiceSender to " + target + ":" + remoteAudioPort);
                    voiceSender = new VoiceSender(target, remoteAudioPort, keyManager, callId);
                    voiceSender.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        public void startReceiving() {
            try {
                if (videoReceiver == null && localVideoPort > 0) {
                    System.out.println("🎥 Starting VideoReceiver on port " + localVideoPort);
                    videoReceiver = new VideoReceiver(localVideoPort, keyManager, videoView, callId);
                    videoReceiver.start();
                }
                if (voiceReceiver == null && localAudioPort > 0) {
                    System.out.println("🎤 Starting VoiceReceiver on port " + localAudioPort);
                    voiceReceiver = new VoiceReceiver(localAudioPort, keyManager, callId);
                    voiceReceiver.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        public void stop() {
            if (videoSender != null) videoSender.stopSend();
            if (voiceSender != null) voiceSender.stopSend();
            if (videoReceiver != null) videoReceiver.stopReceive();
            if (voiceReceiver != null) voiceReceiver.stopReceive();
        }
    }
}
