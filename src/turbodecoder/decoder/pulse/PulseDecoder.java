package turbodecoder.decoder.pulse;

import turbodecoder.decoder.DecoderLog;

/**
 * Pulse decoder
 */
public interface PulseDecoder {

    /**
     *
     */
    public static final int PD_FILE_FORMAT_ERROR = -8;

    /**
     *
     */
    public static final int PD_UNEXPECTED_PULSE_WIDTH = -7;

    /**
     *
     */
    public static final int PD_NOT_ONE_NOT_ZERO = -6;

    /**
     *
     */
    public static final int PD_TOO_LONG = -5;

    /**
     *
     */
    public static final int PD_EOF = -4;

    /**
     *
     */
    public static final int PD_ERROR = -3;

    /**
     *
     */
    public static final int PD_USER_BREAK = -2;

    /**
     *
     */
    public static final int PD_OK = 0;
    
    
    public static final int CHANNEL_MONO=0;
    public static final int CHANNEL_LEFT=1;
    public static final int CHANNEL_RIGHT=2;

    /**
     * Initialize pulse decoder
     * @throws java.lang.Exception
     */
    public void init(String fspec,int samplingRate, int channel, int bitsPerSample,boolean dcBlocker,int tolerance,DecoderLog log) throws Exception;

    /**
     * Close pulse decoder
     * @throws java.lang.Exception
     */
    public void close(String config) throws Exception;

    /**
     * Is this decoder positionable
     *
     * @return true if position can be set
     */
    public boolean isPositionable();

    /**
     * Set current sample
     * @param s
     */
    public void setCurrentSample(int s);

    /**
     * Get current sample
     * @return 
     */
    public long getCurrentSample();

    /**
     *
     * @return
     */
    public String getCurrentSampleString();

    /**
     * Set sample counter
     * @param c
     */
    public void setCounter(int c);

    /**
     * Get sample counter
     * @return 
     */
    public int getCounter();

    /**
     * Wait for edge
     * @return 
     */
    public int countUntilAnyEdge();

    /**
     * Wait for raising edge, no limit
     * @return 
     */
    public int waitForRisingEdge();
    
    /**
     * Wait for falling edge, no limit
     * @return 
     */
    public int waitForFallingEdge();
    
    /**
     * Wait for rising edge, limited by max silence
     * @return 
     */
    public int countUntilRisingEdge();
    
    /**
     * Wait for falling edge, limited by max silence
     * @return 
     */
    public int countUntilFallingEdge();
    
    

    /**
     * Measure width of pulse
     * @return 
     */
    public int measurePulse();

    /**
     * Set maximum samples until timeout (no edge, no pulse)
     * @param s 
     */
    public void setTimeOut(int s);

    /**
     * Request stop
     *
     * @param emergency Indicates emergency stop
     */
    public void requestStop(boolean emergency);

    /**
     * Get total number of samples
     * @return 
     */
    public long getTotalSamples();

    /**
     *
     * @param code
     * @return
     */
    public String getMessage(int code);

    /**
     * Get sample rate
     * @return 
     */
    public int getSampleRate();

}
