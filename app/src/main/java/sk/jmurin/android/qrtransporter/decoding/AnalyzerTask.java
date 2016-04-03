package sk.jmurin.android.qrtransporter.decoding;

import org.opencv.core.Mat;

/**
 * Created by jmurin on 22.3.2016.
 */
public class AnalyzerTask {

    Mat mGray;
    boolean isQR;
    boolean isPoisonPill;

    public AnalyzerTask(boolean isPoisonPill, boolean isQR, Mat mGray) {
        this.isPoisonPill = isPoisonPill;
        this.isQR = isQR;
        this.mGray = mGray;
    }
}
