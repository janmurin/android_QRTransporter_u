package sk.jmurin.android.qrtransporter.deleted;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Janco1 on 12. 2. 2016.
 */
public class Smetisko {

//    private void skontrolujCitanieZKodu() {
//        System.out.println("zacinam kontrolovanie kodu");
//        int rovnakych = 0;
//        List<Integer> zleData = new ArrayList<>();
//        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Transporter");
//        File[] list = file.listFiles();
//        System.out.println("zoznam suborov: " + Arrays.toString(file.list()));
//        //return;
//
//        //for (int i = 0; i < qrImgFiles.size(); i++) {
//        for (int i = 0; i < list.length; i++) {
//            try {
//                System.out.println("=========================================================");
//                //String nacitane = readQRCode(qrImgFiles.get(i).getAbsolutePath(),"obr"+i);
//                String nacitane = readQRCode(list[i].getAbsolutePath(),"obr"+i);
//                if (nacitane != null) {
//                    System.out.println("nacitane " + i + ": " + nacitane.replaceAll("\n", ""));
//                    String data = nacitane.substring(nacitane.indexOf('#') + 1);
//                    if (!data.equals(casti[i])) {
//                        zleData.add(i);
//                        System.out.println("NEROVNAJU SA DATA:");
//                        System.out.println("casti[" + i + "]: " + casti[i].replaceAll("\n", ""));
//                        System.out.println("nacitane[" + i + "]: " + nacitane.replaceAll("\n", ""));
//                    } else {
//                        System.out.println("ROVNAJU SA DATA");
//                        rovnakych++;
//                    }
//                } else {
//                    System.out.println("nacitane " + i + ": null");
//                    System.out.println("casti[" + i + "]: " + casti[i].replaceAll("\n", ""));
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        System.out.println("rovnakych dat: " + rovnakych + "/" + qrImgFiles.size());
//        System.out.println("chybne data: " + zleData);
//    }
//
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
//
//    private String readQRCode(String filePath, String name)
//            throws IOException, Resources.NotFoundException {
//        bitmap = BitmapFactory.decodeFile(filePath, new BitmapFactory.Options());
//
//        width = bitmap.getWidth();
//        height = bitmap.getHeight();
//        pixels = new int[width * height];
//        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
//        bitmap.recycle();
//        //bitmap = null;
//        source = new RGBLuminanceSource(width, height, pixels);
//        bBitmap = new BinaryBitmap(new HybridBinarizer(source));
//        reader = new MultiFormatReader();
//        try {
//            Result result = reader.decode(bBitmap);
////            if(result.getText()==null || result.getText().length()<50){
////                ulozNeprecitanyObrazok(bitmap,name);
////            }
//            return result.getText();
//        } catch (NotFoundException e) {
//            Log.e("tag", "decode exception", e);
//            return null;
//        }
//
////        BinaryBitmap binaryBitmap= new BinaryBitmap();
////        Result qrCodeResult = new MultiFormatReader().decode(bitmap, hintMap);
////        return qrCodeResult.getText();
//    }
}
