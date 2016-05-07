package sk.jmurin.android.qrtransporter.decoding;

/**
 * Created by jmurin on 1.5.2016.
 */
public class HintData {
   final int rowsHint;
    final int[] klasifikator;
    final int hintID;

    public HintData(int hintID, int[] klasifikator, int rowsHint) {
        this.hintID = hintID;
        this.klasifikator = klasifikator;
        this.rowsHint = rowsHint;
    }
}
