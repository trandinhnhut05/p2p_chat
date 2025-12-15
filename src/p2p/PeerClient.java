package p2p;

import java.net.Socket;
public class PeerClient {

    public static void sendMessage(Peer peer, String msg) {
        try (Socket sock = new Socket(peer.ip, peer.port)) {

            SettingsStore ss = new SettingsStore();

            PeerHandler handler = new PeerHandler(sock, null);

            // ✅ HELLO = thông tin của MÌNH
            handler.sendLine("/HELLO:" +
                    ss.getAccountUsername() + ";" +
                    sock.getLocalPort() + ";" +
                    ss.getAccountFingerprint()
            );

            handler.sendMessage(msg);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendPing(Peer peer, long timestamp) {
        try (Socket sock = new Socket(peer.ip, peer.port)) {

            SettingsStore ss = new SettingsStore();
            PeerHandler handler = new PeerHandler(sock, null);

            handler.sendLine("/HELLO:" +
                    ss.getAccountUsername() + ";" +
                    sock.getLocalPort() + ";" +
                    ss.getAccountFingerprint()
            );

            handler.sendPing(timestamp);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

