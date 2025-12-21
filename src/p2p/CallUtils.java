package p2p;

import java.io.IOException;
import java.net.ServerSocket;

public class CallUtils {

    public static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
