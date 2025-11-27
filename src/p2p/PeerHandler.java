package p2p;

import javafx.application.Platform;

import java.io.*;
import java.net.Socket;

public class PeerHandler extends Thread {
    private final Socket socket;
    private final MessageCallback callback;

    public interface MessageCallback {
        void onMessage(String from, String message);
        void onFileReceived(String from, File file);
    }

    public PeerHandler(Socket socket, MessageCallback callback) {
        this.socket = socket;
        this.callback = callback;
        setDaemon(true);
    }

    @Override
    public void run() {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            String fromIp = socket.getInetAddress().getHostAddress();
            while (true) {
                String type;
                try { type = dis.readUTF(); } catch (EOFException eof) { break; }

                if ("MSG".equals(type)) {
                    String msg = dis.readUTF();
                    if (callback != null) Platform.runLater(() -> callback.onMessage(fromIp, msg));
                } else if ("FILE".equals(type)) {
                    String filename = dis.readUTF();
                    long size = dis.readLong();
                    File out = new File("received_" + System.currentTimeMillis() + "_" + filename);
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        long remaining = size;
                        while (remaining > 0) {
                            int r = dis.read(buf, 0, (int)Math.min(buf.length, remaining));
                            if (r == -1) break;
                            fos.write(buf, 0, r);
                            remaining -= r;
                        }
                    }
                    if (callback != null) Platform.runLater(() -> callback.onFileReceived(fromIp, out));
                } else {
                    // unknown type - break
                    break;
                }
            }
        } catch (Exception e) {
            // ignore normally
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}
