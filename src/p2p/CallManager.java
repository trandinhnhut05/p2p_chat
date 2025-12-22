package p2p;

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

    // Tạo cuộc gọi đi (outgoing)
    public void createOutgoingCall(Peer remotePeer, String callId,
                                   int localVideoPort, int localAudioPort,
                                   ImageView localPreview) {

        keyManager.getOrCreate(callId);

        CallSession session = new CallSession(
                remotePeer,
                callId,
                localVideoPort,
                localAudioPort,
                0, 0,
                keyManager,
                localPreview,
                null
        );

        activeCalls.put(callId, session);

    }

    // Khi nhận cuộc gọi đến (incoming)
    public void onIncomingCall(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort,
                               ImageView remoteView) {

        keyManager.getOrCreate(callId);

        CallSession session = new CallSession(
                fromPeer,
                callId,
                0, 0,
                remoteVideoPort,
                remoteAudioPort,
                keyManager,
                null,
                remoteView
        );

        activeCalls.put(callId, session);
        session.startReceiving();
    }

    // Khi cuộc gọi được chấp nhận
    public void onCallAccepted(Peer fromPeer, String callId,
                               int remoteVideoPort, int remoteAudioPort,
                               int localVideoPort, int localAudioPort,
                               ImageView localPreview, ImageView remoteView) {

        CallSession session = activeCalls.get(callId);
        if (session != null) {
            session.setLocalPorts(localVideoPort, localAudioPort);
            session.setRemotePorts(remoteVideoPort, remoteAudioPort);
            session.setLocalPreview(localPreview);
            session.setRemoteView(remoteView);
            session.startSending();
            session.startReceiving();
        }
    }

    // Kết thúc cuộc gọi
    public void endCall(String callId) {
        CallSession session = activeCalls.remove(callId);
        if (session != null) session.stop();
    }

    // ------------------- CallSession -------------------
    private static class CallSession {
        private final Peer remotePeer;
        private final String callId;
        private final KeyManager keyManager;

        private int localVideoPort;
        private int localAudioPort;
        private int remoteVideoPort;
        private int remoteAudioPort;

        private ImageView localPreview;
        private ImageView remoteView;

        private boolean micEnabled = true;
        private boolean videoEnabled = true; // <-- trạng thái video

        private VideoSender videoSender;
        private VideoReceiver videoReceiver;
        private VoiceSender voiceSender;
        private VoiceReceiver voiceReceiver;


        public CallSession(Peer remotePeer, String callId,
                           int localVideoPort, int localAudioPort,
                           int remoteVideoPort, int remoteAudioPort,
                           KeyManager keyManager,
                           ImageView localPreview,
                           ImageView remoteView) {

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

        // Bật/tắt mic
        public void toggleMic() {
            micEnabled = !micEnabled;
            if (voiceSender != null) voiceSender.setEnabled(micEnabled);
        }

        // Bật/tắt video
        public void toggleVideo() {
            videoEnabled = !videoEnabled;
            if (videoSender != null) {
                if (videoEnabled) {
                    videoSender.start(); // bật lại gửi video
                } else {
                    videoSender.stopSend(); // tắt gửi video
                }
            }
            if (localPreview != null) {
                localPreview.setVisible(videoEnabled); // ẩn/hiện preview
            }
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
            this.localPreview = preview;
        }

        public void setRemoteView(ImageView remoteView) {
            this.remoteView = remoteView;
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void startReceiving() {

            try {

                if (videoReceiver == null && localVideoPort > 0) {
                    videoReceiver = new VideoReceiver(
                            localVideoPort,
                            keyManager,
                            remoteView,
                            callId
                    );
                    videoReceiver.start();

                    System.out.println("✅ startReceiving() called");
                }

                if (voiceReceiver == null && localAudioPort > 0) {
                    voiceReceiver = new VoiceReceiver(localAudioPort, keyManager, callId);
                    voiceReceiver.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void stop() {
            if (videoSender != null) videoSender.stopSend();
            if (videoReceiver != null) videoReceiver.stopReceive();
            if (voiceSender != null) voiceSender.stopSend();
            if (voiceReceiver != null) voiceReceiver.stopReceive();
        }
    }

}
