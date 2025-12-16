package p2p;

import javax.sound.sampled.AudioFormat;

public class AudioUtils {
    public static AudioFormat getFormat() {
        return new AudioFormat(
                16000.0f,
                16,
                1,
                true,
                false
        );
    }
}
