package turbodecoder.decoder;

/**
 * A crate that encapsulates results of turbo block decoding
 */
public class BlockDecodeResult {

    /*Possible results of decoding of block*/

    /**
     *
     */
    public static final int OK = PulseDecoder.PD_OK;

    /**
     *
     */
    public static final int EOF = PulseDecoder.PD_EOF;

    /**
     *
     */
    public static final int ERROR = PulseDecoder.PD_ERROR;

    /**
     *
     */
    public static final int NOT_ONE_NOT_ZERO = PulseDecoder.PD_NOT_ONE_NOT_ZERO;

    /**
     *
     */
    public static final int UNEXPECTED_PULSE_WIDTH = PulseDecoder.PD_UNEXPECTED_PULSE_WIDTH;

    /**
     *
     */
    public static final int TOO_LONG = PulseDecoder.PD_TOO_LONG;

    /**
     *
     */
    public static final int USER_BREAK = PulseDecoder.PD_USER_BREAK;

    /**
     *
     */
    public static final int FILE_FORMAT_ERROR = PulseDecoder.PD_FILE_FORMAT_ERROR;

    /**
     *
     */
    public static final int OK_CHSUM_WARNING = -101;

    /**
     *
     */
    public static final int BLOCK_TOO_SHORT = -102;

    /**
     *
     */
    public static final int BAD_CHSUM = -103;

    /**
     *
     * @param code
     * @return
     */
    public static boolean isCodeImmediateBreak(int code) {
        if (code == EOF || code == USER_BREAK || code == ERROR) {
            return true;
        }
        return false;
    }

    /**
     *
     * @param code
     * @return
     */
    public static boolean isCodePhysicalError(int code) {
        if (code == TOO_LONG || code == NOT_ONE_NOT_ZERO || code == UNEXPECTED_PULSE_WIDTH || code == BLOCK_TOO_SHORT || code == BAD_CHSUM) {
            return true;
        }
        return false;
    }

    /**
     *
     * @param code
     * @return
     */
    public static boolean isCodeOK(int code) {
        if (code == OK || code == OK_CHSUM_WARNING) {
            return true;
        }
        return false;
    }

    /**
     *
     * @param code
     * @return
     */
    public static boolean isCodePerfect(int code) {
        if (code == OK) {
            return true;
        } else {
            return false;
        }
    }

    /**
     *
     * @param code
     * @return
     */
    public static boolean isCodeLogicalError(int code) {
        if (code == BAD_CHSUM) {
            return true;
        } else {
            return false;
        }
    }

    /**
     *
     * @param code
     * @return
     */
    public static String getErrorMessage(int code) {
        switch (code) {
            case OK_CHSUM_WARNING:
                return "WARNING: Bad checksum";
            case BLOCK_TOO_SHORT:
                return "ERROR: Block too short";
            case BAD_CHSUM:
                return "ERROR: Bad checksum";
            case FILE_FORMAT_ERROR:
                return "File format error";
                
            default:
                return "CODE: " + Integer.toString(code);
        }
    }

    /**
     * Data decoded
     */
    private int[] blockData;
    /**
     * Error code
     */
    private int errorCode;
    /**
     * Error message
     */
    private String errorMessage;
    /**
     * Sample
     */
    private long sample;
    /**
     * Valid bytes read
     */
    private int validBytes;
    /**
     * Auxiliary byte
     */
    private int aux;

    BlockDecodeResult(int[] data, int validBytes, int errCode, long smp, PulseDecoder pd) {
        this(data, validBytes, errCode, smp, pd, 0);
    }

    BlockDecodeResult(int[] data, int validBytes, int errCode, long smp, PulseDecoder pd, int aux) {
        blockData = data;
        errorCode = errCode;
        this.validBytes = validBytes;
        this.aux = aux;

        if (errCode < -100) {
            errorMessage = getErrorMessage(errCode);
        } else {
            errorMessage = pd.getMessage(errCode);
        }

        sample = smp;
    }

    int[] getData() {
        return blockData;
    }

    int getErrorCode() {
        return errorCode;
    }

    String getErrorMessage() {
        if (errorMessage == null) {
            return "";
        } else {
            return errorMessage;
        }
    }

    long getSample() {
        return sample;
    }

    String getFullErrorMessage() {
        return getErrorMessage() + " {" + Long.toString(sample) + "}";
    }

    /**
     *
     * @return
     */
    public int getCodeSeverity() {
        if (isCodePerfect(errorCode)) {
            return DecoderMessage.SEV_INFO;
        }
        if (isCodeOK(errorCode)) {
            return DecoderMessage.SEV_WARNING;
        }
        return DecoderMessage.SEV_ERROR;
    }

    /**
     *
     * @return
     */
    public int getValidBytes() {
        return validBytes;
    }

    /**
     *
     * @param validBytes
     */
    public void setValidBytes(int validBytes) {
        this.validBytes = validBytes;
    }

    /**
     * @return the aux
     */
    public int getAux() {
        return aux;
    }

}
