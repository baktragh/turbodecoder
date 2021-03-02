package turbodecoder.decoder;

import turbodecoder.decoder.pulse.PulseDecoder;
import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class TurboMonitorDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "MONITOR";
    private DecoderLog log;
    private DecoderConfig config;
    private final int turboType;
    private BlockDecoder blockDecoder;

    /**
     *
     * @param turboType
     */
    public TurboMonitorDecoder(int turboType) {
        this.turboType = turboType;
    }

    /**
     *
     * @param outdir
     * @param log
     * @param d
     * @param config
     * @return
     * @throws Exception
     */
    @Override
    public boolean decodeFile(String outdir, DecoderLog log, PulseDecoder d, DecoderConfig config) throws Exception {

        this.log = log;
        this.config = config;

        /*Select block decoder based on turbo type*/
        switch (turboType) {

            case FileDecoder.PL_LOWER_SILESIA_TURBO_2000: {
                blockDecoder = new Turbo2000BlockDecoder(d, config, 20);
                break;
            }

            case FileDecoder.PL_HARD_TURBO:
            case FileDecoder.CS_TURBO_2000_KB:
            case FileDecoder.CS_TURBO_2000: {
                if (config.genPreferAdaptiveSpeedDetection == false) {
                    blockDecoder = new Turbo2000BlockDecoder(d, config, 256);
                } else {
                    blockDecoder = new SuperTurboBlockDecoder(d, config, 256);
                }
                break;
            }
            case FileDecoder.CS_TURBO_TAPE:
            case FileDecoder.CS_SUPER_TURBO: {
                blockDecoder = new SuperTurboBlockDecoder(d, config, 256);
                break;
            }
            case FileDecoder.PL_KSO_TURBO_2000: {
                blockDecoder = new KSOTurbo2000BlockDecoder(d, config);
                break;
            }
            case FileDecoder.PL_TURBO_BLIZZARD: {
                blockDecoder = new TurboBlizzardBlockDecoder(d, config, true, 256);
                break;
            }
            case FileDecoder.PL_TURBO_ROM: {
                blockDecoder = new TurboRomBlockDecoder(d, config, false, -1);
                break;
            }
            case FileDecoder.PL_ATARI_SUPER_TURBO: {
                blockDecoder = new ASTBlockDecoder(d, config);
                break;
            }
        }

        blockDecoder.setBlockDecoderListener(this);
        long startPosition = d.getCurrentSample();

        /*Array for data*/
        int[] data;
        int validBytes;

        /*Results of decoding*/
        BlockDecodeResult bdr;
        int errCode;

        /*Try to decode block*/
        bdr = blockDecoder.decodeBlock(65_536);
        errCode = bdr.getErrorCode();

        /*Obtain some valid bytes if there are any*/
        validBytes = bdr.getValidBytes();
        data = bdr.getData();

        /*Construct message*/
        StringBuilder msg = new StringBuilder();

        msg.append("SUCCESS: Bytes monitored: ");
        msg.append(Integer.toString(validBytes));


        /*Handle those valid bytes*/
        if (data != null && data.length > 0 && validBytes >= 0) {

            int[] finalData = new int[validBytes];

            /*Copy to auxiliary array*/
            System.arraycopy(data, 0, finalData, 0, validBytes);

            /*Write message*/
            log.addMessage(new DecoderMessage(MSG_PFX, msg.toString(), DecoderMessage.SEV_INFO), true);
            log.addMessage(new DecoderMessage(MSG_PFX, "Reason to terminate monitoring: " + bdr.getFullErrorMessage(), DecoderMessage.SEV_DETAIL), true);

            /*Save data*/
            if (validBytes > 0) {
                saveData(startPosition, finalData, outdir, log, bdr);
            }
        }

        /*Immediate break ?*/
        if (BlockDecodeResult.isCodeImmediateBreak(errCode) == true) {
            log.addMessage(new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()), true);
            return false;
        }

        /*Possible to continue*/
        return true;
    }

    private void saveData(long startPosition, int[] finalData, String outdir, DecoderLog log, BlockDecodeResult bdr) {

        /*Construct file specifier*/
        StringBuilder fspecb = new StringBuilder();

        /*Samples from the beginning*/
        String samples = Utils.padZeros(Long.toString(startPosition), 10);

        /*Length of the data*/
        String length = Utils.padZeros(Integer.toString(finalData.length), 5);

        String firstByte;
        String lastByte;

        /*First and last bytes*/
        firstByte = Utils.padZeros(Integer.toString(finalData[0]), 3);
        lastByte = Utils.padZeros(Integer.toString(finalData[finalData.length - 1]), 3);


        /*Construction of file specifier*/
        fspecb.append(samples);
        fspecb.append('_');
        fspecb.append(FileDecoder.turboSystemBriefNames[turboType]);
        fspecb.append('_');
        fspecb.append(length);
        fspecb.append('_');
        fspecb.append(firstByte);
        fspecb.append('_');
        fspecb.append(lastByte);
        fspecb.append('_');

        fspecb.append(".tm");

        String fspec = fspecb.toString();

        FileOutputStream fos;
        BufferedOutputStream bos = null;

        try {

            /*Prepare output*/
            File fod = new File(outdir);
            String od = fod.getCanonicalPath();

            fspec = od + TurboDecoder.SP + fspec;
            File f = new File(fspec);

            f.delete();

            /*Write data*/
            fos = new FileOutputStream(fspec);
            bos = new BufferedOutputStream(fos);

            int[] dataPortion = getDataPortion(config.monitorSaveAllBytes, finalData);
            int offset = dataPortion[0];
            int count = dataPortion[1];

            for (int i = 0; i < count; i++) {
                bos.write(finalData[offset + i]);
            }

            bos.flush();
            bos.close();

            log.addMessage(
                    new DecoderMessage(MSG_PFX, "SAVE: " + fspec + " (" + Integer.toString(count) + " bytes)", DecoderMessage.SEV_SAVE),
                    true);

        } catch (IOException e) {
            try {
                if (bos != null) {
                    bos.close();
                }
            } catch (IOException xe) {
                xe.printStackTrace();
            }
            log.addMessage(
                    new DecoderMessage(MSG_PFX, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
                    true);
        }

    }

    /**
     *
     * @param eventInfo
     */
    @Override
    public void blockDecodeEvent(Object eventInfo) {
        log.impulse(true);
    }

    private int[] getDataPortion(boolean allBytes, int[] data) {
        switch (turboType) {
            case FileDecoder.PL_LOWER_SILESIA_TURBO_2000:
            case FileDecoder.CS_TURBO_2000:
            case FileDecoder.CS_SUPER_TURBO:
            case FileDecoder.CS_TURBO_2000_KB: {

                if (allBytes == true || data.length < 2) {
                    return new int[]{0, data.length};
                } else {
                    return new int[]{1, data.length - 2};
                }
            }
            case FileDecoder.CS_TURBO_TAPE: {
                if (allBytes == true || data.length < 17) {
                    return new int[]{0, data.length};
                } else {
                    int lastOffset = data[2] + 256 * (data[3] & 0x7F);
                    int finalLength = lastOffset - 16;
                    if (finalLength > data.length) {
                        finalLength = data.length - 17;
                    }
                    return new int[]{17, finalLength};
                }
            }

            case FileDecoder.PL_KSO_TURBO_2000: {
                if (allBytes == true || data.length < 3) {

                    return new int[]{0, data.length};
                } else {
                    /*Stored in block?*/
                    int finalLength = data[0] + 256 * data[1];
                    if (finalLength > data.length) {
                        finalLength = data.length - 3;
                    }
                    return new int[]{2, finalLength};

                }
            }
            case FileDecoder.PL_TURBO_BLIZZARD: {
                if (allBytes == true || data.length < 3) {
                    return new int[]{0, data.length};
                } else {
                    int finalLength = data[0] + 256 * data[1];
                    if (finalLength > data.length) {
                        finalLength = data.length - 3;
                    }
                    return new int[]{2, finalLength};
                }
            }

            case FileDecoder.PL_TURBO_ROM: {
                /*Always return all bytes*/
                return new int[]{0, data.length};
            }
            case FileDecoder.PL_ATARI_SUPER_TURBO: {
                /*Always return all bytes*/
                return new int[]{0, data.length};
            }

            default: {
                return new int[]{0, data.length};
            }
        }
    }

}
