package p2p;

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

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("PeerServer listening on TCP port " + port);
            while (running) {
                Socket s = ss.accept();
                if (!running) { s.close(); break; }
                if (connectionListener != null) connectionListener.onNewConnection(s);
            }
        } catch (java.net.BindException be) {
            System.err.println("PeerServer BindException: port " + port + " already in use.");
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    public static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) { return true; }
        catch (Exception e) { return false; }
    }
}
