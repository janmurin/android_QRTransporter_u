package sk.jmurin.android.qrtransporter.decoding;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import sk.jmurin.android.qrtransporter.R;
import sk.jmurin.android.qrtransporter.sending.Constants;

import static org.opencv.imgcodecs.Imgcodecs.imwrite;
import static org.opencv.imgproc.Imgproc.putText;
import static sk.jmurin.android.qrtransporter.sending.Constants.*;


public class ReadQRActivity2 extends Activity implements CvCameraViewListener2 {
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("iconv");
    }

    private static final String TAG = "ReadQRActivity2";
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    SimpleDateFormat sdfElapsed = new SimpleDateFormat("mm:ss");

    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };
    private Mat mRgba;
    private Mat mGray;
    private Mat mBGR;
    private boolean initialized;
    private Handler decodedResultHandler;
    private BlockingQueue<AnalyzerTask> qrCodesToAnalyze;
    private long start;
    private ConcurrentSkipListSet<String> results;
    private int receivedFrames;
    ColorQRAnalyzer[] analyzers;
    private Map<String, Integer> statistika;
    private int acceptedStatsCount;
    private boolean[] najdene;// viac ako 1000 framov nebude
    private ExecutorService analyzerExecutor;
    private long startNano;
    private boolean readingCompleted;
    private boolean onPause;
    private String statusText;
    private int frameID;

    public ReadQRActivity2() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
//        // remove title
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.readqr_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.readQR_activity_java_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // init variables
        onPause = false;
        readingCompleted = false;
        acceptedStatsCount = 0;
        najdene = new boolean[1000];
        statistika = new HashMap<>();
        results = new ConcurrentSkipListSet<>();
        receivedFrames = 0;

        // we always start from beginning
        // if (savedInstanceState == null) {
        // TODO: ukladat priebezne nacitane vysledky
        decodedResultHandler = new Handler(decodedResultCallback);
        qrCodesToAnalyze = new LinkedBlockingQueue<>();
        statistika.put(Constants.RESULT_OK, 0);
        statistika.put(RESULT_ERROR, 0);
        statistika.put(DECODING_SPEED_ms, 0);
        //statusTextView = (TextView) findViewById(R.id.statusTextView);

        // init analyzers
        analyzers = new ColorQRAnalyzer[Runtime.getRuntime().availableProcessors()];
        analyzerExecutor = Executors.newFixedThreadPool(analyzers.length);
        for (int i = 0; i < analyzers.length; i++) {
            analyzers[i] = new ColorQRAnalyzer(qrCodesToAnalyze, results, i, decodedResultHandler, getIntent());
        }
        startAnalyzers();
    }

    private void startAnalyzers() {
        Log.i(TAG, "starting analyzers: " + analyzers.length);
        acceptedStatsCount = 0;
        for (int i = 0; i < analyzers.length; i++) {
            Thread analyzerThread = new Thread(analyzers[i]);
            //analyzerThread.start();
            analyzerExecutor.submit(analyzerThread);
        }
    }

    private void killAnalyzers() {
        Log.i(TAG, "killing analyzers");
        for (int i = 0; i < analyzers.length; i++) {
            qrCodesToAnalyze.offer(new AnalyzerTask(true, false, null, frameID));
        }
        analyzerExecutor.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed");
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.i(TAG, "onSaveInstanceState");
        // for now we will not save current progress
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause");
        onPause = true;
        killAnalyzers();
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        killAnalyzers();
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        Log.i(TAG, "onCameraViewStarted");
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mBGR = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        Log.i(TAG, "onCameraViewStopped");
        mRgba.release();
        mBGR.release();
        mGray.release();
    }

//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        Log.d(TAG, "onTouch invoked");
//
//        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//        File file = new File(path, "frame.png");
//        imwrite(file.getAbsolutePath(), mRgba);
//        return super.onTouchEvent(event);
//    }

    //    @Override
//    public boolean onTouch(MotionEvent event) {
//        Log.d(TAG, "onTouch invoked");
//
//        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//        File file = new File(path, "frame.png");
//        imwrite(file.getAbsolutePath(),mRgba);
//        return super.onTouchEvent(event);
//    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        //System.out.println(readQR(inputFrame.rgba().getNativeObjAddr()));
        //System.out.println(hello());
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        if (!initialized) {
            //barcode = new Image(mGray.cols(), mGray.rows(), "Y800");
            initialized = true;
            start = System.currentTimeMillis();
            frameID = 0;
        }
        frameID++;
        if (!readingCompleted) {
            // create task for analyzer
            // TODO: preskakovat tie framy ktore su uz dekodovane, nezalezi na tom ci sa stiha dekodovat alebo nie
            // TODO: urobit configuratora ktory otestuje schopnosti kamery snimat obraz
            // TODO: urychlit generovanie obrazkov
            startNano = System.nanoTime();
            Mat copy = new Mat();
            mRgba.copyTo(copy);
            AnalyzerTask newTask = new AnalyzerTask(false, true, copy, frameID);
            qrCodesToAnalyze.offer(newTask);
            Log.d(TAG, String.format("onCameraFrame: task created: %.2f ms", (float) (System.nanoTime() - startNano) / 1000000));

            receivedFrames++;
            Log.i(TAG, "onCameraFrame: qrCodesToAnalyze.size(): " + qrCodesToAnalyze.size() + "" +
                    " FPS: " + (1000 / ((System.currentTimeMillis() - start + 1) / receivedFrames)) + "" +
                    " results size: " + results.size());
            statusText = "ziskanych: " + results.size() + "/" + vsetkych;
            putText(mRgba, statusText, new Point(10, 100), Core.FONT_HERSHEY_PLAIN, 1, new Scalar(255, 0, 0), 2);
        }
        return mRgba;
    }

    private int vsetkych;
    private final Handler.Callback decodedResultCallback = new Handler.Callback() {

        @Override
        public boolean handleMessage(Message message) {
            if (message.what == R.id.COLORqrcode_decode_succeeded) {
//                String zachytenyText = (String) message.obj;
//
//                if (zachytenyText != null) {
//                    vsetkych = Integer.parseInt(zachytenyText.split("#")[0].split("/")[1]);
//                    int frameID = Integer.parseInt(zachytenyText.split("#")[0].split("/")[0]);
//                    najdene[frameID] = true;
//                    Log.i(TAG, "handleMessage: Najdenych: " + results.size() + "/" + vsetkych + " Aktualny: " + frameID + "/" + vsetkych + " Chybajuce: ");
////                    if (results.size() < vsetkych - 10) {
////                        statusTextView.append(">10");
////                    } else {
////                        for (int i = 1; i <= vsetkych; i++) {
////                            if (!najdene[i]) {
////                                statusTextView.append(i + " ");
////                            }
////                        }
////                    }
//                    if (vsetkych == results.size() && !readingCompleted) {
//                        Log.i(TAG, "MAME VSETKO");
//                        readingCompleted = true;
//                        // mame vsetko zabijeme analyzerov, oni odoslu statistiku a potom sa spusti intent
//                        killAnalyzers();
//                    }
//                }
                return true;
            }

            if (message.what == R.id.COLORqranalyzer_stats) {
//                // vezmeme statistiku z analyzera a updatneme celkovu statistiku
//                Map<String, Integer> stats = (Map<String, Integer>) message.obj;
//                Log.d(TAG, "analyzer stats");
//                for (Map.Entry entry : stats.entrySet()) {
//                    Log.d(TAG, entry.getKey() + ": " + entry.getValue());
//                    statistika.put((String) entry.getKey(), statistika.get(entry.getKey()) + (Integer) entry.getValue());
//                }
//                acceptedStatsCount++;
//                if (acceptedStatsCount == analyzers.length) {
//                    // all analyzers are finished, either is file transferred or transfer was cancelled by onBackPressed or onPause
//                    List<String> data = new ArrayList<>(results);
//                    Collections.sort(data, new DataComparator());
//                    double totalDataLength = 0;
//                    for (String dat : data) {
//                        Log.i(TAG, dat);
//                        totalDataLength += dat.length();
//                    }
//                    // vypiseme celkovu statistiku
//                    Log.i(TAG, "FINAL STATS");
//                    for (Map.Entry entry : statistika.entrySet()) {
//                        String kluc = (String) entry.getKey();
//                        int c = (int) entry.getValue();
//                        Log.i(TAG, kluc + " : " + c);
//                    }
//                    receivedFrames++;// so it is not 0
//                    long frameSpeed = ((System.currentTimeMillis() - start) / receivedFrames);
//                    double transferSpeed = totalDataLength / (System.currentTimeMillis() - start) * 1000;
//                    double successRate = statistika.get(Constants.RESULT_OK) / (double) (statistika.get(Constants.RESULT_OK) + statistika.get(RESULT_ERROR)) * 100;
//                    String elapsed = sdfElapsed.format(new Date(System.currentTimeMillis() - start));
//                    String statistics = "ziskanych framov: " + receivedFrames + "\n ms na frame: " + frameSpeed + "" +
//                            "\n transferSpeed: " + (int) transferSpeed + "\n successRate: " + (int) successRate + " elapsed: " + elapsed;
//                    Log.i(TAG, statistics);
//                    // ulozime ziskany subor
//                    if (!onPause) {
//                        // we start ReceivedFileActivity only when reading is not interrupted by onPause
//                        File novy = saveData(data);
//                        Intent test = new Intent(getApplicationContext(), ReceivedFileActivity.class);
//                        test.putExtra(ReceivedFileActivity.FILE_BUNDLE_KEY, novy);
//                        test.putExtra(ReceivedFileActivity.STATS, statistics);
//                        startActivity(test);
//                    }
//                }
            }
            return false;
        }
    };

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private File saveData(List<String> data) {
        Log.i(TAG, "saving data into file");
        if (isExternalStorageWritable()) {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String fileName = "defaultFileName";
            try {
                String first = data.get(0);
                fileName = first.split("#")[0].split("/")[3];
            } catch (Exception e) {
                e.printStackTrace();
            }

            File file = null;
            try {
                // Make sure the Pictures directory exists.
                boolean mkdirs = path.mkdirs();
                if (mkdirs || path.isDirectory()) {
                    file = new File(path, fileName);

                    Log.i(TAG, "subor cesta: " + file);
                    OutputStream os = new FileOutputStream(file);
                    StringBuilder sb = new StringBuilder();
                    for (String d : data) {
                        sb.append(d.substring(d.indexOf("#") + 1));
                    }
                    //System.out.println("full data: " + sb.toString());
                    os.write(Base64.decode(sb.toString(), Base64.NO_WRAP));
                    os.close();
                    Log.i(TAG, "outputstream closed ");
                }
            } catch (Exception e) {
                // Unable to create file, likely because external storage is
                // not currently mounted.
                Log.e("ExternalStorage", "Error writing " + file, e);
            }
            return file;
        } else {
            Log.e(TAG, "external storage not writable");
            return null;
        }
    }
}
