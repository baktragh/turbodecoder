package turbodecoder;

/**
 *
 * @author  
 */
public class ConfigurationException extends Exception {

    String msg = null;

    /**
     *
     * @param msg
     */
    public ConfigurationException(String msg) {
        this.msg = msg;
    }

    /**
     *
     * @return
     */
    @Override
    public String getMessage() {
        return this.msg;
    }

}
