package sk.jmurin.android.qrtransporter.decoding;

import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import sk.jmurin.android.qrtransporter.R;

import static org.opencv.imgcodecs.Imgcodecs.imwrite;
import static sk.jmurin.android.qrtransporter.sending.Constants.*;

/**
 * Created by Janco1 on 27. 10. 2015.
 */
public class ColorQRAnalyzer implements Runnable {

    private final String TAG;
    private final Handler resultHandler;
    private final HintData hint;
    private ImageScanner mScanner;
    private Image barcode;
    private boolean initialized;
    private long startTime;

    private class ChybnyObrazok {
        Mat bgrMat;
        int cislo;
    }

    private final BlockingQueue<AnalyzerTask> qrCodesToAnalyze;
    private final ConcurrentSkipListSet<String> results;
    private final int tag;
    private int vsetkych = 1000;// default hodnota, ktora sa hned zmeni
    private int predchadzajuci = 0;
    private Map<String, Integer> statistika = new HashMap<>();
    private Queue<ChybnyObrazok> chybneObrazky = new LinkedList<>();
    private AnalyzerTask task;
    private int countOK;
    private int countERROR;

    static {
        System.loadLibrary("qr_reader");
    }

    static {
        System.loadLibrary("colorQRreader"); // "myjni.dll" in Windows, "libmyjni.so" in Unixes
    }

    // A native method that returns a Java String to be displayed on the
    // TextView
    public native String getMessage(long nativeObjAddr);

    /**
     * thread that runs native code for analyzing QR code received in onCameraFrame in ReadQRActivity
     *
     * @param qrCodesToAnalyze
     * @param results
     * @param tag
     * @param decodedResultHandler
     * @param intent
     */
    public ColorQRAnalyzer(BlockingQueue<AnalyzerTask> qrCodesToAnalyze,
                           ConcurrentSkipListSet<String> results,
                           int tag,
                           Handler decodedResultHandler,
                           Intent intent,
                           int[] klasifikator) {
        this.qrCodesToAnalyze = qrCodesToAnalyze;
        this.results = results;
        this.tag = tag;
        this.resultHandler = decodedResultHandler;
        TAG = "ColorQRAnalyzer" + tag;
        hint = new HintData();
        hint.klasifikator = klasifikator;
        hint.rowsHint=36;
        Log.i(TAG, "created ");
    }

    public native String readQR(long matAddrRgba, HintData klasifikator);

    @Override
    public void run() {
        Log.i(TAG, "executed ");
        try {
            task = qrCodesToAnalyze.take();
            while (task != null) {
                if (task.isPoisonPill) {
                    Log.i(TAG, "poison pill eaten ");
                    sendStats();
                    return;
                }
                long startTime = System.nanoTime();

                //Log.i(TAG, "dekodovanie vysledok: " + getMessage(task.img.getNativeObjAddr()));
                Log.i(TAG, "dekodovanie vysledok: " + readQR(task.img.getNativeObjAddr(), hint));

                long endTime = System.nanoTime();
                Log.i(TAG, String.format("Elapsed time dekodovanie: %.2f ms", (float) (endTime - startTime) / 1000000));

                task = qrCodesToAnalyze.take();
            }

        } catch (InterruptedException ex) {
            Log.i(TAG, "interrupted ");
            sendStats();
            return;
            //Logger.getLogger(QRAnalyzer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(ColorQRAnalyzer.class.getName()).log(Level.SEVERE, null, ex);
            Log.e(TAG, " interrupted by exception: " + ex.getMessage());
            sendStats();
            return;
        } finally {
            Log.i(TAG, "finally block  ");
        }
        // this code should never happen
        // Log.i(TAG, "successfully finished ");
    }

    private void sendStats() {
        Log.i(TAG, "sending stats ");
        int total = 1;
        total += countERROR;
        total += countOK;
        long duration = System.currentTimeMillis() - startTime;
        statistika.put(DECODING_SPEED_ms, (int) (duration / total));
        statistika.put(RESULT_OK, countOK);
        statistika.put(RESULT_ERROR, countERROR);

        Log.i(TAG, " stats: ");
        for (Map.Entry entry : statistika.entrySet()) {
            Log.i(TAG, entry.getKey() + ": " + entry.getValue());
        }
        Message message = Message.obtain(resultHandler, R.id.qranalyzer_stats, statistika);
        message.sendToTarget();
    }

    private void saveImage(Mat mRgba, int id) {
        long startTime = System.nanoTime();

        String fileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                + "/colorframe_" + id + ".png";
        Imgproc.cvtColor(mRgba, mRgba, Imgproc.COLOR_RGBA2BGR, 3);
        if (!imwrite(fileName, mRgba)) {
            Log.e(TAG, "Failed to save photo to " + fileName);
        }

        long endTime = System.nanoTime();
        Log.i(TAG, String.format("Elapsed time image save: %.2f ms", (float) (endTime - startTime) / 1000000));
    }

}
