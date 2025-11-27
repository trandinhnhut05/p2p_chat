package p2p;
public class PeerInfo {
    public String name;
    public String ip;
    public int port;

    public PeerInfo(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }

    @Override
    public String toString() {
        return name + " (" + ip + ":" + port + ")";
    }
}

