package p2p;

import java.io.*;
import java.net.Socket;

public class FileSender {

    public static void sendFile(Peer peer, File file) {
        try (Socket sock = new Socket(peer.ip, peer.port)) {

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(sock.getOutputStream(), "UTF-8")
            );

            // HELLO
            writer.write("/HELLO:" + peer.username + ";" + peer.port + ";" + peer.getFingerprint());
            writer.write("\n");

            // FILE HEADER
            writer.write("/FILE:" + file.getName() + ":" + file.length());
            writer.write("\n");
            writer.flush();

            // FILE DATA (BINARY)
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                OutputStream out = sock.getOutputStream();
                while ((n = fis.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
