package p2p;

import javafx.scene.image.ImageView;
import p2p.crypto.KeyManager;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.AudioSystem;

/**
 * CallSession
 * -----------
 * Quản lý cuộc gọi video + audio 2 chiều
 */
public class CallSession {

    private final Peer peer;
    private final KeyManager keyManager;
    private final String callKey;

    private final int localVideoPort;
    private final int localAudioPort;

    private final int remoteVideoPort;
    private final int remoteAudioPort;

    private final ImageView remoteVideoView;

    private VideoSender videoSender;
    private VideoReceiver videoReceiver;
    private VoiceSender voiceSender;
    private VoiceReceiver voiceReceiver;

    public CallSession(Peer peer,
                       KeyManager keyManager,
                       String callKey,
                       int localVideoPort,
                       int localAudioPort,
                       int remoteVideoPort,
                       int remoteAudioPort,
                       ImageView remoteVideoView) {
        this.peer = peer;
        this.keyManager = keyManager;
        this.callKey = callKey;
        this.localVideoPort = localVideoPort;
        this.localAudioPort = localAudioPort;
        this.remoteVideoPort = remoteVideoPort;
        this.remoteAudioPort = remoteAudioPort;
        this.remoteVideoView = remoteVideoView;
    }

    public void startCall() {

        // VideoReceiver: nhận video từ đối phương
        videoReceiver = new VideoReceiver(localVideoPort, keyManager, remoteVideoView, callKey);
        videoReceiver.start();

        // VideoSender: gửi video tới đối phương
        videoSender = new VideoSender(peer.getAddress(), remoteVideoPort, keyManager, callKey);
        videoSender.start();

        // VoiceReceiver: nhận audio
        voiceReceiver = new VoiceReceiver(localAudioPort, keyManager, callKey);
        voiceReceiver.start();

        // VoiceSender: gửi audio
        voiceSender = new VoiceSender(peer.getAddress(), remoteAudioPort, keyManager, callKey);
        voiceSender.start();
    }

    public void stopCall() {
        if (videoReceiver != null) videoReceiver.stopReceive();
        if (videoSender != null) videoSender.stopSend();
        if (voiceReceiver != null) voiceReceiver.stopReceive();
        if (voiceSender != null) voiceSender.stopSend();
    }
}
