package sk.jmurin.android.qrtransporter.sending;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import sk.jmurin.android.qrtransporter.R;

public class OdoslatActivity extends AppCompatActivity {

    private Timer timer;
    // gui
    private ImageView qrCodeImageView;
    private TextView statsTextView;
    private Spinner suborSpinner;
    private Button odoslatButton;
    private int smallerDimension;
    private int FPS = 5;
    private Spinner fpsSpinner;
    private int VELKOST;
    private Spinner velkostSpinner;
    private String potrebnyCas;
    private SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
    // animovanie
    private final Handler imgGeneratorResultHandler;
    private final Handler animatorResultHandler;
    private boolean beziAnimovanie;
    private double RYCHLOST;
    private DecimalFormat df = new DecimalFormat("##.##");
    private String data;
    private List<File> qrImgFiles;
    private String[] casti;
    private Thread imgGeneratorThread;
    private boolean generating = false;
    private long startTime;
    private int counter;
    private BitmapFactory.Options options;

    public OdoslatActivity() {
        imgGeneratorResultHandler = new Handler(imgGeneratorCallback);
        animatorResultHandler = new Handler(animatorCallback);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_odoslat);
        System.out.println("OdoslatActivity oncreate");
        qrCodeImageView = (ImageView) findViewById(R.id.qrCodeImageView);
        statsTextView = (TextView) findViewById(R.id.statsTextView);
        odoslatButton = (Button) findViewById(R.id.odoslatButton);

        //Find screen size
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        int width = point.x;
        int height = point.y;
        smallerDimension = width < height ? width : height;
        smallerDimension *= 1.4;
        smallerDimension = 177;

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
                loadSubor(itemAtPosition);
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
        fpsSpinner.setSelection(4);
        FPS = 5;
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

        // init VELKOST spinner
        velkostSpinner = (Spinner) findViewById(R.id.velkostSpinner);
        String[] velkosti = {"100 B", "300 B", "500 B", "600 B", "700 B", "800 B", "900 B", "1050 B", "1200 B", "1400 B", "1600 B", "1800 B"};
        ArrayAdapter<String> adapter3 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, velkosti);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        velkostSpinner.setAdapter(adapter3);
        velkostSpinner.setSelection(3);
        VELKOST = 600;
        velkostSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String itemAtPosition = (String) parent.getItemAtPosition(position);
                System.out.println("vybrane velkostSpinner: [" + itemAtPosition + "]");
                VELKOST = Integer.parseInt(itemAtPosition.replace(" B", ""));
                updateRychlost();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                System.out.println("velkostSpinner Spinner: nothing selected");
            }
        });

        // loadneme prvu polozku zo spinnera
        loadSubor(subory[0]);
    }

    private void updateRychlost() {
        RYCHLOST = FPS * VELKOST / 1024.0;
        refreshStatsTextView();
    }

    // updatne sa po zmene rychlosti, fps alebo suboru
    // handleri tiez modifikuju statsview
    private void refreshStatsTextView() {
        int cas = (data.length() / VELKOST + 1) / FPS; // pocet sekund
        String statsText = "veľkosť súboru: " + (data.length() / 1024) + " KB \n" +
                "rýchlosť: " + df.format(RYCHLOST) + " KB/s \n" +
                "potrebny čas: " + sdf.format(new Date(cas * 1000));
        potrebnyCas = sdf.format(new Date(cas * 1000));
        statsTextView.setText(statsText);
    }

    private void loadSubor(String itemAtPosition) {
        InputStream ins = getResources().openRawResource(getResources().getIdentifier(itemAtPosition.split("\\.")[0], "raw", getPackageName()));
        try {
            byte[] bytes = readBytes(ins);
            String base64data = Base64.encodeToString(bytes, Base64.DEFAULT);
            data = base64data;
            System.out.println("nacitane data size: " + data.length() + " data: " + data);

            refreshStatsTextView();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // zastavime animovanie ak bezi, lebo sa vybral novy subor
        if (beziAnimovanie) {
            stopAnimovanie();
        }
    }

    public void odoslatButtonClicked(View view) {
        System.out.println("odoslatButtonClicked");
        if (!beziAnimovanie) {
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
        beziAnimovanie = true;
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
        beziAnimovanie = false;
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
        // rozdelime data
        casti = new String[data.length() / VELKOST];
        for (int i = 0; i < casti.length - 1; i++) {
            casti[i] = data.substring(i * VELKOST, (i + 1) * VELKOST);
        }
        casti[casti.length - 1] = data.substring((casti.length - 1) * VELKOST);
        System.out.println("pocet casti: " + casti.length);

        // spustime generatora obrazkov
        qrImgFiles = new CopyOnWriteArrayList<>();
        generating = true;
        QRCodesImgGenerator codesImgGenerator = new QRCodesImgGenerator(casti, smallerDimension, imgGeneratorResultHandler, getApplicationContext());
        imgGeneratorThread = new Thread(codesImgGenerator);
        imgGeneratorThread.start();
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
                    String statsText = "veľkosť súboru: " + (data.length() / 1024) + " KB \n" +
                            "frame: 0/" + qrImgFiles.size() + "\n" +
                            "rýchlosť: " + df.format(RYCHLOST) + " KB/s  (" + potrebnyCas + ")";
                    if (beziAnimovanie) {
                        statsTextView.setText(statsText);
                    }
                    // ak mame aspon 20 obrazkov, alebo mame vsetky, tak zacneme s animaciou
                    if (qrImgFiles.size() == casti.length || qrImgFiles.size() > 20) {
                        if (beziAnimovanie) {
                            startTime = System.currentTimeMillis();
                            startTimer();
                        }
                    }
                }
                return true;
            }

            if (message.what == R.id.qr_img_generating_stopped) {
                generating = false;
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
            if (message.what == R.id.animate_new_qr && beziAnimovanie) {
                Bitmap obr = (Bitmap) message.obj;

                Bundle args = message.getData();
                String statsText = "veľkosť súboru: " + (data.length() / 1024) + " KB \n" +
                        "frame: " + args.getCharSequence(Constants.ODOSLAT_STATUS) + "\n" +
                        "rýchlosť: " + df.format(RYCHLOST) + " KB/s\n" +
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
        // TODO: ulozit idcko aktualnej snimky a aktualne data
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

}
