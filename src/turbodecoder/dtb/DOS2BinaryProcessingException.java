package turbodecoder.dtb;

/**
 * DOS2 Binary processing exception
 */
public class DOS2BinaryProcessingException extends Exception {

    /**
     * Message
     */
    private final String message;

    /**
     *
     * @param message
     */
    public DOS2BinaryProcessingException(String message) {
        this.message = message;

    }

    /**
     * Get message
     *
     * @return Message
     */
    @Override
    public String getMessage() {
        return getMessageString();
    }

    /**
     *
     * @return
     */
    @Override
    public String toString() {
        return getClass().getName() + " " + getMessageString();
    }

    private String getMessageString() {
        return message;
    }
}
