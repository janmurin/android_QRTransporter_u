package sk.jmurin.android.qrtransporter.sending;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by jmurin on 28.3.2016.
 */
public class ColorQRCodeEncoder {

    private static final String TAG = "ColorQRCodeEncoder";
    public static final int MARKER_AREA = 64;
    public static final int MARKER_SIZE = 8;
    private static final int CORNER_MARKER_AREA = 4;
    private static final int CORNER_MARKER_SIZE = 2;
    private static final int LENGTH_BYTES = 2;
    private static final int HASH_BYTES = 4;
    private static final int BITS_FOR_ELEMENT = 3;
    private static final int MARGIN_ELEMENTS = 2;// margin is wide 2 elements
    private static final int COLORS_SIZE = 8;
    public static final int MIN_PAKET_SIZE = 10;
    private final int WIDTH;
    private final int HEIGHT;
    private final int[] colors8 = {Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE, Color.rgb(255, 0, 255), Color.rgb(0, 255, 255), Color.WHITE};
    private int elementSize;
    private int byteCapacity;
    private int[][] tmpl;
    private int dataElementsSize; // how much elements can be colored to store data
    private int widthElements;
    private int heightElements;

    public ColorQRCodeEncoder(int width, int height, int minDataSize) {
        this.WIDTH = width;
        this.HEIGHT = height;
        Log.i(TAG, "size of colorQR in px: " + WIDTH + "x" + HEIGHT);
        createTemplate(minDataSize);
    }

    /**
     * calculates:
     * - size of QRcode element in pixels depending on size of bytes to store and image width and height
     * - capacity of code in bytes(at least minDataSize) and number of elements for storing data
     * <p/>
     * initializes int[][] array used as template when encoding data into bitmap
     *
     * @param minDataSize how much minimal data we want to transfer with one qr code
     */
    private void createTemplate(int minDataSize) {
        if (minDataSize < MIN_PAKET_SIZE) {
            throw new RuntimeException("too small data size [" + minDataSize + "], required at least " + MIN_PAKET_SIZE + " bytes.");
        }
        if (minDataSize > 65535 - HASH_BYTES) {// 65535 is largest number that can be stored in 2 bytes
            throw new RuntimeException("too much data for length bytes, required: " + minDataSize + " bytes, " +
                    "available max only: " + (65535 - HASH_BYTES));
        }
        // before actual data adding 2 bytes for length of data and bytes reserved for hash
        minDataSize += LENGTH_BYTES;
        minDataSize += HASH_BYTES;
        // convert required datasize in bytes to number of required qrcode elements
        minDataSize = (int) Math.ceil(minDataSize * 8 / (double) BITS_FOR_ELEMENT);
        // determine elementSize from number of colored elements needed
        elementSize = 1; // px
        int elementsNeeded = MARKER_AREA * 3 + (COLORS_SIZE - 2) + minDataSize + CORNER_MARKER_AREA; // excluding black and white color
        int capacity = getCapacity(elementSize);
        while (elementsNeeded < capacity) {
            elementSize++;
            capacity = getCapacity(elementSize);
        }
        elementSize--;
        capacity = getCapacity(elementSize);
        Log.i(TAG, "elementsNeeded: " + elementsNeeded + " capacity: " + capacity + " elementSize: " + elementSize);
        if (elementSize == 0) {
            throw new RuntimeException("unable to create colorQR: too much data. required " + elementsNeeded + " available: " + capacity);
        }
        capacity = capacity - elementsNeeded + minDataSize; // calculate free elements
        dataElementsSize = capacity; // includes length and hash elements
        // calculate capacity in bytes, how much bytes we can accept into qr code
        this.byteCapacity = capacity * BITS_FOR_ELEMENT / 8 - LENGTH_BYTES - HASH_BYTES;
        Log.i(TAG, "dataElementsSize:" + dataElementsSize + " byteCapacity: " + byteCapacity);

        // elements matrix
        widthElements = (WIDTH - 2 * MARGIN_ELEMENTS * elementSize) / elementSize;
        heightElements = (HEIGHT - 2 * MARGIN_ELEMENTS * elementSize) / elementSize;
        tmpl = new int[widthElements][heightElements];
        Log.i(TAG, "tmpl size:" + tmpl.length + "x" + tmpl[0].length);
        // create markers
        createMarkers(tmpl);
        // set right bottom corner
        tmpl[widthElements - 1][heightElements - 1] = Color.BLACK;
        tmpl[widthElements - 2][heightElements - 1] = Color.BLACK;
        tmpl[widthElements - 1][heightElements - 2] = Color.BLACK;
        tmpl[widthElements - 2][heightElements - 2] = Color.BLACK;
        // set colors on first COLORS_SIZE free elements
        int counter = 1;
        for (int y = 0; y < heightElements; y++) {
            boolean allIncluded = false;
            for (int x = 0; x < widthElements; x++) {
                if ((x < MARKER_SIZE && y < MARKER_SIZE) // skip left top
                        || (x < MARKER_SIZE && y >= heightElements - MARKER_SIZE) // skip left bottom
                        || (x >= widthElements - MARKER_SIZE && y < MARKER_SIZE) // skip right top
                        || (x >= widthElements - CORNER_MARKER_SIZE && y >= heightElements - CORNER_MARKER_SIZE)) { // skip corner bottom
                    continue;
                }
                tmpl[x][y] = colors8[counter];
                counter++;
                if (counter == COLORS_SIZE - 1) {// black skipped and white not included
                    allIncluded = true;
                    break;
                }
            }
            if (allIncluded) {
                break;
            }
        }
    }

    /**
     * @param elementSize
     * @return how many elements for qr code
     */
    private int getCapacity(int elementSize) {
        return ((WIDTH - 2 * MARGIN_ELEMENTS * elementSize) / elementSize) * ((HEIGHT - 2 * MARGIN_ELEMENTS * elementSize) / elementSize);
    }

    public int getByteCapacity() {
        return byteCapacity;
    }

    public Bitmap encodeAsBitmap(byte[] data) {
        try {
            if (data.length != byteCapacity) {
                // probably last chunk with less data than others
                createTemplate(data.length);
            }
            // convert bytes to string of bits
            // initialize with length bits
            String hashBits = String.format("%32s", Integer.toBinaryString(Arrays.hashCode(data))).replace(' ', '0');
            StringBuilder sb = new StringBuilder(String.format("%16s", Integer.toBinaryString((data.length + HASH_BYTES) & 0xFFFF)).replace(' ', '0'));
            for (int i = 0; i < data.length; i++) {
                sb.append(String.format("%8s", Integer.toBinaryString(data[i] & 0xFF)).replace(' ', '0'));
            }
            // append hash
            sb.append(hashBits);
            Log.i(TAG, "data bits: " + sb);
            Log.i(TAG, "hashcode: " + Arrays.hashCode(data));

            String bits = sb.toString();
            // parse bits into colors
            List<Integer> parsedColors = new ArrayList<>();
            int idx = 0;
            while (idx <= bits.length() - BITS_FOR_ELEMENT) {
                parsedColors.add(getColorIdx(bits.substring(idx, idx + BITS_FOR_ELEMENT)));
                idx += BITS_FOR_ELEMENT;
            }
            // add last bits if not modulus with BITS_FOR_ELEMENT
            if (idx != bits.length()) {
                String suffix = "";
                int missingBits = BITS_FOR_ELEMENT - (bits.length() - idx);
                for (int j = 0; j < missingBits; j++) {
                    suffix = suffix + "0";
                }
                parsedColors.add(getColorIdx(bits.substring(idx, bits.length()) + suffix));
            }
            //Log.i(TAG,"parsedColors: "+parsedColors);
            // possible situations:
            //    1. parsedColors.size() == dataElementsSize - all OK
            //    2. parsedColors.size() < dataElementsSize - elements will be added as padding
            //    3. parsedColors.size() > dataElementsSize - impossible exceptional situation, ERROR

            Bitmap image = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);

            if (parsedColors.size() > dataElementsSize) {
                // should never happen, just for debug purposes
                throw new RuntimeException("low capacity: parsedColors.size() > dataElementsSize: " + parsedColors.size() + " > " + dataElementsSize);
            }
            // set data elements
            int counter = 0;
            int colorsSkip = 0;
            for (int y = 0; y < heightElements; y++) {
                boolean allIncluded = false;
                for (int x = 0; x < widthElements; x++) {
                    if ((x < MARKER_SIZE && y < MARKER_SIZE) // skip left top
                            || (x < MARKER_SIZE && y >= heightElements - MARKER_SIZE) // skip left bottom
                            || (x >= widthElements - MARKER_SIZE && y < MARKER_SIZE) // skip right top
                            || (x >= widthElements - CORNER_MARKER_SIZE && y >= heightElements - CORNER_MARKER_SIZE)) { // skip corner bottom
                        continue;
                    }
                    // skipping first positions reserved for colors
                    if (colorsSkip < COLORS_SIZE - 2) { // not including black and white
                        colorsSkip++;
                        tmpl[x][y] = colors8[colorsSkip];
                        continue;
                    }
                    if (counter < parsedColors.size()) {
                        tmpl[x][y] = colors8[parsedColors.get(counter)];
                    } else {
                        tmpl[x][y] = Color.GRAY; // unused elements
                    }
                    counter++;
                    if (counter == dataElementsSize) {
                        // all data elements are set
                        allIncluded = true;
                        break;
                    }
                }
                if (allIncluded) {
                    break;
                }
            }

            // draw code from matrix
            for (int y = 0; y < heightElements; y++) {
                for (int x = 0; x < widthElements; x++) {
                    nakresliStvorcek(
                            image,
                            MARGIN_ELEMENTS * elementSize + x * elementSize,
                            MARGIN_ELEMENTS * elementSize + y * elementSize,
                            tmpl[x][y],
                            elementSize);
                }
            }

            return image;
        } catch (Exception e) {
            System.out.println("problematic data length: " + data.length);
            throw e;
        }

    }


    private void createMarkers(int[][] dm) {
        for (int i = 0; i < 6; i++) {
            // left top
            dm[i][0] = Color.BLACK;
            dm[i + 1][6] = Color.BLACK;
            dm[0][i + 1] = Color.BLACK;
            dm[6][i] = Color.BLACK;
            // right top
            dm[widthElements - 7 + i][0] = Color.BLACK;
            dm[widthElements - 6 + i][6] = Color.BLACK;
            dm[widthElements - 7][i + 1] = Color.BLACK;
            dm[widthElements - 1][i] = Color.BLACK;
            // left bottom
            dm[i][heightElements - 7] = Color.BLACK;
            dm[i + 1][heightElements - 1] = Color.BLACK;
            dm[0][i + heightElements - 6] = Color.BLACK;
            dm[6][i + heightElements - 7] = Color.BLACK;
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                // left top
                dm[2 + i][2 + j] = Color.BLACK;
                // right top
                dm[widthElements - 5 + i][2 + j] = Color.BLACK;
                // left bottom
                dm[2 + i][heightElements - 5 + j] = Color.BLACK;
            }
        }
    }

    private Integer getColorIdx(String substring) {
        if (substring.length() != 3) {
            throw new UnsupportedOperationException("not supported for other bits than 3: [" + substring + "]");
        }
        switch (substring) {
            case "000":
                return 0;
            case "001":
                return 1;
            case "010":
                return 2;
            case "011":
                return 3;
            case "100":
                return 4;
            case "101":
                return 5;
            case "110":
                return 6;
            case "111":
                return 7;
            default:
                throw new RuntimeException("impossible substring");
        }
    }

    private static void nakresliStvorcek(Bitmap obrazok, int x, int y, int farba, int stvorcekSize) {
        // nakreslime stvorcek
        for (int k = 0; k < stvorcekSize; k++) {
            for (int l = 0; l < stvorcekSize; l++) {
                obrazok.setPixel(x + k, y + l, farba);
            }
        }
    }
}
