package sk.jmurin.android.qrtransporter.decoding;

import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import org.opencv.core.Mat;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import sk.jmurin.android.qrtransporter.R;

import static sk.jmurin.android.qrtransporter.sending.Constants.*;

/**
 * Created by Janco1 on 27. 10. 2015.
 */
public class QRAnalyzer implements Runnable {

    private final String TAG;
    private final Handler resultHandler;
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

    /**
     * thread that runs native code for analyzing QR code received in onCameraFrame in ReadQRActivity
     *
     *
     * @param qrCodesToAnalyze
     * @param results
     * @param tag
     * @param decodedResultHandler
     * @param intent
     */
    public QRAnalyzer(BlockingQueue<AnalyzerTask> qrCodesToAnalyze, ConcurrentSkipListSet<String> results, int tag, Handler decodedResultHandler, Intent intent) {
        this.qrCodesToAnalyze = qrCodesToAnalyze;
        this.results = results;
        this.tag = tag;
        this.resultHandler = decodedResultHandler;
        TAG = "QRAnalyzer" + tag;
        setupScanner(intent);
    }

    public void setupScanner(Intent intent) {
        Log.i(TAG, "setupScanner() ");
        mScanner = new ImageScanner();
        //mScanner.setConfig(0, 0, 1);
        mScanner.setConfig(Symbol.QRCODE, Config.ENABLE, 1);
        //mScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
        //mScanner.setConfig(Symbol.QRCODE, Config.ENABLE, 0);
        mScanner.setConfig(0, Config.X_DENSITY, 3);
        mScanner.setConfig(0, Config.Y_DENSITY, 3);

//        int[] symbols = intent.getIntArrayExtra(ZBarConstants.SCAN_MODES);
//        if (symbols != null) {
//            mScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
//            for (int symbol : symbols) {
//                mScanner.setConfig(symbol, Config.ENABLE, 1);
//            }
//        }

    }

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
                String result = readQR(task.mGray);
                analyzujText(result);
                task = qrCodesToAnalyze.take();
            }

        } catch (InterruptedException ex) {
            Log.i(TAG, "interrupted ");
            sendStats();
            return;
            //Logger.getLogger(QRAnalyzer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(QRAnalyzer.class.getName()).log(Level.SEVERE, null, ex);
            Log.e(TAG, " interrupted by exception: " + ex.getMessage());
            sendStats();
            return;
        }
        // this code should never happen
       // Log.i(TAG, "successfully finished ");
    }

    private void sendStats() {
        Log.i(TAG, "sending stats ");
        int total = 0;
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

    public String readQR(Mat mGray) {
        Log.d(TAG, "reading QR with native code");
        if (!initialized) {
            barcode = new Image(mGray.cols(), mGray.rows(), "Y800");
            initialized = true;
            startTime = System.currentTimeMillis();
        }
        long start = System.nanoTime();
        int width = mGray.width(), height = mGray.height(), channels = mGray.channels();
        byte[] sourcePixels = new byte[width * height * channels];
        mGray.get(0, 0, sourcePixels);
        long endTime = System.nanoTime();
        Log.d(TAG, String.format("Elapsed time copy data: %.2f ms", (float) (endTime - start) / 1000000) + " image size: " + sourcePixels.length);

        start = System.nanoTime();
        barcode.setData(sourcePixels);

        int result = mScanner.scanImage(barcode);
        String resultData = "";
        if (result != 0) {
            SymbolSet syms = mScanner.getResults();
            for (Symbol sym : syms) {
                String symData = sym.getData();
                resultData = symData.replaceAll("\n", "");
                Log.d(TAG, "sym data: " + resultData);
            }
        }
        endTime = System.nanoTime();
        Log.i(TAG, String.format("Elapsed time read data: %.2f ms", (float) (endTime - start) / 1000000));
        return resultData;
    }

    private void analyzujText(String zachytenyText) {
        try {
            if (isValid(zachytenyText)) {
                vsetkych = Integer.parseInt(zachytenyText.split("#")[0].split("/")[1]);
                predchadzajuci = Integer.parseInt(zachytenyText.split("#")[0].split("/")[0]);
                countOK++;

                // ulozime si data pre neskorsie spracovanie
                if (!results.contains(zachytenyText)) {
                    if (results.size() < vsetkych) {
                        // nemame tieto data a nemame ich vsetky
                        results.add(zachytenyText);
                        Log.i(TAG, "prijate NOVE data: [" + zachytenyText + "]");
                        Message message = Message.obtain(resultHandler, R.id.qrcode_decode_succeeded, zachytenyText);
                        message.sendToTarget();
                    } else {
                        // toto by sa nemalo nikdy stat
                        // nemame tieto data a mame ich vsetky
                        // skontrolujeme kde sa nezhoduju data
                        for (String chunk : results) {
                            if (chunk.startsWith(zachytenyText.substring(0, 10))) {
                                // skusime sa pozriet kde sa nezhoduju
                                String oldData = chunk;
                                String newData = zachytenyText;
                                for (int i = 0; i < oldData.length(); i++) {
                                    if (oldData.charAt(i) != newData.charAt(i)) {
                                        Log.e(TAG, "DATA SA NEZHODUJU:");
                                        Log.e(TAG, oldData);
                                        Log.e(TAG, newData.substring(0, i));
                                        break;
                                    }
                                }
                            }

                        }
                    }
                } else {
                    //System.out.println("tieto data uz mame: " + zachytenyText);
                    Log.d(TAG, "tieto data uz mame: [" + zachytenyText + "]");
                }

            } else {
                Log.i(TAG, "zachytenyText ale neplatny: [" + zachytenyText + "]");
                countERROR++;
            }
        } catch (Exception e) {
            Log.e(TAG, "VYNIMKA: " + e.getLocalizedMessage());
            e.printStackTrace();
            Log.e(TAG, "ZACHYTENE DATA: [" + zachytenyText + "]");
        }
    }

    private boolean isValid(String zachytenyText) {
        if (zachytenyText == null || zachytenyText.length() == 0) {
            return false;
        }
        // musi obsahovat int/int#string
        try {
            String hlavicka = zachytenyText.split("#")[0];
            int cislo1 = Integer.parseInt(hlavicka.split("/")[0]);
            int cislo2 = Integer.parseInt(hlavicka.split("/")[1]);
        } catch (Exception e) {
            // e.printStackTrace();
            return false;
        }

        return true;
    }


//    private void ulozNeprecitanyObrazok(Bitmap br, String name) {
//        System.out.println("ulozNeprecitanyObrazok");
//        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//        File file = new File(path, "Transporter/null" + name + ".png");
//
//        try {
//            System.out.println("subor cesta: " + file);
//            FileOutputStream out = new FileOutputStream(file);
//            boolean vysledok = br.compress(Bitmap.CompressFormat.PNG, 100, out);
//            //System.out.println("uspesne skonvertovalo: " + vysledok);
//            out.flush();
//            out.close();
//            //System.out.println("outputstream closed ");
//        } catch (Exception e) {
//            // Unable to create file, likely because external storage is
//            // not currently mounted.
//            Log.w("ExternalStorage", "Error writing " + file, e);
//        }
//    }

//    private void ulozAktualnyObrazok() {
//        ChybnyObrazok co = new ChybnyObrazok();
//        co.bitmap = br.getBitmap(1);
//        predchadzajuci++;
//        co.cislo = predchadzajuci;
//        chybneObrazky.offer(co);
//        if (chybneObrazky.size() > 20) {
//            chybneObrazky.poll();
//        }
//    }

    //    private void saveImage3() {
//        long startTime = System.nanoTime();
//
//        String fileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
//                + "/sample_picture_" + System.currentTimeMillis() + ".jpg";
//        Imgproc.cvtColor(mRgba, mBGR, Imgproc.COLOR_RGBA2BGR, 3);
//        if (!imwrite(fileName, mBGR)) {
//            Log.e(TAG, "Failed to save photo to " + fileName);
//        }
//
//        long endTime = System.nanoTime();
//        System.out.println(String.format("Elapsed time image save3: %.2f ms", (float) (endTime - startTime) / 1000000));
//    }
}
