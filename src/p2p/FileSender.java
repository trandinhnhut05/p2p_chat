package p2p;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;

public class FileSender {
    public static void sendFile(Peer peer, File file) {
        try (Socket socket = new Socket(peer.ip, peer.port);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            dos.writeUTF("FILE");
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
        } catch (Exception e) {
            System.err.println("Failed sendFile to " + peer + ": " + e.getMessage());
        }
    }
}
