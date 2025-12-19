package p2p;

import javafx.application.Platform;
import p2p.crypto.KeyManager;

import java.io.*;
import java.net.Socket;

/**
 * FileReceiver
 * -------------
 * Nhận file từ peer qua TCP (AES decrypted bằng KeyManager)
 */
public class FileReceiver implements Runnable {

    private static final int BUFFER_SIZE = 8192;

    private final Socket socket;
    private final Peer peer;
    private final KeyManager keyManager;

    public FileReceiver(Socket socket, Peer peer, KeyManager keyManager) {
        this.socket = socket;
        this.peer = peer;
        this.keyManager = keyManager;
    }

    @Override
    public void run() {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            String fileName = dis.readUTF();
            long fileSize = dis.readLong();

            File dir = new File(System.getProperty("user.home"), "Downloads/p2p-chat");
            if (!dir.exists()) dir.mkdirs();

            File outFile = new File(dir, fileName);

            try (BufferedOutputStream bos =
                         new BufferedOutputStream(new FileOutputStream(outFile))) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = fileSize;

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = dis.read(buffer, 0, toRead);
                    if (read == -1) break;

                    // 🔐 GIẢI MÃ AES
                    byte[] decrypted = keyManager.decrypt(peer.getId(), copyOf(buffer, read));

                    bos.write(decrypted);
                    remaining -= read;
                }
                bos.flush();
            }

            Platform.runLater(() ->
                    System.out.println("Received file: " + outFile.getAbsolutePath())
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] copyOf(byte[] src, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, 0, out, 0, len);
        return out;
    }
}
