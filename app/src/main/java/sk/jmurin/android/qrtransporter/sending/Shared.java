package sk.jmurin.android.qrtransporter.sending;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Janco1 on 13. 2. 2016.
 */
public class Shared {
    public static AtomicLong lastTimePictureChanged = new AtomicLong(0);
}
