package p2p;

import javafx.application.Platform;
import javafx.scene.image.ImageView;

import java.net.InetAddress;
import java.util.Objects;
import java.util.UUID;

/**
 * Peer đại diện cho 1 node trong mạng P2P
 */
public class Peer {

    private final InetAddress address;
    private int servicePort;
    private String username;
    private final String fingerprint;
    private long lastSeen;
    private String lastMessage = "";

    private int videoPort = 6000; // default
    private int audioPort = 7000; // default

    private String callKey;

    public Peer(InetAddress address, int servicePort, String username, String fingerprint) {
        this.address = address;
        this.servicePort = servicePort;
        this.username = username;
        this.fingerprint = fingerprint;
        this.lastSeen = System.currentTimeMillis();
    }

    /* ========== GETTERS / SETTERS ========== */
    public InetAddress getAddress() { return address; }
    public String getIp() { return address.getHostAddress(); }
    public int getServicePort() { return servicePort; }
    public void setServicePort(int servicePort) { this.servicePort = servicePort; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getId() { return fingerprint; }
    public long getLastSeen() { return lastSeen; }
    public void updateLastSeen() { this.lastSeen = System.currentTimeMillis(); }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public int getVideoPort() { return videoPort; }
    public void setVideoPort(int videoPort) { this.videoPort = videoPort; }
    public int getAudioPort() { return audioPort; }
    public void setAudioPort(int audioPort) { this.audioPort = audioPort; }
    public void setCallKey(String callKey) {
        this.callKey = callKey;
    }

    public String getCallKey() {
        return callKey;
    }

    /* ========== OVERRIDE ========== */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;
        Peer peer = (Peer) o;
        return Objects.equals(fingerprint, peer.fingerprint);
    }

    @Override
    public int hashCode() { return Objects.hash(fingerprint); }

    @Override
    public String toString() {
        return username + " (" + address.getHostAddress() + ":" + servicePort + ")";
    }

    /* ========== CALL METHODS ========== */

    /**
     * Gửi request cuộc gọi đến peer khác
     */
    public void sendCallRequest(Peer remotePeer, int localVideoPort, int localAudioPort,
                                CallManager callManager, ImageView localVideoView) {
        String callId = UUID.randomUUID().toString();

        // Tạo session local
        callManager.createOutgoingCall(remotePeer, callId, localVideoPort, localAudioPort, localVideoView);

        // Gửi message CALL_REQUEST
        String msg = "CALL_REQUEST|" + callId + "|" + localVideoPort + "|" + localAudioPort;
        sendMessage(remotePeer, msg);
    }

    /**
     * Gửi phản hồi chấp nhận cuộc gọi
     */
    public void sendCallAccept(Peer remotePeer, String callId, int videoPort, int audioPort) {
        String msg = "CALL_ACCEPT|" + callId + "|" + videoPort + "|" + audioPort;
        sendMessage(remotePeer, msg);
    }

    /**
     * Xử lý message nhận được
     */
    public void onMessageReceived(String msg, Peer fromPeer, ImageView remoteVideoView, CallManager callManager) {
        String[] parts = msg.split("\\|");
        switch (parts[0]) {
            case "CALL_REQUEST":
                String callId = parts[1];
                int remoteVideoPort = Integer.parseInt(parts[2]);
                int remoteAudioPort = Integer.parseInt(parts[3]);

                Platform.runLater(() -> callManager.onIncomingCall(fromPeer, callId,
                        remoteVideoPort, remoteAudioPort, remoteVideoView));
                break;

            case "CALL_ACCEPT":
                callId = parts[1];
                int acceptVideoPort = Integer.parseInt(parts[2]);
                int acceptAudioPort = Integer.parseInt(parts[3]);

                Platform.runLater(() -> callManager.onCallAccepted(fromPeer, callId, acceptVideoPort, acceptAudioPort));
                break;
        }
    }

    /**
     * Dummy method: gửi message P2P
     */
    private void sendMessage(Peer remotePeer, String msg) {
        // TODO: implement gửi message qua socket
        System.out.println("Sending to " + remotePeer.getUsername() + ": " + msg);
    }
}
