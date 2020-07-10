package turbodecoder.dtb;

import turbodecoder.decoder.FileDataCache;

/**
 * The DOS 2 binary stream reader reads DOS 2 Binary file byte after byte
 */
public class DOS2BinaryStreamReader {

    /*External states*/

    /**
     *
     */

    public static final int READING = 0;

    /**
     *
     */
    public static final int HEADER_READ = 1;

    /**
     *
     */
    public static final int SEGHEADER_READ = 2;

    /**
     *
     */
    public static final int DATA_SEGMENT_READ = 3;

    /**
     *
     */
    public static final int INIT_SEGMENT_READ = 4;

    /**
     *
     */
    public static final int RUN_SEGMENT_READ = 5;

    /**
     *
     */
    public static final int RUNINIT_SEGMENT_READ = 6;

    /*External error states*/

    /**
     *
     */

    public static final int ERROR_NO_HEADER = -1;

    /**
     *
     */
    public static final int ERROR_BAD_SEGHEADER = -2;

    /**
     *
     */
    public static final int ERROR_IN_ERROR = -128;

    /*Internal states*/
    private static final int STATE_INITIAL_HEADER_1 = 0;
    private static final int STATE_INITIAL_HEADER_2 = 1;

    private static final int STATE_SEGHEADER_1 = 2;
    private static final int STATE_SEGHEADER_2 = 3;
    private static final int STATE_SEGHEADER_3 = 4;
    private static final int STATE_SEGHEADER_4 = 5;
    private static final int STATE_SEGDATA = 6;

    private static final int STATE_ERROR = 128;

    private final FileDataCache segmentCache;
    private final FileDataCache dataCache;
    private int segHeaderFirstLo;
    private int segHeaderFirstHi;
    private int segHeaderLastLo;
    private int segHeaderLastHi;
    private int segFirst;
    private int segLast;

    private int state;
    private int segDataLength;

    /**
     * Create new instance
     */
    public DOS2BinaryStreamReader() {
        state = STATE_INITIAL_HEADER_1;
        dataCache = new FileDataCache(131_072);
        segmentCache = new FileDataCache(65_535);
    }

    /**
     * Get all data currently read
     *
     * @return Data of the binary file
     */
    public int[] getData() {
        return dataCache.getBytes();
    }

    /**
     * Read next byte
     *
     * @param b Byte
     * @return Negative value - error. Positive value - external status
     * @throws turbodecoder.dtb.DOS2BinaryProcessingException
     */
    public int readNextByte(int b) throws DOS2BinaryProcessingException {

        /*Error state*/
        if (state == STATE_ERROR) {
            return ERROR_IN_ERROR;
        }

        /*Initial header - first 255*/
        if (state == STATE_INITIAL_HEADER_1) {
            if (b != 255) {
                state = STATE_ERROR;
                return ERROR_NO_HEADER;
            }
            state = STATE_INITIAL_HEADER_2;
            return READING;
        }

        /*Initial header - second 255*/
        if (state == STATE_INITIAL_HEADER_2) {
            /* No other 255?*/
            if (b != 255) {
                state = STATE_ERROR;
                return ERROR_NO_HEADER;
            }
            /*Otherwise add to the data cache*/
            dataCache.add(255);
            dataCache.add(255);
            state = STATE_SEGHEADER_1;
            return HEADER_READ;
        }

        /*Segment header - first byte*/
        if (state == STATE_SEGHEADER_1) {
            segHeaderFirstLo = b;
            state = STATE_SEGHEADER_2;
            return READING;
        }
        /*Segment header - second byte*/
        if (state == STATE_SEGHEADER_2) {
            segHeaderFirstHi = b;
            /*Check for 255 255*/
            if (segHeaderFirstLo == 255 && segHeaderFirstHi == 255) {
                state = STATE_SEGHEADER_1;
                return HEADER_READ;
            }
            /*Otherwise just continue*/
            state = STATE_SEGHEADER_3;
            return READING;
        }
        /*Segment header - third byte*/
        if (state == STATE_SEGHEADER_3) {
            segHeaderLastLo = b;
            state = STATE_SEGHEADER_4;
            return READING;
        }

        /*Segment header - fourth byte*/
        if (state == STATE_SEGHEADER_4) {
            segHeaderLastHi = b;

            /*Check if the header is OK*/
            segFirst = 256 * segHeaderFirstHi + segHeaderFirstLo;
            segLast = 256 * segHeaderLastHi + segHeaderLastLo;

            /*Negative segment size*/
            if (segLast < segFirst) {
                state = STATE_ERROR;
                return ERROR_BAD_SEGHEADER;
            }
            /*Add header to the data cache*/
            dataCache.add(segHeaderFirstLo);
            dataCache.add(segHeaderFirstHi);
            dataCache.add(segHeaderLastLo);
            dataCache.add(segHeaderFirstHi);
            state = STATE_SEGDATA;
            segDataLength = segLast - segFirst + 1;
        }

        /*Segment data*/
        if (state == STATE_SEGDATA) {
            /*Add to the cache*/
            segmentCache.add(b);
            /*Decrease number of bytes to go*/
            segDataLength--;

            /*If still bytes to go, just continue reading*/
            if (segDataLength > 0) {
                return READING;
            }

            /*We expect segment header*/
            state = STATE_SEGHEADER_1;

            /*We add all segment data to the data cache*/
            dataCache.add(segmentCache.getBytes());
            segmentCache.reset();

            /*We report type of the segment*/
            if (segFirst == 736 && segLast == 737) {
                return RUN_SEGMENT_READ;
            }
            if (segFirst == 738 && segLast == 739) {
                return INIT_SEGMENT_READ;
            }
            if (segFirst == 736 && segLast == 739) {
                return RUNINIT_SEGMENT_READ;
            }
            return DATA_SEGMENT_READ;

        }

        /*Undetermined state - We throw exception*/
        throw new DOS2BinaryProcessingException("Invalid Stream Reader State");

    }

}
