package sk.jmurin.android.qrtransporter.decoding;

import org.opencv.core.Mat;

/**
 * Created by jmurin on 22.3.2016.
 */
public class AnalyzerTask {

    final int frameID;
    final Mat mGray;
    final boolean isQR;
    final boolean isPoisonPill;

    public AnalyzerTask(boolean isPoisonPill, boolean isQR, Mat mGray, int frameID) {
        this.isPoisonPill = isPoisonPill;
        this.isQR = isQR;
        this.mGray = mGray;
        this.frameID = frameID;
    }
}
