package turbodecoder;

/**
 *
 * @author  
 */
public class FileFormatException extends Exception {

    private final String message;

    /**
     *
     * @param msg
     */
    public FileFormatException(String msg) {
        message = msg;
    }

    /**
     *
     * @return
     */
    @Override
    public String getMessage() {
        if (message != null) {
            return message;
        } else {
            return "No message";
        }
    }

}
