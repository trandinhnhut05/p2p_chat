package p2p;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý tất cả peer hiện có.
 */
public class PeerManager {

    // lưu tất cả peer theo id
    private static final Map<String, Peer> peerMap = new ConcurrentHashMap<>();

    // thêm peer vào map (gọi khi peer mới được phát hiện)
    public static void registerPeer(Peer p) {
        if (p != null && p.getId() != null) {
            peerMap.put(p.getId(), p);
        }
    }

    // xóa peer (nếu cần)
    public static void unregisterPeer(Peer p) {
        if (p != null && p.getId() != null) {
            peerMap.remove(p.getId());
        }
    }

    // lấy peer theo id
    public static Peer getPeerById(String id) {
        return peerMap.get(id);
    }

    // lấy tất cả peer (tuỳ chọn)
    public static Map<String, Peer> getAllPeers() {
        return peerMap;
    }
}
