package p2p;

import p2p.crypto.KeyManager;

import java.net.InetAddress;

/**
 * Quản lý cuộc gọi Video / Voice (E2EE aware)
 */
public class CallManager {

    private final KeyManager keyManager;

    private VideoSender videoSender;
    private VideoReceiver videoReceiver;

    private VoiceSender voiceSender;
    private VoiceReceiver voiceReceiver;

    public CallManager(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    /* ===================== VIDEO ===================== */

    public synchronized void startVideoCall(Peer peer,
                                            int localVideoPort,
                                            int remoteVideoPort,
                                            javafx.scene.image.ImageView imageView)
            throws Exception {

        String peerId = peer.getIp() + ":" + peer.getPort();

        // 🔐 đảm bảo đã có AES key
        if (keyManager.getSessionKey(peerId) == null) {
            System.out.println("🔐 Creating AES key before video call...");
            keyManager.createAndSendSessionKey(peerId);
        }

        stopVideoCall();

        videoReceiver = new VideoReceiver(localVideoPort, keyManager, imageView);
        videoSender = new VideoSender(
                InetAddress.getByName(peer.getIp()),
                remoteVideoPort,
                keyManager
        );

        videoReceiver.start();
        videoSender.start();

        System.out.println("📹 Video call started with " + peerId);
    }

    public synchronized void stopVideoCall() {
        if (videoSender != null) {
            videoSender.stopSend();
            videoSender = null;
        }
        if (videoReceiver != null) {
            videoReceiver.stopReceive();
            videoReceiver = null;
        }
        System.out.println("📹 Video call stopped");
    }

    /* ===================== VOICE ===================== */

    public synchronized void startVoiceCall(Peer peer,
                                            int localVoicePort,
                                            int remoteVoicePort)
            throws Exception {

        String peerId = peer.getIp() + ":" + peer.getPort();

        if (keyManager.getSessionKey(peerId) == null) {
            System.out.println("🔐 Creating AES key before voice call...");
            keyManager.createAndSendSessionKey(peerId);
        }

        stopVoiceCall();

        voiceReceiver = new VoiceReceiver(localVoicePort, keyManager);
        voiceSender = new VoiceSender(
                InetAddress.getByName(peer.getIp()),
                remoteVoicePort,
                keyManager
        );

        voiceReceiver.start();
        voiceSender.start();

        System.out.println("🎤 Voice call started with " + peerId);
    }

    public synchronized void stopVoiceCall() {
        if (voiceSender != null) {
            voiceSender.stopSend();
            voiceSender = null;
        }
        if (voiceReceiver != null) {
            voiceReceiver.stopReceive();
            voiceReceiver = null;
        }
        System.out.println("🎤 Voice call stopped");
    }

    /* ===================== FULL CALL ===================== */

    public synchronized void startFullCall(Peer peer,
                                           int localVideoPort,
                                           int remoteVideoPort,
                                           int localVoicePort,
                                           int remoteVoicePort,
                                           javafx.scene.image.ImageView imageView)
            throws Exception {

        startVideoCall(peer, localVideoPort, remoteVideoPort, imageView);
        startVoiceCall(peer, localVoicePort, remoteVoicePort);
    }

    public synchronized void stopAll() {
        stopVideoCall();
        stopVoiceCall();
    }
}
