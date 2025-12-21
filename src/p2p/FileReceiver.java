package p2p;

import javafx.application.Platform;
import p2p.crypto.KeyManager;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.net.Socket;

/**
 * FileReceiver
 * -------------
 * Nhận file qua TCP (AES-CBC)
 */
public class FileReceiver implements Runnable {

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

            // ===== READ IV =====
            int ivLen = dis.readInt();
            byte[] ivBytes = new byte[ivLen];
            dis.readFully(ivBytes);

            // ===== READ DATA =====
            int dataLen = dis.readInt();
            byte[] encrypted = new byte[dataLen];
            dis.readFully(encrypted);

            // ===== DECRYPT =====
            Cipher cipher = keyManager.createDecryptCipher(
                    peer.getId(),
                    new IvParameterSpec(ivBytes)
            );
            byte[] plain = cipher.doFinal(encrypted);

            // ===== WRITE FILE =====
            File dir = new File(System.getProperty("user.home"), "Downloads/p2p-chat");
            if (!dir.exists()) dir.mkdirs();

            File outFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(plain);
            }

            Platform.runLater(() ->
                    System.out.println("Received file: " + outFile.getAbsolutePath())
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
