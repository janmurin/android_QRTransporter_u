package sk.jmurin.android.qrtransporter.sending;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import sk.jmurin.android.qrtransporter.R;

public class OdoslatActivity2 extends AppCompatActivity {

    private Timer timer;
    private static final String TAG = "OdoslatActivity2";
    // gui
    private ImageView qrCodeImageView;
    private TextView statsTextView;
    private Spinner suborSpinner;
    private Button odoslatButton;
    private int FPS = 5;
    private Spinner fpsSpinner;
    private int CODE_DATA_SIZE;
    private Spinner velkostSpinner;
    private String potrebnyCas;
    private SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
    // animovanie
    private final Handler imgGeneratorResultHandler;
    private final Handler animatorResultHandler;
    private boolean isAnimating;
    private double RYCHLOST;
    private DecimalFormat df = new DecimalFormat("##.##");
    private byte[] sendingData;
    private List<File> qrImgFiles;
    private Thread imgGeneratorThread;
    private boolean generating = false;
    private long startTime;
    private int counter;
    private BitmapFactory.Options options;
    private String loadedFilename;
    private int screenWidth;
    private int screenHeight;

    public OdoslatActivity2() {
        imgGeneratorResultHandler = new Handler(imgGeneratorCallback);
        animatorResultHandler = new Handler(animatorCallback);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_odoslat2);
        System.out.println("OdoslatActivity oncreate");
        qrCodeImageView = (ImageView) findViewById(R.id.qrCodeImageView);
        statsTextView = (TextView) findViewById(R.id.statsTextView);
        odoslatButton = (Button) findViewById(R.id.odoslatButton);

        //Find screen size
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        screenWidth = point.x;
        screenHeight = point.y;

        // init subory spinner
        suborSpinner = (Spinner) findViewById(R.id.suborySpinner);
        String[] subory = {"test1.txt", "test2.jpg", "test3.pdf"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subory);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        suborSpinner.setAdapter(adapter);
        suborSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String itemAtPosition = (String) parent.getItemAtPosition(position);
                System.out.println("vybrany subor: [" + itemAtPosition + "]");
                loadSubor(itemAtPosition, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                System.out.println("Spinner: nothing selected");
            }
        });

        // init fps spinner
        fpsSpinner = (Spinner) findViewById(R.id.fpsSpinner);
        String[] fpska = {"1 FPS", "2 FPS", "3 FPS", "4 FPS", "5 FPS", "6 FPS", "7 FPS", "8 FPS", "9 FPS", "10 FPS", "11 FPS", "12 FPS", "13 FPS",
                "14 FPS", "15 FPS", "16 FPS", "17 FPS", "18 FPS", "19 FPS", "20 FPS"};
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fpska);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fpsSpinner.setAdapter(adapter2);
        fpsSpinner.setSelection(0);
        FPS = 1;
        fpsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String itemAtPosition = (String) parent.getItemAtPosition(position);
                System.out.println("vybrane fps: [" + itemAtPosition + "]");
                FPS = Integer.parseInt(itemAtPosition.replace(" FPS", ""));
                updateRychlost();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                System.out.println("FPS Spinner: nothing selected");
            }
        });

        // init CODE_DATA_SIZE spinner
        velkostSpinner = (Spinner) findViewById(R.id.velkostSpinner);
        String[] velkosti = {"100 B", "300 B", "500 B", "600 B", "700 B", "800 B", "900 B", "1050 B", "1200 B", "1400 B", "1600 B", "1800 B"};
        ArrayAdapter<String> adapter3 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, velkosti);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        velkostSpinner.setAdapter(adapter3);
        velkostSpinner.setSelection(3);
        CODE_DATA_SIZE = 600;
        velkostSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String itemAtPosition = (String) parent.getItemAtPosition(position);
                System.out.println("vybrane velkostSpinner: [" + itemAtPosition + "]");
                // this sendingData size for QRcode is only minimal, it will be most probably larger
                // it is directive for encoder how much minimum sendingData size we want in one code
                CODE_DATA_SIZE = Integer.parseInt(itemAtPosition.replace(" B", ""));
                updateRychlost();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                System.out.println("velkostSpinner Spinner: nothing selected");
            }
        });

        // loadneme prvu polozku zo spinnera
        loadSubor(subory[0], 0);
    }

    private void updateRychlost() {
        RYCHLOST = FPS * CODE_DATA_SIZE / 1024.0;
        refreshStatsTextView();
    }

    // updatne sa po zmene rychlosti, fps alebo suboru
    // handleri tiez modifikuju statsview
    private void refreshStatsTextView() {
        int cas = (sendingData.length / CODE_DATA_SIZE + 1) / FPS; // pocet sekund
        String statsText = "veľkosť súboru: " + (sendingData.length / 1024) + " KB \n" +
                "rýchlosť: " + df.format(RYCHLOST) + " KB/s (" + CODE_DATA_SIZE + ")\n" +
                "potrebny čas: " + sdf.format(new Date(cas * 1000));
        potrebnyCas = sdf.format(new Date(cas * 1000));
        statsTextView.setText(statsText);
    }

    private void loadSubor(String itemAtPosition, int pos) {
        InputStream ins = getResources().openRawResource(getResources().getIdentifier(itemAtPosition.split("\\.")[0], "raw", getPackageName()));
        loadedFilename = itemAtPosition;
        //try {
        //sendingData = readBytes(ins);
        if (pos == 0) {
            sendingData = getBase64("test1_base64.txt");
        }
        if (pos == 1) {
            sendingData = getBase64("test2_base64.txt");
        }
        if (pos == 2) {
            sendingData = getBase64("test3_base64.txt");
        }
        // ulozenie base64 verzie dat
        //String base64data = Base64.encodeToString(sendingData, Base64.DEFAULT);
        //System.out.println("base64 data: " + base64data);
        //saveData(base64data,itemAtPosition);
        System.out.println("nacitane sendingData size: " + sendingData.length + " sendingData: " + sendingData);
        refreshStatsTextView();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        // zastavime animovanie ak bezi, lebo sa vybral novy subor
        if (isAnimating) {
            stopAnimovanie();
        }
    }


    public void odoslatButtonClicked(View view) {
        System.out.println("odoslatButtonClicked");
        if (!isAnimating) {
            startAnimovanie();
        } else {
            stopAnimovanie();
        }
    }

    private void startAnimovanie() {
        if (generating) {
            // ak nahodou este stale generuje, tak nespravime nic a uzivatel musi pockat kym skonci generovanie
            Toast.makeText(this, "Este stale sa generuju obrazky", Toast.LENGTH_LONG).show();
            // generovanie sa skonci kazdu chvilu lebo dostalo interrupt
            return;
        }
        isAnimating = true;
        suborSpinner.setEnabled(false);
        velkostSpinner.setEnabled(false);
        fpsSpinner.setEnabled(false);

        odoslatButton.setText(R.string.stopText);
        vygenerujQRkody();
        // vlakno na animovanie QR kodov sa spusti az ked sa vygeneruje prvych 20 QR kodov
    }

    private void stopAnimovanie() {
        if (generating) {
            imgGeneratorThread.interrupt();
        }
        isAnimating = false;
        odoslatButton.setText(R.string.odoslatText);

        // nemusel sa stihnut spustit timer a samotne animovanie
        stopTimer();

        suborSpinner.setEnabled(true);
        velkostSpinner.setEnabled(true);
        fpsSpinner.setEnabled(true);
        qrCodeImageView.setImageResource(R.drawable.qr_transporter);
        refreshStatsTextView();
    }

    /**
     * vygeneruje z dat obrazky a spusteny generator upozornuje o stave generovania dokym neskonci generovanie
     */
    public void vygenerujQRkody() {

        // spustime generatora obrazkov
        qrImgFiles = new CopyOnWriteArrayList<>();
        generating = true;
        String val = (String) (velkostSpinner.getSelectedItem());
        CODE_DATA_SIZE = Integer.parseInt(val.replace(" B", ""));

        ColorQRCodesImgGenerator codesImgGenerator = new ColorQRCodesImgGenerator(
                sendingData,
                Math.min(screenWidth, screenHeight),
                Math.min(screenWidth, screenHeight), // todo prisposobit na fullscreen
                imgGeneratorResultHandler,
                getApplicationContext(),
                loadedFilename,
                FPS,
                CODE_DATA_SIZE);
        imgGeneratorThread = new Thread(codesImgGenerator);
        imgGeneratorThread.start();
    }

    private Context getContext() {
        return this;
    }

    private final Handler.Callback imgGeneratorCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == R.id.qr_img_file) {
                File subor = (File) message.obj;
                qrImgFiles.add(subor);
                System.out.println("pridany subor: " + subor.getName());

                if (timer == null) {
                    // este nebezi timer, takze animatorCallback nevie aktualizovat statusText a obrazok
                    String statsText = "veľkosť súboru: " + (sendingData.length / 1024) + " KB \n" +
                            "frame: 0/" + qrImgFiles.size() + "(" + CODE_DATA_SIZE + ")\n" +
                            "rýchlosť: " + df.format(RYCHLOST) + " KB/s  (" + potrebnyCas + ")";
                    if (isAnimating) {
                        statsTextView.setText(statsText);
                    }
                    // ak mame aspon 20 obrazkov, tak zacneme s animaciou
                    if (qrImgFiles.size() > 20) {
                        if (isAnimating) {
                            startTime = System.currentTimeMillis();
                            startTimer();
                        }
                    }
                }
                return true;
            }

            if (message.what == R.id.qr_img_generating_stopped) {
                generating = false;
                String msg = (String) message.obj;
                if (msg.equals(ColorQRCodesImgGenerator.GENERATING_INTERRUPTED)) {
                    return true;
                }
                if (!msg.equals(ColorQRCodesImgGenerator.GENERATING_SUCCESSFUL)) {
                    // generator exception
                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                    return true;
                }
                if (qrImgFiles.size() <= 20) {
                    // generating stopped and timer is not started
                    if (isAnimating) {
                        startTime = System.currentTimeMillis();
                        startTimer();
                    }
                } else {
                    // timer animating must be already running
                }
                return true;
            }

            if (message.what == R.id.update_code_data_size) {
                int size = (int) message.obj;
                CODE_DATA_SIZE = size;
                updateRychlost();
                return true;
            }
            return false;
        }
    };

    private void startTimer() {
        options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_4444;
        System.out.println("startujem timera");

        timer = new Timer();
        counter = 0;
        timer.scheduleAtFixedRate(new TimerTask() {

            public void run() {
                if (counter == qrImgFiles.size()) {
                    counter = 0;
                }
                Bitmap bitmap = BitmapFactory.decodeFile(qrImgFiles.get(counter).getAbsolutePath(), options);
                Message message = Message.obtain(animatorResultHandler, R.id.animate_new_qr, bitmap);
                Bundle args = new Bundle();
                args.putCharSequence(Constants.ODOSLAT_STATUS, (counter + 1) + "/" + qrImgFiles.size());
                message.setData(args);
                message.sendToTarget();

                counter++;
            }
        }, 0, 1000 / FPS);
    }

    ;

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private final Handler.Callback animatorCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == R.id.animate_new_qr && isAnimating) {
                Bitmap obr = (Bitmap) message.obj;

                Bundle args = message.getData();
                String statsText = "veľkosť súboru: " + (sendingData.length / 1024) + " KB \n" +
                        "frame: " + args.getCharSequence(Constants.ODOSLAT_STATUS) + "\n" +
                        "rýchlosť: " + df.format(RYCHLOST) + " KB/s (" + CODE_DATA_SIZE + ")\n" +
                        "ubehlo: " + sdf.format(new Date(System.currentTimeMillis() - startTime)) + " (" + potrebnyCas + ")";
                statsTextView.setText(statsText);

                qrCodeImageView.setImageBitmap(Bitmap.createScaledBitmap(obr, qrCodeImageView.getWidth(), qrCodeImageView.getHeight(), false));
                System.out.println("zobrazeny obrazok " + args.getCharSequence(Constants.ODOSLAT_STATUS));
                return true;
            }

            return false;
        }
    };

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAnimovanie();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        stopAnimovanie();
        // TODO: ulozit idcko aktualnej snimky a aktualne sendingData
    }

    @Override
    public void onBackPressed() {
        System.out.println("onBackPressed");
        finish();
    }

    public void novySuborButtonClicked(View view) {
        System.out.println("novySuborButtonClicked");
        // TODO: vybratie suboru
    }

    private byte[] readBytes(InputStream inputStream) throws IOException {
        // this dynamically extends to take the bytes you read
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the byteBuffer
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }

        // and then we can return your byte array.
        return byteBuffer.toByteArray();
    }

    private byte[] getBase64(String filename) {
        InputStream ins = getResources().openRawResource(getResources().getIdentifier(filename.split("\\.")[0], "raw", getPackageName()));
        BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
        StringBuilder out = new StringBuilder();
        String line;
        byte[] data = null;
        try {
            while ((line = reader.readLine()) != null) {
                out.append(line + "\n");
            }
            data = Base64.decode(out.toString(), Base64.DEFAULT);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    private File saveData(String data, String fileName) {
        Log.i(TAG, "saving data into file");
        if (isExternalStorageWritable()) {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            File file = null;
            try {
                // Make sure the Pictures directory exists.
                boolean mkdirs = path.mkdirs();
                if (mkdirs || path.isDirectory()) {
                    file = new File(path, fileName);

                    Log.i(TAG, "subor cesta: " + file);
//                    OutputStream os = new FileOutputStream(file);
//                    //System.out.println("full data: " + sb.toString());
//                    //os.write(Base64.decode(sb.toString(), Base64.NO_WRAP));
//                    os.write(data.);
//                    os.close();
                    PrintWriter pw = new PrintWriter(file);
                    pw.print(data);
                    pw.close();
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
