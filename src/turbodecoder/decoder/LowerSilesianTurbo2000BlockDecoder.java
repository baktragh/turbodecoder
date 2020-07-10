package turbodecoder.decoder;

import java.io.IOException;
import turbodecoder.Utils;
import turbodecoder.dtb.DOS2Binary;
import turbodecoder.dtb.DOS2BinaryException;

/**
 *
 * @author  
 */
public class LowerSilesianTurbo2000BlockDecoder implements BlockDecoder, BlockDecoderListener {

    /**
     *
     */
    public static final int MANGLE_NONE = 0;

    /**
     *
     */
    public static final int MANGLE_FUNNY_COPY_10 = 1;

    /*Pulses*/
    private int MAX_PULSE = 50;
    private int PILOT_HI = 48;
    private int PILOT_LO = 32;
    private int WIDE_HI = 31;
    private int WIDE_LO = 19;
    private int NARROW_LO = 6;
    private int SYNC_HI = 24;
    private int SYNC_LO = 6;

    private final PulseDecoder decoder;
    private final DecoderConfig config;
    private int validBytes;
    private BlockDecoderListener blockDecoderListener = null;
    private final int minPilotTonePulses;
    private final int mangleType;


    LowerSilesianTurbo2000BlockDecoder(PulseDecoder decoder, DecoderConfig config, int minPiloTonePulses, int mangleType) {

        this.decoder = decoder;
        this.config = config;
        recalculatePulses(decoder.getSampleRate());
        this.minPilotTonePulses = minPiloTonePulses;
        this.mangleType = mangleType;

    }

    /**
     *
     * @param dataLength
     * @return
     */
    @Override
    public BlockDecodeResult decodeBlock(int dataLength) {
        return decodeBlock(dataLength, null);
    }

    @Override
    public BlockDecodeResult decodeBlock(int dataLength, Object constraint) {

        int r;
        int data[] = new int[dataLength];
        validBytes = 0;

        if (blockDecoderListener == null) {
            blockDecoderListener = this;
        }

        /*Verbose - Display decoder settings*/
 /*Verbose - Display decoder settings*/
        if (config.genVerboseMessages) {
            blockDecoderListener.blockDecodeEvent(
                    String.format("Pulse ranges: SL %d,SH %d,NL %d,WL %d, WH %d,PL %d, PH %d, MP %d",
                            SYNC_LO,
                            SYNC_HI,
                            NARROW_LO,
                            WIDE_LO,
                            WIDE_HI,
                            PILOT_LO,
                            PILOT_HI,
                            MAX_PULSE
                    ));
        }

        while (true) {


            /*Wait for pilot tone*/
            r = waitForPilot();
            if (r == PulseDecoder.PD_ERROR || r == PulseDecoder.PD_EOF || r == PulseDecoder.PD_USER_BREAK) {
                return new BlockDecodeResult(null, validBytes, r, decoder.getCurrentSample(), decoder);
            }
            if (r != PulseDecoder.PD_OK) {
                continue;
            }

            /*Verbose - Pilot Tone*/
            if (config.genVerboseMessages) {
                blockDecoderListener.blockDecodeEvent("Pilot tone found " + decoder.getCurrentSampleString());
            }


            /*Wait for sync pulse*/
            r = waitForSync();
            if (r == PulseDecoder.PD_ERROR || r == PulseDecoder.PD_EOF || r == PulseDecoder.PD_USER_BREAK) {
                return new BlockDecodeResult(null, validBytes, r, decoder.getCurrentSample(), decoder);
            }
            if (r != PulseDecoder.PD_OK) {
                continue;
            }

            /*Verbose - Sync pulse*/
            if (config.genVerboseMessages) {
                blockDecoderListener.blockDecodeEvent("Sync pulse found " + decoder.getCurrentSampleString());
            }

            if (blockDecoderListener != null) {
                blockDecoderListener.blockDecodeEvent(null);
            }

            /*Obtain data*/
            r = decodeData(data);

            /*Check for validity*/
            boolean checkSumRight;

            switch (mangleType) {
                case MANGLE_FUNNY_COPY_10: {
                    boolean formatRight = true;

                    /*Verify check sum*/
                    checkSumRight = isCorrectChecksumFunnyCopy10(data);

                    /*Unmangle data*/
                    unmangleFunnyCopy10(data);

                    /*Check if in right format*/
                    DOS2Binary dtb = new DOS2Binary("");
                    try {
                        int[] userData = new int[validBytes - 2];
                        System.arraycopy(data, 1, userData, 0, validBytes - 2);
                        dtb.analyzeFromData(userData, ((Boolean) constraint));
                    } catch (IOException | DOS2BinaryException e) {
                        e.printStackTrace();
                        formatRight = false;
                    }
                    if (formatRight == false) {
                        /*Verbose - What data obtained*/
                        if (config.genVerboseMessages) {
                            blockDecoderListener.blockDecodeEvent("Decoding of data failed. Bytes decoded:" + validBytes + " " + decoder.getCurrentSampleString());
                        }
                        return new BlockDecodeResult(data, validBytes, r, decoder.getCurrentSample(), decoder);
                    }

                    /*Format is right, block appears to be OK*/
                    r = PulseDecoder.PD_OK;
                    break;

                }
                default: {
                    /*Check elementary integrity*/
                    if (r != PulseDecoder.PD_OK) {
                        /*Verbose - What data obtained*/
                        if (config.genVerboseMessages) {
                            blockDecoderListener.blockDecodeEvent("Decoding of data failed. Bytes decoded:" + validBytes + " " + decoder.getCurrentSampleString());
                        }
                        return new BlockDecodeResult(data, validBytes, r, decoder.getCurrentSample(), decoder);
                    }
                    /*Is block too short?*/
                    if (data.length < 3) {
                        return new BlockDecodeResult(data, validBytes, BlockDecodeResult.BLOCK_TOO_SHORT, decoder.getCurrentSample(), decoder);
                    }
                    /*Check sum?*/
                    checkSumRight = Utils.checkT2KBlock(data);

                }
            }

            /*Handle check sum*/
            if (checkSumRight == false) {
                if (config.genIgnoreBadSum == false) {
                    return new BlockDecodeResult(data, validBytes, BlockDecodeResult.BAD_CHSUM, decoder.getCurrentSample(), decoder);
                } else {
                    return new BlockDecodeResult(data, validBytes, BlockDecodeResult.OK_CHSUM_WARNING, decoder.getCurrentSample(), decoder);
                }
            }

            return new BlockDecodeResult(data, validBytes, r, decoder.getCurrentSample(), decoder);

        }

    }

    /**
     * Wait for pilot tone
     */
    private int waitForPilot() {

        int r;
        int h = 0;

        /*Wait for starting edge*/
        decoder.setTimeOut(MAX_PULSE);
        decoder.setCounter(0);
        r = decoder.countUntilAnyEdge(); //str

        if (r != PulseDecoder.PD_OK) {
            return r;
        }

        while (true) {

            decoder.setCounter(0);
            r = decoder.measurePulse();
            if (r != PulseDecoder.PD_OK) {
                return r;
            }

            /*If pulse is shorter than lower bound of pilot tone*/
            if (decoder.getCounter() < PILOT_LO) {
                return PulseDecoder.PD_UNEXPECTED_PULSE_WIDTH;
            }

            /*Pilot tone pulse. If required number of pilot tone pulses found, pilot tone
             *is considered valid*/
            h++;

            if (h < minPilotTonePulses) {
            } else {
                break;
            }

        }

        return PulseDecoder.PD_OK;

    }

    private int waitForSync() {
        int r;

        while (true) {

            /*Wait for the first edge*/
            decoder.setCounter(0);
            r = decoder.countUntilAnyEdge();

            /*Edge not found - bailing out*/
            if (r != PulseDecoder.PD_OK) {
                return r;
            }

            /*Compare to half of sync pulse upper bound. If longer, a pilot
             tone pulse was found and it is necessary still wait for sync */
            if (decoder.getCounter() > (SYNC_HI / 2)) {
                continue;
            }

            /*Wait for the 2nd edge*/
            r = decoder.countUntilAnyEdge();

            /*If 2nd edge not found for whatever reason, bailing out*/
            if (r != PulseDecoder.PD_OK) {
                return r;
            }

            /*Sync pulse found*/
            return PulseDecoder.PD_OK;
        }

    }

    private int decodeData(int[] data) {

        /*Data*/
        int d = 0;
        int mask = 128;
        int cur = 0;
        int dataLength = data.length;

        /*Result*/
        int r;

        /*Binary file format related*/
        while (true) {

            /*Measure length of pulse*/
            decoder.setCounter(0);
            r = decoder.measurePulse();

            /*Any failure results into corrupt data block*/
            if (r != PulseDecoder.PD_OK) {
                return r;
            }
            int c = decoder.getCounter();

            /*Determine 0 or 1*/
            if (c >= WIDE_LO && c <= WIDE_HI) {
                cur += mask;
            } else if (c >= NARROW_LO && c < WIDE_LO) {
                cur += 0;
            } else {
                return PulseDecoder.PD_NOT_ONE_NOT_ZERO;
            }

            if (mask == 1) {
                data[d] = cur;
                validBytes++;
                mask = 128;
                cur = 0;
                d++;
            } else {
                mask >>= 1;
            }

            if (d < dataLength) {
            } else {
                break;
            }
        }
        return PulseDecoder.PD_OK;
    }

    /**
     *
     * @return
     */
    public long getCurrentSample() {
        return decoder.getCurrentSample();
    }

    /**
     *
     * @param listener
     */
    @Override
    public void setBlockDecoderListener(BlockDecoderListener listener) {
        blockDecoderListener = listener;
    }

    private void recalculatePulses(int sampleRate) {

        double rate = sampleRate;
        PILOT_HI = (int) Math.round(PILOT_HI * (rate / 44100.0));
        PILOT_LO = (int) Math.round(PILOT_LO * (rate / 44100.0));
        SYNC_HI = (int) Math.round(SYNC_HI * (rate / 44100.0));
        SYNC_LO = (int) Math.round(SYNC_LO * (rate / 44100.0));
        WIDE_HI = (int) Math.round(WIDE_HI * (rate / 44100.0));
        WIDE_LO = (int) Math.round(WIDE_LO * (rate / 44100.0));
        NARROW_LO = (int) Math.round(NARROW_LO * (rate / 44100.0));
        MAX_PULSE = (int) Math.round(MAX_PULSE * (rate / 44100.0));
    }

    private boolean isCorrectChecksumFunnyCopy10(int[] data) {

        /*Get check sum stored in the block*/
        int storedCheckSum = data[validBytes - 1];

        /*Calculate check sum*/
        int calculatedCheckSum = data[0];
        for (int i = 1; i < validBytes - 1; i++) {
            calculatedCheckSum ^= data[i];
        }

        /*Mask the MSB*/
        calculatedCheckSum &= 0x7F;
        storedCheckSum &= 0x7F;

        /*Compare*/
        return (calculatedCheckSum == storedCheckSum);

    }

    private void unmangleFunnyCopy10(int[] data) {
        boolean wasCarry;

        /*From head to tail*/
        for (int i = 1; i < validBytes - 1; i++) {

            if (i < validBytes - 1) {
                wasCarry = ((data[i + 1] & 0x80) == 0x80);
            } else {
                wasCarry = false;
            }
            data[i] = ((data[i] << 1) & 0xFF);
            if (wasCarry == true) {
                data[i] |= 0x01;
            }
        }
    }

    /**
     *
     * @param eventInfo
     */
    @Override
    public void blockDecodeEvent(Object eventInfo) {

    }

}
