package sk.jmurin.android.qrtransporter.decoding;

import org.opencv.core.Mat;

/**
 * Created by jmurin on 22.3.2016.
 */
public class AnalyzerTask {

    final int frameID;
    final Mat img;
    final boolean isQR;
    final boolean isPoisonPill;

    public AnalyzerTask(boolean isPoisonPill, boolean isQR, Mat img, int frameID) {
        this.isPoisonPill = isPoisonPill;
        this.isQR = isQR;
        this.img = img;
        this.frameID = frameID;
    }
}
