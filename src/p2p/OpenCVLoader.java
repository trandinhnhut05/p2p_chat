package p2p;

public class OpenCVLoader {
    private static boolean loaded = false;

    public static synchronized void init() {
        if (!loaded) {
            System.loadLibrary("opencv_java4120");
            loaded = true;
        }
    }
}
