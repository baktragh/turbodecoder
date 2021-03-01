package turbodecoder.decoder;

/**
 *
 * @author  
 */
public class DecoderMessage {

    /**
     *
     */
    public static final int SEV_INFO = 0;

    /**
     *
     */
    public static final int SEV_ERROR = 1;

    /**
     *
     */
    public static final int SEV_WARNING = 2;

    /**
     *
     */
    public static final int SEV_DETAIL = 3;

    /**
     *
     */
    public static final int SEV_SAVE = 4;

    private final int severity;
    private final String messageText;

    public DecoderMessage(String prefix, String message, int severity) {
        messageText = "[" + prefix + "] " + message;
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    public String getMessage() {
        return messageText;
    }

}
