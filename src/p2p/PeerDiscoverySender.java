package p2p;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerDiscoverySender extends Thread {

    private final String username;
    private final int servicePort;
    private final int discoveryPort;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PeerDiscoverySender(String username,
                               int servicePort,
                               int discoveryPort) {
        this.username = username;
        this.servicePort = servicePort;
        this.discoveryPort = discoveryPort;
        setName("PeerDiscoverySender");
        setDaemon(true);
    }


    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);

            while (running.get()) {

                String msg = "DISCOVER|" + username + "|" + servicePort;
                byte[] data = msg.getBytes(StandardCharsets.UTF_8);

                Enumeration<NetworkInterface> interfaces =
                        NetworkInterface.getNetworkInterfaces();

                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();

                    if (!ni.isUp() || ni.isLoopback()) continue;

                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        InetAddress broadcast = ia.getBroadcast();
                        if (broadcast == null) continue;

                        DatagramPacket packet =
                                new DatagramPacket(
                                        data,
                                        data.length,
                                        broadcast,
                                        discoveryPort
                                );

                        socket.send(packet);
                    }
                }

                Thread.sleep(2000);
            }

        } catch (Exception e) {
            if (running.get()) e.printStackTrace();
        }finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }



    public void shutdown() {
        running.set(false);
        interrupt();
    }
}
