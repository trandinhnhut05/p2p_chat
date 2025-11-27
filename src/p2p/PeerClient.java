package p2p;

import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class PeerClient {

    public static void sendMessage(Peer peer, String message) {
        try (Socket socket = new Socket(peer.ip, peer.port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("MSG");
            dos.writeUTF(message);
            dos.flush();
        } catch (Exception e) {
            System.err.println("Failed sendMessage to " + peer + " : " + e.getMessage());
        }
    }
}
