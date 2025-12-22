package p2p;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.opencv.core.Core;

public class OpenCVLoader {

    private static boolean loaded = false;

    /**
     * Khởi tạo OpenCV an toàn
     * @return true nếu OpenCV load thành công, false nếu thất bại
     */
    public static boolean init() {
        if (loaded) return true;

        try {
            // Thay đổi đường dẫn nếu bạn để opencv_java460.dll ở nơi khác
            System.load("C:\\Users\\trand\\Downloads\\opencv\\build\\java\\x64\\opencv_java4120.dll");

            // Hoặc nếu dùng java.library.path, uncomment:
            // System.loadLibrary("opencv_java460");

            loaded = true;
            System.out.println("🟢 OpenCV loaded successfully");
            return true;
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            loaded = false;
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("OpenCV Error");
                alert.setHeaderText("Cannot load OpenCV library");
                alert.setContentText("Make sure opencv_java460.dll is in the path:\n" +
                        "C:\\Users\\trand\\Downloads\\opencv\\build\\java\\x64");
                alert.showAndWait();
            });
            return false;
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }
}
