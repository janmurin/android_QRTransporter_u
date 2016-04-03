package sk.jmurin.android.qrtransporter.decoding;

/**
 * Created by jmurin on 22.3.2016.
 */
public class DataComparator implements java.util.Comparator<String> {
    @Override
    public int compare(String st1, String st2) {
        int id1 = Integer.parseInt(st1.substring(0, st1.indexOf('/')));
        int id2 = Integer.parseInt(st2.substring(0, st2.indexOf('/')));

        return Integer.compare(id1, id2);
    }
}
