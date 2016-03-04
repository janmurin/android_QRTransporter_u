package sk.jmurin.android.qrtransporter.deleted;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.File;
import java.util.List;

import sk.jmurin.android.qrtransporter.R;
import sk.jmurin.android.qrtransporter.sending.Constants;
import sk.jmurin.android.qrtransporter.sending.Shared;

/**
 * Created by Janco1 on 2. 11. 2015.
 */
public class Animator implements Runnable {

    private final List<File> qrImgFiles;
    private final Handler handler;
    private final int FPS;
    private final BitmapFactory.Options options;
    private int counter;

    public Animator(List<File> qrImgFiles, Handler animatorResultHandler, int fps) {
        this.qrImgFiles = qrImgFiles;
        this.handler = animatorResultHandler;
        this.FPS = fps;
        options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_4444;
        System.out.println("startujem animatora");
        Shared.lastTimePictureChanged.set(System.currentTimeMillis());
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            if (counter == qrImgFiles.size()) {
                counter = 0;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(qrImgFiles.get(counter).getAbsolutePath(), options);
            Message message = Message.obtain(handler, R.id.animate_new_qr, bitmap);
            Bundle args = new Bundle();
            args.putCharSequence(Constants.ODOSLAT_STATUS, (counter + 1) + "/" + qrImgFiles.size());
            message.setData(args);


            try {
                int cakat = (int) ((1000 / FPS) - (System.currentTimeMillis() - Shared.lastTimePictureChanged.get()));
                System.out.print("celkovo cakat: "+(1000/FPS)+", preslo od poslednej: "+(System.currentTimeMillis() - Shared.lastTimePictureChanged.get())+"" +
                        " este cakat:  " + cakat + " ms");
                cakat = Math.max(cakat, 0);
                System.out.println("cakam: " + cakat);
                Thread.sleep(cakat);
                message.sendToTarget();
                System.out.println("obrazok poslany");

            } catch (InterruptedException e) {
                //e.printStackTrace();
                break;
            }
            counter++;
        }
        System.out.println("animator skonceny");
    }
}
