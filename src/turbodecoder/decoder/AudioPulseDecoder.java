package turbodecoder.decoder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 *
 * @author  
 */
public class AudioPulseDecoder implements PulseDecoder {

    /**
     *
     */
    public static final int CHANNEL_MONO = 0;

    /**
     *
     */
    public static final int CHANNEL_LEFT = 1;

    /**
     *
     */
    public static final int CHANNEL_RIGHT = 2;

    private TargetDataLine waveLine;
    private long sample;
    private int lastValue;
    private int counter;

    transient private boolean stopRequest;
    private int maxSilence;
    private final byte[] currentByte;

    private int numBytes;
    private int byteIndex;
    private int numBits;
    private int numChannels;

    private byte[] buffer;
    private int bufferPosition;
    private int bufferedCount;


    private int sampleRate;

    /**
     *
     */
    public AudioPulseDecoder() {
        waveLine = null;
        sample = 0L;
        lastValue = 0;
        counter = 0;
        stopRequest = false;
        currentByte = new byte[4];

        numBytes = 1;
        byteIndex = 0;
        bufferedCount = 0;
        bufferPosition = 0;
        numBits = 8;
        numChannels = 1;
    }

    @Override
    public void init(String config, DecoderLog log, Object config2, Object config3) throws Exception {

        /*Handle mono/stereo etc*/
        setupSampleGetterReader(config, (String) config2, (String) config3);

        AudioFormat af = new AudioFormat(sampleRate, numBits, numChannels, numBits == 16, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, af);
        waveLine = (TargetDataLine) AudioSystem.getLine(info);
        waveLine.open(af, waveLine.getBufferSize());
        waveLine.start();

        sample = 0L;
        buffer = new byte[waveLine.getBufferSize()];
        bufferPosition = 0;
        bufferedCount = 0;
        log.addMessage(new DecoderMessage("AudioPulseDecoder", Integer.toString(numBytes) + "/" + Integer.toString(byteIndex) + ":" + Integer.toString(sampleRate), DecoderMessage.SEV_DETAIL), false);
        log.addMessage(new DecoderMessage("AudioPulseDecoder", waveLine.getLineInfo().toString(), DecoderMessage.SEV_DETAIL), false);
    }

    @Override
    public void close(String s) {
        try {
            if (waveLine != null) {
                waveLine.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized boolean getStopRequest() {
        return stopRequest;
    }

    @Override
    public boolean isPositionable() {
        return false;
    }

    @Override
    public void setTimeOut(int s) {
        maxSilence = s;
    }

    @Override
    public synchronized void requestStop(boolean emergency) {
        stopRequest = true;
        if (emergency == true && waveLine != null) {
            waveLine.stop();
        }
        sample = 0L;
    }

    @Override
    public long getCurrentSample() {
        return sample;
    }

    @Override
    public int countUntilAnyEdge() {

        int k;

        while (true) {
            k = getNextSample();
            if (k == PD_EOF || k == PD_USER_BREAK || k == PD_ERROR) {
                return k;
            }

            counter++;
            if (counter > maxSilence) {
                return PD_TOO_LONG;
            }

            if (k != lastValue) {
                lastValue = k;
                return PD_OK;
            }

        }

    }

    @Override
    public int waitForRisingEdge() {
        return waitForSpecificEdge(true,false);
    }
    
     @Override
    public int countUntilRisingEdge() {
       return waitForSpecificEdge(true,true);
    }
    
    @Override
     public int waitForFallingEdge() {
       return waitForSpecificEdge(false,false);
    }
    @Override
     public int countUntilFallingEdge() {
        return waitForSpecificEdge(false,true);
    }
    
    private int waitForSpecificEdge(boolean rising,boolean count) {
        
        lastValue=-1;
        int k;
        final int edgeBefore = rising?0:1;
        final int edgeAfter = rising?1:0;
        
        while (true) {
            k = getNextSample();

            if (k == PD_EOF || k == PD_USER_BREAK || k == PD_ERROR) {
                lastValue=k;
                return k;
            }
            
            if (count==true) {
                counter++;
                if (counter>maxSilence) {
                    lastValue=k;
                    return PD_TOO_LONG;
                }
            }

            if (lastValue == edgeBefore && k == edgeAfter) {
                lastValue = k;
                return PD_OK;
            }

            lastValue = k;
        }
    }

    @Override
    public int measurePulse() {

        int r = countUntilAnyEdge();
        if (r == PD_EOF || r == PD_ERROR || r == PD_USER_BREAK) {
            return r;
        }

        while (true) {
            int k = getNextSample();
            if (k == PD_EOF || k == PD_USER_BREAK || k == PD_ERROR) {
                return k;
            }
            counter++;
            if (counter > maxSilence) {
                return PD_TOO_LONG;
            }
            if (k != lastValue) {
                lastValue = k;
                return PD_OK;
            }

        }

    }

    @Override
    public int getCounter() {
        return counter;
    }

    @Override
    public void setCounter(int c) {
        counter = c;
    }

    @Override
    public void setCurrentSample(int s) {

    }

    @Override
    public long getTotalSamples() {
        return 0L;
    }

    /**
     * Get next sample (0 or 1)
     *
     * @return 0,1 - valid values or USER_BREAK,ERROR,EOF
     *
     */
    private int getNextSample() {

        if (getStopRequest() == true) {
            synchronized(this) {
                stopRequest = false;
            }
            return PD_USER_BREAK;
        }

        int k;

        /*If there is no data in the buffer, read it*/
        if (bufferedCount == 0) {
            try {
                bufferedCount = waveLine.read(buffer, 0, buffer.length);
                bufferPosition = 0;
            } catch (Exception e) {
                e.printStackTrace();
                return PD_ERROR;
            }
        }

        if (bufferedCount > 0) {
            for (int i = 0; i < numBytes; i++) {
                currentByte[i] = buffer[bufferPosition];
                bufferPosition++;
                bufferedCount--;
            }
        }

        sample++;
        if (numBits == 8) {
            k = (currentByte[byteIndex] < 0 ? currentByte[byteIndex] + 256 : currentByte[byteIndex]);
            if (k >= 128) {
                return 1;
            }
            if (k >= 0) {
                return 0;
            }
        } else {
            int frame = (currentByte[byteIndex] & 0xFF) | ((currentByte[byteIndex + 1]) << 8);
            return (frame < 0) ? 0 : 1;
        }

        return PD_EOF;
    }

    /**
     *
     * @param result
     * @return
     */
    @Override
    public String getMessage(int result) {
        switch (result) {
            case PD_EOF:
                return "EOF: End of file reached";
            case PD_TOO_LONG:
                return "ERROR: No pulse found";
            case PD_USER_BREAK:
                return "BREAK: User break";
            case PD_ERROR:
                return "ERROR: I/O Error";
            case PD_OK:
                return "OK: Success";
            case PD_NOT_ONE_NOT_ZERO:
                return "ERROR: Not one, not zero: [" + Integer.toString(counter) + "]";
            case PD_FILE_FORMAT_ERROR:
                return "ERROR: Bad file format";
            default:
                return "CODE: " + Integer.toString(result);
        }
    }

    private void setupSampleGetterReader(String config, String config2, String config3) {

        /*Mono/Stereo*/
        if (config.equals("Mono")) {
            numBytes = 1;
            byteIndex = 0;
            numChannels = 1;
        } /*Left/Right*/ else {
            numBytes = 2;
            numChannels = 2;
            if (config.equals("Left")) {
                byteIndex = 0;
            } else {
                byteIndex = 1;
            }
        }

        if (config3.equals("16")) {
            numBytes *= 2;
            byteIndex *= 2;
            numBits = 16;
        } else {
            numBits = 8;
        }

        try {
            sampleRate = Integer.parseInt(config2);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            sampleRate = 44_100;
        }

    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     *
     * @return
     */
    @Override
    public String getCurrentSampleString() {
        return "{" + sample + "}";
    }

}
