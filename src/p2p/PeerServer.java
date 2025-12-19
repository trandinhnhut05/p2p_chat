package p2p;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PeerServer
 * ----------
 * TCP server lắng nghe kết nối từ peer khác
 */
public class PeerServer extends Thread {

    public interface ConnectionListener {
        void onNewConnection(Socket socket);
    }

    private final int port;
    private final ConnectionListener listener;

    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService acceptPool = Executors.newSingleThreadExecutor();

    public PeerServer(int port, ConnectionListener listener) {
        this.port = port;
        this.listener = listener;
        setName("PeerServer");
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);

            while (running.get()) {
                Socket socket = serverSocket.accept();
                acceptPool.execute(() -> listener.onNewConnection(socket));
            }
        } catch (IOException e) {
            if (running.get()) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        acceptPool.shutdownNow();
        interrupt();
    }

    /**
     * Kiểm tra port TCP còn trống không
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
