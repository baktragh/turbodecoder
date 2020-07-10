package turbodecoder;

/**
 * Implementations of this interface are capable to handle log
 */
public interface Logger {

    /**
     *
     * @param s Message string
     * @param fromExtThread Called from external thread
     */
    public void addMessage(String s, boolean fromExtThread);

    /**
     * Clear the log
     */
    public void clear();

    /**
     * Add impulse (work is being done, but no completion rate can be calculated
     * @param fromExtThread
     */
    public void impulse(boolean fromExtThread);

}
