package turbodecoder;

/**
 *
 * @author  
 */
public interface UIPersistor {

    /**
     *
     * @return
     */
    Object getPersistenceData();

    /**
     *
     * @param data
     * @throws Exception
     */
    void setPersistenceData(Object data) throws Exception;

    /**
     *
     * @return
     */
    String getPersistenceId();

    /**
     *
     */
    void setPersistenceDefaults();
}
