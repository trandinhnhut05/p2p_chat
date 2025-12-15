package p2p;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerServer extends Thread {
    private final int port;
    private final ConnectionListener connectionListener;
    private volatile boolean running = true;

    public interface ConnectionListener {
        void onNewConnection(Socket socket);
    }

    public PeerServer(int port, ConnectionListener listener) {
        this.port = port;
        this.connectionListener = listener;
        setDaemon(true);
    }

    public void shutdown() {
        running = false;
        try { new Socket("127.0.0.1", port).close(); } catch (Exception ignored) {}
    }
    public static boolean isPrivateIP(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }


    public static boolean isSameLAN(String ip1, String ip2) {
        try {
            byte[] a = InetAddress.getByName(ip1).getAddress();
            byte[] b = InetAddress.getByName(ip2).getAddress();
            // So sánh 3 byte đầu (255.255.255.x)
            return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("PeerServer listening on TCP port " + port);

            while (running) {
                Socket s = ss.accept();
                if (!running) {
                    s.close();
                    break;
                }

                String remoteIp = s.getInetAddress().getHostAddress();

                // ✅ CHỈ CHẶN IP PUBLIC
                if (!isPrivateIP(remoteIp)) {
                    System.out.println("Rejected non-private IP: " + remoteIp);
                    s.close();
                    continue;
                }

                if (connectionListener != null) {
                    connectionListener.onNewConnection(s);
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }


    public static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) { return true; }
        catch (Exception e) { return false; }
    }
}
