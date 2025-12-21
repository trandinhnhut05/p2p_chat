package p2p;

import p2p.crypto.CryptoUtils;
import p2p.crypto.KeyManager;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;

/**
 * FileSender
 * -----------
 * Gửi file qua TCP (AES-CBC)
 */
public class FileSender {

    public static void sendFile(Peer peer, File file, KeyManager keyManager) {
        try (Socket socket = new Socket(peer.getAddress(), peer.getServicePort());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            // ===== HEADER =====
            dos.writeUTF("FILE");
            dos.writeUTF(file.getName());

            // ===== READ FILE =====
            byte[] fileBytes = Files.readAllBytes(file.toPath());

            // ===== ENCRYPT =====
            IvParameterSpec iv = CryptoUtils.generateIv();
            Cipher cipher = keyManager.createEncryptCipher(peer.getId(), iv);
            byte[] encrypted = cipher.doFinal(fileBytes);

            // ===== SEND =====
            dos.writeInt(iv.getIV().length);
            dos.write(iv.getIV());

            dos.writeInt(encrypted.length);
            dos.write(encrypted);
            dos.flush();

            System.out.println("Sent encrypted file: " + file.getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
