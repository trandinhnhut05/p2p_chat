package p2p;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerHandler extends Thread {

    public interface MessageCallback {
        void onMessage(String fromIp, String fingerprint, String message);
        void onFileReceived(String fromIp, String filename, File file);
    }

    private final Socket socket;
    private final MessageCallback callback;

    private BufferedReader reader;
    private BufferedWriter writer;
    private InputStream in;
    private OutputStream out;

    private String peerFingerprint;
    private boolean handshaked = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public PeerHandler(Socket socket, MessageCallback callback) {
        this.socket = socket;
        this.callback = callback;
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        String fromIp = socket.getInetAddress().getHostAddress();
        try {
            String line;
            while ((line = reader.readLine()) != null) {

                // ===== HELLO =====
                if (line.startsWith("/HELLO:")) {
                    // /HELLO:username;port;fingerprint
                    String[] p = line.substring(7).split(";");
                    if (p.length >= 3) {
                        String peerUsername = p[0].trim();
                        int peerPort = Integer.parseInt(p[1].trim());
                        peerFingerprint = p[2].trim();

                        // có thể dùng callback để cập nhật peer info
                        // (fingerprint được truyền lên MainUI rồi)
                        handshaked = true;
                    }
                    continue;
                }


                if (!handshaked) continue;

                // ===== PING =====
                if (line.startsWith("/PING:")) {
                    sendLine("/PONG:" + line.substring(6));
                    continue;
                }

                // ===== FILE =====
                if (line.startsWith("/FILE:")) {
                    String[] parts = line.split(":", 3);
                    if (parts.length < 3) continue;

                    String filename = parts[1];
                    long size = Long.parseLong(parts[2]);

                    File tmp = File.createTempFile("recv_", "_" + filename);
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[8192];
                        long received = 0;
                        while (received < size) {
                            int n = in.read(buf, 0, (int) Math.min(buf.length, size - received));
                            if (n < 0) break;
                            fos.write(buf, 0, n);
                            received += n;
                        }
                    }

                    if (callback != null)
                        callback.onFileReceived(fromIp, filename, tmp);
                    continue;
                }

                // ===== NORMAL MESSAGE =====
                if (callback != null)
                    callback.onMessage(fromIp, peerFingerprint, line);
            }
        } catch (Exception ignored) {
        } finally {
            shutdown();
        }
    }

    public void sendMessage(String msg) {
        executor.submit(() -> sendLine(msg));
    }

    public void sendPing(long timestamp) {
        executor.submit(() -> sendLine("/PING:" + timestamp));
    }

    public synchronized void sendLine(String line) {
        try {
            writer.write(line);
            writer.write("\n");
            writer.flush();
        } catch (IOException ignored) {}
    }

    public void shutdown() {
        try { socket.close(); } catch (IOException ignored) {}
        executor.shutdownNow();
    }
}
