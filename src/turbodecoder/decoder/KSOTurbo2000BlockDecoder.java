package turbodecoder.decoder;

import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class KSOTurbo2000BlockDecoder implements BlockDecoder, BlockDecoderListener {

    int PILOT_HI = 54;
    int PILOT_LO = 34;

    int WIDE_HI = 28;
    int WIDE_LO = 17;

    int NARROW_LO = 5;

    int MAX_PULSE = 56;

    private final PulseDecoder decoder;
    private final DecoderConfig config;
    private int validBytes;
    private BlockDecoderListener blockDecoderListener = null;
    private int firstBlockBit;
    private int validBits;

    KSOTurbo2000BlockDecoder(PulseDecoder decoder, DecoderConfig config) {

        this.decoder = decoder;
        this.config = config;
        recalculatePulses(decoder.getSampleRate());

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
                    String.format("Pulse ranges: NL %d,WL %d, WH %d, MP %d",
                            NARROW_LO,
                            WIDE_LO,
                            WIDE_HI,
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

            /*Wait for first bit*/
            r = waitForFirstBit();
            if (r == PulseDecoder.PD_ERROR || r == PulseDecoder.PD_EOF || r == PulseDecoder.PD_USER_BREAK) {
                return new BlockDecodeResult(null, validBytes, r, decoder.getCurrentSample(), decoder);
            }
            if (r != PulseDecoder.PD_OK) {
                continue;
            }

            /*Verbose - Sync pulse*/
            if (config.genVerboseMessages) {
                blockDecoderListener.blockDecodeEvent("First data bit found " + decoder.getCurrentSampleString());
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
                    blockDecoderListener.blockDecodeEvent("Decoding of data failed. Bytes decoded:" + validBytes + "/"+validBits+" "+ decoder.getCurrentSampleString());
                }

                return new BlockDecodeResult(data, validBytes, r, decoder.getCurrentSample(), decoder);
            } /*Data obtained*/ else {

                /*Not acceptable - too short*/
                if (data.length < 3) {
                    return new BlockDecodeResult(data, validBytes, BlockDecodeResult.BLOCK_TOO_SHORT, decoder.getCurrentSample(), decoder);
                }
                /*Checksum verify*/
                boolean b = Utils.checkKSOBlock(data);
                
                if (config.genVerboseMessages) {
                    blockDecoderListener.blockDecodeEvent("Block decoded:" + validBytes + "/"+validBits+" "+ decoder.getCurrentSampleString());
                }

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
        
        decoder.setTimeOut(MAX_PULSE/2);

        while (true) {
            
            /*Wait for falling edge, do not count*/
            r = decoder.waitForFallingEdge();
            
            if (r != PulseDecoder.PD_OK) {
                return r;
            }
            
            /*Wait for rising edge and count*/
            decoder.setCounter(0);
            r= decoder.countUntilRisingEdge();
            
            
            if (r != PulseDecoder.PD_OK) {
                return r;
            }
            
            
            /*If pulse is shorter than lower bound of pilot tone*/
            if (decoder.getCounter() < (PILOT_LO/2)) {
                return PulseDecoder.PD_UNEXPECTED_PULSE_WIDTH;
            }
            
            h++;

            if (h < 256) {
            } else {
                break;
            }

        }

        return PulseDecoder.PD_OK;

    }

    private int waitForFirstBit() {
        int r;
        int c;
        validBits=0;
        
        decoder.setTimeOut(MAX_PULSE/2);

        while (true) {
            
            /*Wait for falling edge, do not count*/
            r=decoder.waitForFallingEdge();

            /*Pulse not found*/
            if (r != PulseDecoder.PD_OK) {
                return r;
            }
            
            /*Wait for rising edge,count*/
            decoder.setCounter(0);
            r=decoder.countUntilRisingEdge();
            
            if (r!=PulseDecoder.PD_OK) {
                return r;
            }
            
            c = decoder.getCounter();

            /*Is it pilot tone pulse?*/
            if (c> (WIDE_HI/2)) {
                continue;
            }

            /*Is it narrow pulse?*/
            if (c < (WIDE_LO/2)) {
                firstBlockBit = 0;
                validBits++;
                return r;
            }
            /*Is it wide pulse?*/
            if (c <= (WIDE_HI/2) ) {
                firstBlockBit = 128;
                validBits++;
                return r;
            }

            /*Not a valid pulse*/
            return PulseDecoder.PD_UNEXPECTED_PULSE_WIDTH;

        }

    }

    private int decodeData(int[] data) {

        /*Data*/
        int d = 0;
        int mask = 64;
        int cur = firstBlockBit;
        int dataLength = data.length;

        /*Result*/
        int r;
        
        decoder.setTimeOut(MAX_PULSE/2);

        while (true) {
            
            /*Wait for falling edge, do not count*/
            r = decoder.waitForFallingEdge();

            /*Any failure results into corrupt data block*/
            if (r != PulseDecoder.PD_OK) {
                return r;
            }
            
            /*Wait for rising edge while counting*/
            decoder.setCounter(0);
            r = decoder.countUntilRisingEdge();
            
            if (r != PulseDecoder.PD_OK) {
                return r;
            }
            int c = decoder.getCounter();
            

            /*Determine 0 or 1*/
            if (c >= (WIDE_LO/2) && c <= (WIDE_HI/2)) {
                cur += mask;
            } else if (c >= (NARROW_LO/2) && c < (WIDE_LO/2)) {
                cur += 0;
            } else {
                return PulseDecoder.PD_NOT_ONE_NOT_ZERO;
            }
            
            validBits++;

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
    }

    /**
     *
     * @param eventInfo
     */
    @Override
    public void blockDecodeEvent(Object eventInfo) {

    }

}
