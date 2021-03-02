package turbodecoder.decoder;

import turbodecoder.decoder.pulse.PulseDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class Turbo2000BlockDecoder implements BlockDecoder, BlockDecoderListener {

    /**
     *
     */
    public static final int PULSE_WIDTHS_DEFAULT = 0;

    /**
     *
     */
    public static final int PULSE_WIDTHS_LOWER_SILESIA_TURBO_2000 = 1;

    /*Pulses*/
    int PILOT_HI = 47;
    int PILOT_LO = 25;

    int SYNC_HI = 19;
    int SYNC_LO = 2;
    /*Was 4*/

    int WIDE_HI = 40;
    int WIDE_LO = 20;
    int NARROW_LO = 6;

    int MAX_PULSE = 50;

    private final PulseDecoder decoder;
    private final DecoderConfig config;
    private int validBytes;
    private BlockDecoderListener blockDecoderListener = null;
    private final int minPilotTonePulses;


    Turbo2000BlockDecoder(PulseDecoder decoder, DecoderConfig config, int minPilotTonePulses) {

        this.decoder = decoder;
        this.config = config;
        recalculatePulses(decoder.getSampleRate());
        this.minPilotTonePulses = minPilotTonePulses;

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

            /*Data not obtained*/
            if (r != PulseDecoder.PD_OK) {

                /*Verbose - What data obtained*/
                if (config.genVerboseMessages) {
                    blockDecoderListener.blockDecodeEvent("Decoding of data failed. Bytes decoded:" + validBytes + " " + decoder.getCurrentSampleString());
                }

                return new BlockDecodeResult(data, validBytes, r, decoder.getCurrentSample(), decoder);
            } /*Data obtained*/ else {

                /*Not acceptable - too short*/
                if (data.length < 3) {
                    return new BlockDecodeResult(data, validBytes, BlockDecodeResult.BLOCK_TOO_SHORT, decoder.getCurrentSample(), decoder);
                }
                /*Checksum verify*/
                boolean b = Utils.checkT2KBlock(data);

                if (b == false) {
                    if (config.genIgnoreBadSum == false) {
                        return new BlockDecodeResult(data, validBytes, BlockDecodeResult.BAD_CHSUM, decoder.getCurrentSample(), decoder);
                    } else {
                        return new BlockDecodeResult(data, validBytes, BlockDecodeResult.OK_CHSUM_WARNING, decoder.getCurrentSample(), decoder);
                    }
                }

                return new BlockDecodeResult(data, validBytes, r, decoder.getCurrentSample(), decoder);
            }

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
        r = decoder.waitForRisingEdge();

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

    /**
     *
     * @param eventInfo
     */
    @Override
    public void blockDecodeEvent(Object eventInfo) {

    }

}
