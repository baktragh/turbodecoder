package turbodecoder.decoder;

import turbodecoder.decoder.pulse.PulseDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class TurboBlizzardBlockDecoder implements BlockDecoder, BlockDecoderListener {

    /*22,12,8*/
    private int PILOT_HI = 36;
    private int PILOT_LO = 19;
    private int MAX_3PILOT = 3 * 35;

    private int WIDE_HI = 18;
    private int WIDE_LO = 10;

    private int NARROW_LO = 4;

    private int MAX_PULSE = 40;

    private final PulseDecoder decoder;
    private final DecoderConfig config;
    private int validBytes;
    private BlockDecoderListener blockDecoderListener = null;
    private final boolean sync;
    private final int pilotTonePulses;

    TurboBlizzardBlockDecoder(PulseDecoder decoder, DecoderConfig config, boolean sync, int pilotTonePulses) {

        this.decoder = decoder;
        this.config = config;
        recalculatePulses(decoder.getSampleRate());

        this.sync = sync;
        this.pilotTonePulses = pilotTonePulses;

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
                    String.format("Pulse ranges: NL %d,WL %d, WH %d,PL %d, PH %d, MP %d",
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
            if (sync == true && config.genPreferAdaptiveSpeedDetection == true) {
                r = waitForPilotAdaptive();
            } else {
                r = waitForPilot();
            }

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


            /*Wait for sync, but for zero length block, don't check for the last edge*/
            r = waitForSync(dataLength == 0 ? false : true);
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

            blockDecoderListener.blockDecodeEvent(null);


            /*If there is no data requested, we are finished*/
            if (data.length == 0) {
                return new BlockDecodeResult(data, 0, r, decoder.getCurrentSample(), decoder);
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
                boolean b = Utils.checkBlizzardBlock(data);

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

            /*Pilot tone pulse. If given number of  pilot tone pulses found, pilot tone
             *is considered valid*/
            h++;

            if (h < pilotTonePulses) {
            } else {
                break;
            }

        }

        return PulseDecoder.PD_OK;

    }

    private int waitForPilotAdaptive() {

        int r = 0;
        int h = 0;
        int c = 0;

        /*Detection of speed*/
        decoder.setTimeOut(MAX_PULSE);
        decoder.setCounter(0);
        r = decoder.waitForRisingEdge();
        if (r != PulseDecoder.PD_OK) {
            return r;
        }

        for (int q = 0; q < 4; q++) {

            /*Read three pulses to measure speed*/
            decoder.setTimeOut(MAX_3PILOT);
            decoder.setCounter(0);

            for (int i = 0; i < 4; i++) {
                r = decoder.measurePulse();
                if (r != PulseDecoder.PD_OK) {
                    return r;
                }
            }

            /*Four pulses read, speed is measured*/
            c = decoder.getCounter();

            int cPilot = c / 4;
            int cWide = c / 8;
            int cNarrow = c / 12;

            PILOT_HI = c / 2;
            /*Two pilot tone pulses is max*/
            PILOT_LO = cWide + (cPilot - cWide) / 2;
            WIDE_HI = PILOT_LO - 1;
            WIDE_LO = cNarrow + (cWide - cNarrow) / 2;
            NARROW_LO = cNarrow / 2;

            /*Older*/

 /*PILOT_LO = (c / 4 + 1);
            PILOT_HI = (c / 2);

            WIDE_HI = PILOT_LO - 1;
            WIDE_LO = (c / 8 + 1);

            NARROW_LO = c / 16;*/

 /*Wait for edge*/
            decoder.setCounter(0);
            decoder.setTimeOut(MAX_PULSE);
            r = decoder.countUntilAnyEdge();
            if (r != PulseDecoder.PD_OK) {
                return r;
            }

            /*At least 384 pulses should be at the same speed*/
            h = 0;
            decoder.setTimeOut(PILOT_HI);

            while (h < pilotTonePulses / 4) {
                decoder.setCounter(0);
                r = decoder.measurePulse();
                if (r != PulseDecoder.PD_OK) {
                    return r;
                }
                c = decoder.getCounter();
                if (c < PILOT_LO) {
                    return PulseDecoder.PD_UNEXPECTED_PULSE_WIDTH;
                }
                h++;
            }

            /*1/4 of required pulses found*/
        }

        return PulseDecoder.PD_OK;
    }

    private int waitForSync(boolean checkForLastEdge) {
        int r;
        int c;

        while (true) {

            /*Wait for the first edge*/
            decoder.setCounter(0);
            r = decoder.countUntilAnyEdge();

            /*Edge not found - bailing out*/
            if (r != PulseDecoder.PD_OK) {
                return r;
            }

            /*Pick half pulse width*/
            c = decoder.getCounter();

            /*Is it pilot tone pulse?*/
            if (c > (WIDE_HI / 2)) {
                continue;
            }

            /*Is it narrow pulse?*/
            if (c <= (WIDE_LO / 2)) {

                /*If not checking for last edge, we are done - sync block*/
                if (checkForLastEdge == false) {
                    return r;
                }

                /*Otherwise check for another edge*/
                r = decoder.countUntilAnyEdge();
                if (r != PulseDecoder.PD_OK) {
                    return r;
                }

                /*Another narrow pulse?*/
                decoder.setCounter(0);
                r = decoder.countUntilAnyEdge();
                if (r != PulseDecoder.PD_OK) {
                    return r;
                }
                c = decoder.getCounter();
                if (c <= (WIDE_LO / 2)) {
                    return decoder.countUntilAnyEdge();
                }
            }

            return PulseDecoder.PD_UNEXPECTED_PULSE_WIDTH;
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
        WIDE_HI = (int) Math.round(WIDE_HI * (rate / 44100.0));
        WIDE_LO = (int) Math.round(WIDE_LO * (rate / 44100.0));
        NARROW_LO = (int) Math.round(NARROW_LO * (rate / 44100.0));
        MAX_PULSE = (int) Math.round(MAX_PULSE * (rate / 44100.0));
        MAX_3PILOT = (int) Math.round(MAX_3PILOT * (rate / 44100.0));

    }

    void pickTransferSpeed(TurboBlizzardBlockDecoder sd) {
        this.PILOT_HI = sd.PILOT_HI;
        this.PILOT_LO = sd.PILOT_LO;
        this.WIDE_HI = sd.WIDE_HI;
        this.WIDE_LO = sd.WIDE_LO;
        this.NARROW_LO = sd.NARROW_LO;
        this.MAX_3PILOT = sd.MAX_3PILOT;
        this.MAX_PULSE = sd.MAX_PULSE;

    }

    /**
     *
     * @param eventInfo
     */
    @Override
    public void blockDecodeEvent(Object eventInfo) {

    }

}
