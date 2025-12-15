package p2p;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Broadcast discovery packet every second.
 * Format: username;localIP;servicePort[;fingerprint]
 */
public class PeerDiscoverySender extends Thread {
    private final String username;
    private final int servicePort;
    private final int discoveryPort;
    private volatile boolean running = true;

    public PeerDiscoverySender(String username, int servicePort, int discoveryPort) {
        this.username = username;
        this.servicePort = servicePort;
        this.discoveryPort = discoveryPort;
        setDaemon(true);
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setReuseAddress(true);

            SettingsStore ss = new SettingsStore();
            String myFp = ss.getAccountFingerprint();

            while (running) {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;

                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        InetAddress broadcast = ia.getBroadcast();
                        if (broadcast == null) continue;

                        String localIP = ia.getAddress().getHostAddress();

                        String msg = username + ";" + localIP + ";" + servicePort +
                                (myFp != null && !myFp.isBlank() ? ";" + myFp : "");

                        byte[] data = msg.getBytes(StandardCharsets.UTF_8);

                        DatagramPacket packet = new DatagramPacket(
                                data, data.length, broadcast, discoveryPort
                        );

                        socket.send(packet);
                    }
                }
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

}
