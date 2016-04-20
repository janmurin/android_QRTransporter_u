package sk.jmurin.android.qrtransporter.sending;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;

import java.io.File;
import java.io.FileOutputStream;

import sk.jmurin.android.qrtransporter.R;
import sk.jmurin.android.qrtransporter.sending.QRCodeEncoder;

/**
 * Created by Janco1 on 2. 11. 2015.
 */
public class QRCodesImgGenerator implements Runnable {
    private final String[] casti;
    private final Handler handler;
    private final int dimension;
    private final Context context;
    private final String loadedFilename;
    private final int FPS;
    private Bitmap resized;

    public QRCodesImgGenerator(String[] casti, int smallerDimension, Handler imgGeneratorResultHandler, Context applicationContext, String loadedFilename, int FPS) {
        this.casti = casti;
        this.handler = imgGeneratorResultHandler;
        this.dimension = smallerDimension;
        this.context = applicationContext;
        this.loadedFilename = loadedFilename;
        this.FPS = FPS;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < casti.length; i++) {
                if (Thread.currentThread().isInterrupted()) { // priamo vlakno prerusim aby som nemusel vyrabat executory
                    throw new InterruptedException();// ukoncime generovanie nasilne
                }
                System.out.println("i= " + i);
                String header = (i + 1) + "/" + casti.length + "#";
                if (i == 0) {
                    header = (i + 1) + "/" + casti.length + "/" + FPS + "/" + loadedFilename.replaceAll("/","").replaceAll("#","") + "#";
                }
                QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(header + casti[i],
                        null,
                        Contents.Type.TEXT,
                        BarcodeFormat.QR_CODE.toString(),
                        dimension);

                Bitmap obrazok = qrCodeEncoder.encodeAsBitmap();
                // odstranime prilis vela bielych okrajov
                obrazok = odstranBieleOkraje(obrazok);

                File path = context.getFilesDir();
                File file = new File(path, "gen" + i + ".png");

                path.mkdirs();
                FileOutputStream out = new FileOutputStream(file);
                boolean vysledok = obrazok.compress(Bitmap.CompressFormat.PNG, 100, out);
                //System.out.println("uspesne skonvertovalo: " + vysledok);
                out.flush();
                out.close();

//                File path2 =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//                File file2 = new File(path2, "qr_gen" + i + ".png");
//                FileOutputStream out2 = new FileOutputStream(file2);
//                obrazok.compress(Bitmap.CompressFormat.PNG, 100, out2);
//                //System.out.println("uspesne skonvertovalo: " + vysledok);
//                out2.flush();
//                out2.close();
                //System.out.println("outputstream closed ");
                Message message = Message.obtain(handler, R.id.qr_img_file, file);
                message.sendToTarget();
            }
        } catch (InterruptedException e) {
            System.out.println("prerusenie generatora");
            //e.printStackTrace();
        } catch (Exception e) {
            System.out.println("vynimka generatora");
            // TODO: osetrit pripad nedostatku miesta
            e.printStackTrace();
        } finally {
            Message message = Message.obtain(handler, R.id.qr_img_generating_stopped, false);
            message.sendToTarget();
            System.out.println("generator skonceny");
        }
    }

    private Bitmap odstranBieleOkraje(Bitmap obrazok) {
        // po diagonale zistit velkost bieleho okraja
        int velkostOkraja = 0;
        for (int i = 0; i < obrazok.getWidth(); i++) {
            if (obrazok.getPixel(i, i) == Color.BLACK) {
                velkostOkraja = i;
                break;
            }
        }
        System.out.println("velkostOkraja: " + velkostOkraja);
        // zmensujeme iba obrazky ktore maju velku velkost okraja
        if (velkostOkraja > 10) {
            resized = Bitmap.createBitmap(obrazok, velkostOkraja - 5, velkostOkraja - 5, obrazok.getWidth() - 2 * velkostOkraja + 10, obrazok.getWidth() - 2 * velkostOkraja + 10);
            return resized;
        }
        return obrazok;
    }


}
