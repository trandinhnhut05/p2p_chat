package p2p;

import p2p.crypto.KeyManager;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import java.io.*;
import java.net.Socket;

/**
 * FileSender
 * -----------
 * Gửi file tới peer qua TCP (AES encrypted)
 */
public class FileSender {

    private static final int BUFFER_SIZE = 8192;

    public static void sendFile(Peer peer, File file, KeyManager keyManager) {
        try (Socket socket = new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("FILE");
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
            dos.flush();

            Cipher cipher = keyManager.createAESCipher(Cipher.ENCRYPT_MODE, peer.getId());

            try (CipherOutputStream cos = new CipherOutputStream(dos, cipher);
                 BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = bis.read(buffer)) != -1) {
                    cos.write(buffer, 0, read);
                }
                cos.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
