package turbodecoder.decoder.pulse;

import java.io.*;
import turbodecoder.FileFormatException;
import turbodecoder.decoder.DecoderLog;
import turbodecoder.decoder.DecoderMessage;
import turbodecoder.decoder.PulseDecoder;
import turbodecoder.decoder.dsp.DCBlocker;

/**
 *
 * @author
 */
public class WavePulseDecoder implements PulseDecoder {

    private static final int BUF_SIZE = 32_768;

    private RandomAccessFile waveFile;
    private long sample;
    private int lastValue;
    private int counter;
    private int offset;
    transient private boolean stopRequest;
    private int maxSilence;
    private int frameSize;
    private int byteIndex;
    private final byte[] currentByte;
    private int bufAvail;
    private int bufPointer;
    private byte[] buffer;

    private int bytesPerSample;
    private int numChannels;
    private int sampleRate;
    private long totalSamples;
    private boolean pastEOF;

    private static final double TIME_CONSTANT = 0.995;
    private boolean useDCBlocker;
    private final DCBlocker dcBlocker;

    /**
     *
     */
    public WavePulseDecoder() {
        waveFile = null;
        sample = 0L;
        lastValue = 0;
        counter = 0;
        stopRequest = false;
        currentByte = new byte[4];
        pastEOF = false;
        useDCBlocker = false;
        dcBlocker = new DCBlocker(TIME_CONSTANT);
    }

    @Override
    public void init(String fspec, int samplingRate, int channel, int bitsPerSample, boolean useDCBlocker, DecoderLog log) throws Exception {

        /*Open and examine file*/
        waveFile = new RandomAccessFile(fspec, "r");
        examineFormat();

        /*Now we know how many channels and how many bits per sample*/
        frameSize = numChannels * bytesPerSample;

        /*Index in frame*/
        if (numChannels == 1) {
            byteIndex = 0;
        } else /*Second channel?*/ if (channel == PulseDecoder.CHANNEL_RIGHT) {
            byteIndex = bytesPerSample;
        } else {
            byteIndex = 0;
        }

        rewind();
        
        /*Initialize buffering*/
        buffer = new byte[BUF_SIZE];
        bufAvail = 0;
        bufPointer = 0;
        
        /*Initialize DSPs*/
        this.useDCBlocker = useDCBlocker;
        dcBlocker.reset();
        
        StringBuilder dspList = new StringBuilder();
        if (useDCBlocker) dspList.append("DC Blocker,");
        
        log.addMessage(new DecoderMessage("WavePulseDecoder",
                String.format("%s FrameSize: %02d, ByteIndex: %d, SamplingRate: %d, DSP: %s",
                        fspec, frameSize, byteIndex, sampleRate,dspList.toString()),
                DecoderMessage.SEV_DETAIL), false);
        
        

    }

    @Override
    public void close(String s) {
        try {
            if (waveFile != null) {
                waveFile.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized boolean getStopRequest() {
        return stopRequest;
    }

    @Override
    public boolean isPositionable() {
        return true;
    }

    @Override
    public void setTimeOut(int s) {
        maxSilence = s;
    }

    @Override
    public synchronized void requestStop(boolean emergency) {
        stopRequest = true;
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
                return k;
            }

            if (count == true) {
                counter++;
                if (counter > maxSilence) {
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
        if (waveFile == null) {
            return;
        }
        try {
            waveFile.seek(offset + (s * frameSize));
            sample = s;
            bufAvail = 0;
            bufPointer = 0;
            pastEOF = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getTotalSamples() {
        if (waveFile == null) {
            return 0L;
        }
        return totalSamples;
    }

    private void rewind() throws Exception {
        sample = 0L;
        waveFile.seek(offset);
        pastEOF = false;
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

        if (pastEOF == true) {
            return PD_EOF;
        }

        int frame;

        try {

            /*If nothing in the buffer, read up to BUF_SIZE bytes*/
            if (bufAvail == 0) {
                bufAvail = waveFile.read(buffer, 0, BUF_SIZE);
                bufPointer = 0;

                /*If just reached EOF, consider it logical value change*/
                if (bufAvail == -1) {
                    pastEOF = true;
                    return lastValue == 1 ? 0 : 1;
                }
            }

            /*Get frame from the buffer*/
            for (int i = 0; i < frameSize; i++) {
                currentByte[i] = buffer[bufPointer];
                bufPointer++;
                bufAvail--;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return PD_ERROR;
        }

        sample++;
        if (bytesPerSample == 1) {
            frame = (currentByte[byteIndex] < 0 ? currentByte[byteIndex] + 256 : currentByte[byteIndex]);
        } else {
            frame = (currentByte[byteIndex] & 0xFF) | ((currentByte[byteIndex + 1]) << 8);
        }

        if (useDCBlocker == true) {
            frame = dcBlocker.getOutputValue(frame);
        }

        if (bytesPerSample == 1) {
            if (frame >= 128) {
                return 1;
            } else {
                return 0;
            }
        } else {
            return (frame < 0) ? 0 : 1;
        }

    }

    private void examineFormat() throws Exception {

        /*Check for RIFF header*/
        int[] hdr = read4Bytes(waveFile);
        if (hdr == null) {
            throw new FileFormatException("End of WAVE file reached unexpectedly");
        }
        if (hdr[0] != 'R' || hdr[1] != 'I' || hdr[2] != 'F' || hdr[3] != 'F') {
            throw new FileFormatException("The 'RIFF' chunk was not found in the WAVE file");
        }

        /* WAVE chunk present ?*/
        waveFile.seek(8L);
        hdr = read4Bytes(waveFile);
        if (hdr == null) {
            throw new FileFormatException("End of WAVE file reached unexpectedly");
        }
        if (hdr[0] != 'W' || hdr[1] != 'A' || hdr[2] != 'V' || hdr[3] != 'E') {
            throw new FileFormatException("The 'WAVE' chunk was not found in the WAVE file");
        }

        /*Find the 'fmt ' chunk*/
        long ckLen = seekForChunk("fmt ", waveFile);
        if (ckLen == Long.MAX_VALUE) {
            throw new FileFormatException("The 'fmt ' chunk was not found in the WAVE file");
        }

        /*Now we read whole fmt chunk (if it is not too big)*/
        if (ckLen > 40) {
            throw new FileFormatException("The 'fmt ' chunk in the WAVE file has excessive size");
        }
        int[] fmtChunk = new int[(int) ckLen];

        for (int i = 0; i < ckLen; i++) {
            int b = waveFile.read();
            if (b == -1) {
                throw new FileFormatException("The 'fmt ' chunk in the WAVE file is truncated");
            }
            fmtChunk[i] = b;
        }

        /*Check format code*/
        if (!(fmtChunk[0] == 1 && fmtChunk[1] == 0)) {
            throw new FileFormatException("Audio format specified in the 'fmt ' chunk is not supported");
        }

        /*Determine sample rate, sampe and frame format, number of channels*/
        boolean b = true;
        /*Number of channels*/
 /*Check HI byte*/
        if (fmtChunk[3] != 0) {
            b = false;
        } /*Check LO byte for 1 or 2 channels*/ else {
            switch (fmtChunk[2]) {
                case 1: {
                    numChannels = 1;
                    break;
                }
                case 2: {
                    numChannels = 2;
                    break;
                }
                default: {
                    b = false;
                }

            }
        }
        /*Sample rate*/
        long lRate = fmtChunk[4] + 256 * fmtChunk[5] + 65_536 * fmtChunk[6] + (65_536 * 256) * fmtChunk[7];
        if (lRate < 44_100 || lRate > 705_600) {
            throw new FileFormatException("Wave file sample rate is not between 44100 and 705600 Hz");
        }
        sampleRate = (int) lRate;

        /*Bytes per sample*/
        b = false;
        if (fmtChunk[14] == 8 && fmtChunk[15] == 0) {
            b = true;
            bytesPerSample = 1;
        }

        if (fmtChunk[14] == 16 && fmtChunk[15] == 0) {
            b = true;
            bytesPerSample = 2;
        }
        if (b == false) {
            throw new FileFormatException("Wave file data format is not 8-bit or 16-bit,1 or 2 channels");
        }

        ckLen = seekForChunk("data", waveFile);
        if (ckLen == Long.MAX_VALUE) {
            throw new FileFormatException("No 'data' chunk was found in the WAVE file");
        }
        offset = (int) waveFile.getFilePointer();
        totalSamples = ckLen / (bytesPerSample * numChannels);
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

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Read four bytes from the WAVE file
     */
    private int[] read4Bytes(RandomAccessFile wFile) throws Exception {

        int[] retVal = new int[4];

        for (int i = 0; i < 4; i++) {
            int b = wFile.read();
            if (b == -1) {
                return null;
            }
            retVal[i] = b;
        }
        return retVal;

    }

    private long getUnsignedFullWord(RandomAccessFile wFile) throws Exception {
        int[] fWord = read4Bytes(wFile);
        if (fWord == null) {
            return Long.MAX_VALUE;
        }
        long l = (fWord[0]) + (fWord[1] << 8) + (fWord[2] << 16) + (fWord[3] << 24);
        return l;
    }

    /**
     * Seek for given chunk
     *
     * @param header
     * @return Length of the chunk. -1 For EOF
     * @throws Exception
     */
    private long seekForChunk(String chunkName, RandomAccessFile wFile) throws Exception {

        long ckLen;

        while (true) {

            /*Try to read header*/
            int[] hdr = read4Bytes(waveFile);
            if (hdr == null) {
                return Long.MAX_VALUE;
            }
            /*Try to read length*/
            ckLen = getUnsignedFullWord(wFile);
            if (ckLen == Long.MAX_VALUE) {
                return ckLen;
            }

            /*Check whether the chunk string matches*/
            boolean match = true;
            for (int i = 0; i < 4; i++) {
                if (hdr[i] != chunkName.charAt(i)) {
                    match = false;
                    break;
                }
            }

            if (match == false) {
                wFile.skipBytes((int) ckLen);
            } else {
                break;
            }
        }
        return ckLen;

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
