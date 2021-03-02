package turbodecoder.decoder;

import turbodecoder.decoder.pulse.PulseDecoder;
import turbodecoder.decoder.pulse.WavePulseDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class SuperTurboBlockDecoder implements BlockDecoder, BlockDecoderListener {

    private static final int LO_IDX = 0;
    private static final int MED_IDX = 1;
    private static final int HI_IDX = 2;

    private PulseDecoder decoder;
    private BlockDecoderListener blockDecoderListener = null;
    private int validBytes;

    private int MAX_3PILOT = 200;
    private int MAX_PULSE = 50;
    private int T2K_MIN = 40;

    private DecoderConfig config;
    private int determinedTurbo;
    private boolean uniTurboDetermination;
    private int minPilotToneBunchPulses;

    SuperTurboBlockDecoder(PulseDecoder decoder, DecoderConfig config, boolean uniTurboDetermination, int minPilotTonePulses) {

        this.decoder = decoder;
        this.config = config;
        this.uniTurboDetermination = uniTurboDetermination;
        recalculatePulses(decoder.getSampleRate());
        this.minPilotToneBunchPulses = minPilotTonePulses / 4;
    }

    SuperTurboBlockDecoder(PulseDecoder decoder, DecoderConfig config, int minPilotTonePulses) {
        this(decoder, config, false, minPilotTonePulses);
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

        int r = 0;

        int[] data = new int[dataLength];
        int[] t2kHeaderData = new int[19];
        int[] stHeaderData = new int[29];

        int plcrate[] = new int[3];
        validBytes = 0;
        determinedTurbo = FileDecoder.CS_SUPER_TURBO;

        if (blockDecoderListener == null) {
            blockDecoderListener = this;
        }

        while (true) {

            /*Wait for pilot tone*/
            r = waitForPilot(plcrate);
            if (r == PulseDecoder.PD_ERROR || r == WavePulseDecoder.PD_EOF || r == PulseDecoder.PD_USER_BREAK) {
                return new BlockDecodeResult(null, validBytes, r, decoder.getCurrentSample(), decoder);
            }
            if (r != PulseDecoder.PD_OK) {
                continue;
            }

            /*Verbose - Pilot Tone*/
            if (config.genVerboseMessages) {
                blockDecoderListener.blockDecodeEvent(String.format("Pilot tone found. Pulse boundaries: %d,%d,%d. ", plcrate[0], plcrate[1], plcrate[2]) + decoder.getCurrentSampleString());
            }


            /*Wait for sync pulse*/
            r = waitForSync(plcrate);
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

            /*Prepare for data*/
            if (uniTurboDetermination == true) {
                if (determinedTurbo == FileDecoder.CS_TURBO_2000) {
                    data = t2kHeaderData;
                } else {
                    data = stHeaderData;
                }
            }

            /*Obtain data*/
            r = decodeData(plcrate, data);


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
                boolean b = Utils.checkSTBlock(data);

                if (b == false) {
                    if (config.genIgnoreBadSum == false) {
                        return new BlockDecodeResult(data, validBytes, BlockDecodeResult.BAD_CHSUM, decoder.getCurrentSample(), decoder, determinedTurbo);
                    } else {
                        return new BlockDecodeResult(data, validBytes, BlockDecodeResult.OK_CHSUM_WARNING, decoder.getCurrentSample(), decoder, determinedTurbo);
                    }
                }

                return new BlockDecodeResult(data, validBytes, r, decoder.getCurrentSample(), decoder, determinedTurbo);
            }

        }

    }

    private int waitForPilot(int[] plcrate) {

        int r = 0;
        int h = 0;
        int c = 0;

        /*Detection of speed*/
        decoder.setTimeOut(MAX_PULSE);
        decoder.setCounter(0);

        for (int q = 0; q < 4; q++) {

            /*Read three pulses to measure speed*/
            decoder.setTimeOut(MAX_3PILOT);
            decoder.setCounter(0);

            for (int i = 0; i < 3; i++) {
                r = decoder.measurePulse();
                if (r != PulseDecoder.PD_OK) {
                    return r;
                }
            }

            /*Three pulses read, speed is measured*/
            c = decoder.getCounter();

            plcrate[HI_IDX] = c / 2;
            plcrate[MED_IDX] = c / 4;
            plcrate[LO_IDX] = c / 8;

            /*Wait for edge*/
            decoder.setCounter(0);
            decoder.setTimeOut(MAX_PULSE);
            r = decoder.countUntilAnyEdge();
            if (r != PulseDecoder.PD_OK) {
                return r;
            }

            /*At least 256 pulses should be at the same speed*/
            h = 0;
            decoder.setTimeOut(plcrate[HI_IDX]);

            while (h < minPilotToneBunchPulses) {
                decoder.setCounter(0);
                r = decoder.measurePulse();
                if (r != PulseDecoder.PD_OK) {
                    return r;
                }
                c = decoder.getCounter();
                if (c < MED_IDX) {
                    return PulseDecoder.PD_UNEXPECTED_PULSE_WIDTH;
                }
                h++;
            }

            /*256 pulses found*/
        }

        /*Turbo 2000 or Super Turbo?*/
        if (uniTurboDetermination && plcrate[HI_IDX] > T2K_MIN) {
            determinedTurbo = FileDecoder.CS_TURBO_2000;
        }

        return PulseDecoder.PD_OK;

    }

    private int waitForSync(int[] plcrate) {
        int r = 0;

        decoder.setTimeOut(MAX_PULSE);

        while (true) {

            /*Wait for the first edge*/
            decoder.setCounter(0);
            r = decoder.countUntilAnyEdge();

            /*Edge not found - bailing out*/
            if (r != PulseDecoder.PD_OK) {
                return r;
            }

            /*Compare to half of sync pulse upper bound. If longer, probably
             *pilot tone pulse was found*/
            if (decoder.getCounter() > (plcrate[MED_IDX] / 2)) {
                continue;
            }

            /*Checking whether the pulse is not longer than maximum length
             *of pilot tone pulse. If it is, pilot tone should be found again
             */
            if (decoder.getCounter() > (plcrate[HI_IDX] / 2)) {
                return PulseDecoder.PD_UNEXPECTED_PULSE_WIDTH;
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

    private int decodeData(int[] plcrate, int[] data) {
        /*Data*/
        int d = 0;
        int mask = 128;
        int cur = 0;
        int dataLength = data.length;

        /*Result*/
        int r = 0;

        decoder.setTimeOut(plcrate[HI_IDX]);

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
            if (c > plcrate[MED_IDX]) {
                cur += mask;
            } else {
                cur += 0;
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
     * @param listener
     */
    @Override
    public void setBlockDecoderListener(BlockDecoderListener listener) {
        blockDecoderListener = listener;
    }

    private void recalculatePulses(int sampleRate) {
        double rate = sampleRate;
        MAX_3PILOT = (int) Math.round(MAX_3PILOT * (rate / 44100.0));
        MAX_PULSE = (int) Math.round(MAX_PULSE * (rate / 44100.0));
        T2K_MIN = (int) Math.round(T2K_MIN * (rate / 44100.0));

    }

    /**
     *
     * @param eventInfo
     */
    @Override
    public void blockDecodeEvent(Object eventInfo) {

    }

}
