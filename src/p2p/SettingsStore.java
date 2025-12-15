package p2p;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Simple settings persistence using a properties file.
 * Location: ./p2p_settings.properties (in working dir)
 *
 * Keys:
 *  - peer.<id>.muted = true|false
 *  - peer.<id>.blocked = true|false
 *  - history.folder = absolutePath
 *  - account.fingerprint = fingerprint string for this client (optional)
 *  - account.username = username for this client (optional)
 */
public class SettingsStore {
    private static final String FILENAME = "p2p_settings.properties";
    private final Properties props = new Properties();

    public SettingsStore() {
        load();
    }

    private void load() {
        File f = new File(FILENAME);
        if (!f.exists()) return;
        try (InputStream in = new FileInputStream(f)) {
            props.load(in);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try (OutputStream out = new FileOutputStream(FILENAME)) {
            props.store(out, "P2P Chat settings");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String peerKey(String id, String prop) {
        return "peer." + id + "." + prop;
    }

    public boolean isMutedById(String id) {
        return Boolean.parseBoolean(props.getProperty(peerKey(id, "muted"), "false"));
    }

    public void setMutedById(String id, boolean muted) {
        props.setProperty(peerKey(id, "muted"), String.valueOf(muted));
        save();
    }

    public boolean isBlockedById(String id) {
        return Boolean.parseBoolean(props.getProperty(peerKey(id, "blocked"), "false"));
    }

    public void setBlockedById(String id, boolean blocked) {
        props.setProperty(peerKey(id, "blocked"), String.valueOf(blocked));
        save();
    }

    public String getHistoryFolder() {
        return props.getProperty("history.folder", "chat_history");
    }

    public void setHistoryFolder(String path) {
        props.setProperty("history.folder", path);
        save();
        try {
            Files.createDirectories(Path.of(path));
        } catch (Exception ignored) {}
    }

    // ----- account fingerprint (this client) -----
    public String getAccountFingerprint() {
        return props.getProperty("account.fingerprint", "");
    }

    public void setAccountFingerprint(String fp) {
        if (fp == null) fp = "";
        props.setProperty("account.fingerprint", fp);
        save();
    }

    public String getAccountUsername() {
        return props.getProperty("account.username", "");
    }

    public void setAccountUsername(String username) {
        if (username == null) username = "";
        props.setProperty("account.username", username);
        save();
    }
}
