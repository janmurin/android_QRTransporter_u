package sk.jmurin.android.qrtransporter.sending;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

import sk.jmurin.android.qrtransporter.R;

/**
 * Created by Janco1 on 2. 11. 2015.
 */
public class ColorQRCodesImgGenerator implements Runnable {

    private static final String TAG = "ColorQRCodesImgGenerato";
    public static final String GENERATING_INTERRUPTED = "generating interrupted";
    public static final String GENERATING_SUCCESSFUL = "generating successful";
    public static final int HEADER_LENGTH = 5;
    private final int BYTE_CAPACITY;
    private final byte[] data;
    private final Handler handler;
    private final Context context;
    private final String loadedFilename;
    private final int FPS;
    private final ColorQRCodeEncoder qrCodeEncoder;
    private Bitmap resized;

    public ColorQRCodesImgGenerator(byte[] casti, int width, int height, Handler imgGeneratorResultHandler, Context applicationContext, String loadedFilename, int FPS, int CODE_DATA_SIZE) {
        this.data = casti;
        this.handler = imgGeneratorResultHandler;
        this.context = applicationContext;
        this.loadedFilename = loadedFilename;
        this.FPS = FPS;
        qrCodeEncoder = new ColorQRCodeEncoder(width, height, CODE_DATA_SIZE);
        BYTE_CAPACITY = qrCodeEncoder.getByteCapacity();
        Log.i(TAG, "BYTE_CAPACITY: " + BYTE_CAPACITY);
    }

    @Override
    public void run() {
        Message me = Message.obtain(handler, R.id.update_code_data_size, BYTE_CAPACITY);
        me.sendToTarget();
        try {
            int paketsCount = (int) Math.ceil((data.length + loadedFilename.getBytes().length) / (BYTE_CAPACITY - HEADER_LENGTH));
            Log.i(TAG, "paketsCount: " + paketsCount);
            if (paketsCount > 65535) {// 65535 is largest number that can be stored in 2 bytes
                throw new RuntimeException("too much pakets for 2 byte representation: " + paketsCount + " > " + 65535);
            }
            if (FPS > 255) {
                throw new RuntimeException("FPS is too much for 1 byte representation: " + FPS + " > " + 255);
            }
            // paketID # paketsCount # FPS # data
            //   2B          2B        1B    (BYTE_CAPACITY-5)B
            // create pakets
            int offset = 0;
            int paketID = 0;
            while (offset < data.length) {
                if (Thread.currentThread().isInterrupted()) { // priamo vlakno prerusim aby som nemusel vyrabat executory
                    throw new InterruptedException();// ukoncime generovanie nasilne
                }
                paketID++;
                byte[] paket = null;
                // if number of bytes to transfer(header + data) is greater than or equal BYTE_CAPACITY, then create paket with BYTE_CAPACITY
                // else create paket of size HEADER_LENGTH + data.length - offset
                if (data.length - offset + HEADER_LENGTH >= BYTE_CAPACITY) {
                    paket = new byte[BYTE_CAPACITY];
                } else {
                    paket = new byte[Math.max(data.length - offset + HEADER_LENGTH, ColorQRCodeEncoder.MIN_PAKET_SIZE)];
                }
                Log.i(TAG, "paketID: " + paketID + " paket size:: " + paket.length);
                // create header
                if (paketID == 1) {
                    paket[0] = (byte) (paketID >> 8);
                    paket[1] = (byte) (paketID);
                    paket[2] = (byte) (paketsCount >> 8);
                    paket[3] = (byte) (paketsCount);
                    paket[4] = (byte) (FPS);
                    byte[] filenameBytes = loadedFilename.getBytes();
                    System.arraycopy(filenameBytes, 0, paket, HEADER_LENGTH, filenameBytes.length);
                    paket[5 + filenameBytes.length] = 0; // delimeter
                    byte[] chunk = Arrays.copyOfRange(data, offset, offset + paket.length - HEADER_LENGTH - filenameBytes.length - 1);//-1 for delimiter
                    System.arraycopy(chunk, 0, paket, HEADER_LENGTH + filenameBytes.length + 1, chunk.length);
                    offset += chunk.length;
                } else {
                    paket[0] = (byte) (paketID >> 8);
                    paket[1] = (byte) (paketID);
                    paket[2] = (byte) (paketsCount >> 8);
                    paket[3] = (byte) (paketsCount);
                    paket[4] = (byte) (FPS);
                    byte[] chunk = Arrays.copyOfRange(data, offset, offset + paket.length - HEADER_LENGTH);
                    System.arraycopy(chunk, 0, paket, HEADER_LENGTH, chunk.length);
                    offset += chunk.length;
                }

                Bitmap obrazok = qrCodeEncoder.encodeAsBitmap(paket);

                File path = context.getFilesDir();
                File file = new File(path, "gen" + paketID + ".png");

                path.mkdirs();
                FileOutputStream out = new FileOutputStream(file);
                boolean vysledok = obrazok.compress(Bitmap.CompressFormat.PNG, 100, out);
                //System.out.println("uspesne skonvertovalo: " + vysledok);
                out.flush();
                out.close();
                //System.out.println("outputstream closed ");
                Message message = Message.obtain(handler, R.id.qr_img_file, file);
                message.sendToTarget();
            }

        } catch (InterruptedException e) {
            Message message = Message.obtain(handler, R.id.qr_img_generating_stopped, GENERATING_INTERRUPTED);
            message.sendToTarget();
            Log.i(TAG, "generator skonceny prerusenim");
            return;
        } catch (Exception e) {
            Log.i(TAG, "vynimka generatora");
            // TODO: osetrit pripad nedostatku miesta
            e.printStackTrace();
            Message message = Message.obtain(handler, R.id.qr_img_generating_stopped, e.getLocalizedMessage());
            message.sendToTarget();
            Log.i(TAG, "generator skonceny vynimkou");
            return;
        }
        Message message = Message.obtain(handler, R.id.qr_img_generating_stopped, GENERATING_SUCCESSFUL);
        message.sendToTarget();
        Log.i(TAG, "generator skonceny normalne");

    }

}
