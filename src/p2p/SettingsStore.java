package p2p;

import java.io.*;
import java.util.Properties;

/**
 * SettingsStore
 * --------------
 * Lưu trữ cấu hình người dùng:
 * - mute / block theo peerId
 * - thư mục history
 */
public class SettingsStore {

    private static final String BASE_DIR = System.getProperty("user.home") + File.separator + ".p2p-chat";
    private static final String SETTINGS_FILE = BASE_DIR + File.separator + "settings.properties";

    private final Properties props = new Properties();

    public SettingsStore() {
        load();
    }

    /* ================= LOAD / SAVE ================= */

    private void load() {
        try {
            File dir = new File(BASE_DIR);
            if (!dir.exists()) dir.mkdirs();

            File f = new File(SETTINGS_FILE);
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) {
                    props.load(in);
                }
            }
        } catch (IOException ignored) {}
    }

    private synchronized void save() {
        try {
            File dir = new File(BASE_DIR);
            if (!dir.exists()) dir.mkdirs();

            try (OutputStream out = new FileOutputStream(SETTINGS_FILE)) {
                props.store(out, "P2P Chat Settings");
            }
        } catch (IOException ignored) {}
    }

    /* ================= MUTE / BLOCK ================= */

    public boolean isMutedById(String peerId) {
        return Boolean.parseBoolean(props.getProperty("mute." + peerId, "false"));
    }

    public void setMutedById(String peerId, boolean muted) {
        props.setProperty("mute." + peerId, String.valueOf(muted));
        save();
    }

    public boolean isBlockedById(String peerId) {
        return Boolean.parseBoolean(props.getProperty("block." + peerId, "false"));
    }

    public void setBlockedById(String peerId, boolean blocked) {
        props.setProperty("block." + peerId, String.valueOf(blocked));
        save();
    }

    /* ================= HISTORY FOLDER ================= */

    public String getHistoryFolder() {
        return props.getProperty("history.folder", BASE_DIR + File.separator + "history");
    }

    public void setHistoryFolder(String path) {
        props.setProperty("history.folder", path);
        save();
    }
}
