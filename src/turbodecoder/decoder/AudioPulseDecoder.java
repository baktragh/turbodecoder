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

    private int xm1;
    private int ym1;
    private static final double TIME_CONSTANT = 0.995;
    private boolean useDCBlocker;

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
    public void init(String fspec, int samplingRate, int channel, int bitsPerSample, boolean dcBlocker, DecoderLog log) throws Exception {

        /*Handle mono/stereo etc*/
        setupSampleGetterReader(channel, bitsPerSample, samplingRate);

        AudioFormat af = new AudioFormat(sampleRate, numBits, numChannels, numBits == 16, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, af);
        waveLine = (TargetDataLine) AudioSystem.getLine(info);
        waveLine.open(af, waveLine.getBufferSize());
        waveLine.start();

        sample = 0L;
        buffer = new byte[waveLine.getBufferSize()];
        bufferPosition = 0;
        bufferedCount = 0;

        xm1 = 0;
        ym1 = 0;
        useDCBlocker = dcBlocker;

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
        return waitForSpecificEdge(true, false);
    }

    @Override
    public int countUntilRisingEdge() {
        return waitForSpecificEdge(true, true);
    }

    @Override
    public int waitForFallingEdge() {
        return waitForSpecificEdge(false, false);
    }

    @Override
    public int countUntilFallingEdge() {
        return waitForSpecificEdge(false, true);
    }

    private int waitForSpecificEdge(boolean rising, boolean count) {

        lastValue = -1;
        int k;
        final int edgeBefore = rising ? 0 : 1;
        final int edgeAfter = rising ? 1 : 0;

        while (true) {
            k = getNextSample();

            if (k == PD_EOF || k == PD_USER_BREAK || k == PD_ERROR) {
                lastValue = k;
                return k;
            }

            if (count == true) {
                counter++;
                if (counter > maxSilence) {
                    lastValue = k;
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
            synchronized (this) {
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

        int frame;

        if (numBits == 8) {
            frame = (currentByte[byteIndex] < 0 ? currentByte[byteIndex] + 256 : currentByte[byteIndex]);
        } else {
            frame = (currentByte[byteIndex] & 0xFF) | ((currentByte[byteIndex + 1]) << 8);
        }

        if (useDCBlocker == true) {
            int tempFrame = frame - xm1 + (int) Math.round(TIME_CONSTANT * ym1);
            xm1 = frame;
            ym1 = tempFrame;
            frame = tempFrame;
        }

        if (numBits == 8) {
            return (frame >= 128) ? 1 : 0;
        } else {
            return (frame < 0) ? 0 : 1;
        }

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

    private void setupSampleGetterReader(int channel, int bitsPerSample, int samplingRate) {

        /*Mono/Stereo*/
        if (channel == PulseDecoder.CHANNEL_MONO) {
            numBytes = 1;
            byteIndex = 0;
            numChannels = 1;
        } /*Left/Right*/ else {
            numBytes = 2;
            numChannels = 2;
            if (channel == PulseDecoder.CHANNEL_LEFT) {
                byteIndex = 0;
            } else {
                byteIndex = 1;
            }
        }

        if (bitsPerSample == 16) {
            numBytes *= 2;
            byteIndex *= 2;
            numBits = 16;
        } else {
            numBits = 8;
        }

        sampleRate = samplingRate;

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
