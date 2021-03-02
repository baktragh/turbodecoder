package turbodecoder.decoder;

import turbodecoder.decoder.pulse.PulseDecoder;
import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;
import turbodecoder.dtb.DOS2Binary;
import turbodecoder.dtb.DOS2BinaryException;

/**
 *
 * @author  
 */
public class KSOTurbo2000FileDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "KSO T2000";
    private static final int BLOCK_FULL = 0;
    private static final int BLOCK_PART = 1;
    private static final int BLOCK_EOF = 2;
    private static final int BLOCK_BAD = 3;
    private BlockDecoder decoder;
    private long firstFileSample = 0L;
    private DecoderLog log;

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

        decoder = new KSOTurbo2000BlockDecoder(d, config);

        this.log = log;
        decoder.setBlockDecoderListener(this);

        int[] header;
        int[] data;
        int foundBlockType;

        BlockDecodeResult bdr = null;
        int errCode;

        /*Try to decode header*/
        headerDecode:
        while (true) {

            /*Decode 13 bytes block*/
            firstFileSample = d.getCurrentSample();
            bdr = decoder.decodeBlock(13);
            errCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }


            /*KSO Turbo 2000 header found?*/
            if (BlockDecodeResult.isCodeOK(errCode)) {
                header = bdr.getData();
                if (header[0] == 0 && header[1] == 255) {
                    break;
                }
            }

            /*Header not decoded for any other reasons, retry*/
            continue;

        }

        /*Header is decoded ok*/
 /*Write the header to the log*/
        log.addMessage(
                new DecoderMessage(MSG_PFX, "HEADER: " + headerToString(header) + "<" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        byte[] ba = null;
        ByteArrayOutputStream baos = null;
        DataOutputStream dos = null;

        try {

            baos = new ByteArrayOutputStream();
            dos = new DataOutputStream(baos);

            boolean quitme = false;
            boolean error = false;
            int blockCount = 0;

            /*Handle file with loader - skip one block*/
            if (config.ksoTurbo2000FileFormat == DecoderConfig.PL_KSO_TURBO_2000_FORMAT_WITH_LOADER) {
                bdr = decoder.decodeBlock(3_075);
                errCode = bdr.getErrorCode();

                if (!BlockDecodeResult.isCodeOK(errCode)) {
                    log.addMessage(
                            new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                            true);
                } else {
                    log.addMessage(
                            new DecoderMessage(MSG_PFX, "Loader decoded and skipped", DecoderMessage.SEV_DETAIL), true
                    );
                }

                if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                    return false;
                }

            }

            /*Decode the blocks of the file*/
            outerLoop:
            while (quitme == false) {

                /*Read block*/
                bdr = decoder.decodeBlock(3_075);
                errCode = bdr.getErrorCode();

                blockCount++;

                /*Block not obtained for any reason*/
                if (!BlockDecodeResult.isCodeOK(errCode)) {
                    log.addMessage(
                            new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                            true);
                    error = true;
                    break;
                }

                /*Block perfect ? If not, taunt*/
                if (!BlockDecodeResult.isCodePerfect(errCode)) {
                    log.addMessage(
                            new DecoderMessage(MSG_PFX, "BLOCK: " + Integer.toString(blockCount) + ": <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                            true);
                }

                data = bdr.getData();
                foundBlockType = getBlockType(data);


                /*According to the block type, do the resolution*/
                switch (foundBlockType) {
                    /*Full block*/
                    case BLOCK_FULL: {
                        for (int i = 2; i < 3_074; i++) {
                            dos.write(data[i]);
                        }
                        break;
                    }
                    /*EOF block*/
                    case BLOCK_EOF: {
                        quitme = true;
                        break;
                    }
                    /*Partial block*/
                    case BLOCK_PART: {
                        int dataLen = data[0] + (data[1] << 8);
                        for (int i = 0; i < dataLen; i++) {
                            dos.write(data[2 + i]);
                        }
                        quitme = true;
                        break;
                    }

                    /*Bad block*/
                    default: {
                        log.addMessage(
                                new DecoderMessage(MSG_PFX, "ERROR: Unknown block type {" + Long.toString(bdr.getSample()) + "}", DecoderMessage.SEV_ERROR),
                                true);
                        error = true;
                        break outerLoop;
                    }
                }

            }


            /*We are out of the outerLoop*/
            ba = baos.toByteArray();
            dos.close();

            if (error == true) {
                if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                    return false;
                } else {
                    return true;
                }
            }

        } catch (IOException e) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
                    true);
            try {
                dos.close();
            } catch (IOException xe) {
                /*Intentionally blank*/
            }
            return true;
        }

        /*Construct output filespec*/
        String fspec = constructFilespec(outdir, header, ba, firstFileSample, config.genPrependSampleNumber);

        /*Flush data to disk*/
        RandomAccessFile raf = null;

        try {
            raf = new RandomAccessFile(fspec, "rw");
            raf.setLength(0L);
            raf.write(ba);
            raf.close();
        } catch (IOException e) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
                    true);
            try {
                raf.close();
            } catch (IOException xe) {
                /*Intentionally blank*/
            }
            return true;
        }

        log.addMessage(
                new DecoderMessage(MSG_PFX, "SAVE: " + fspec, DecoderMessage.SEV_SAVE),
                true);
        return true;

    }

    String constructFilespec(String outdir, int[] headerData, byte[] fileData, long sample, boolean prependSample) throws Exception {

        /*First, we construct namebase, removing all
         *dangerous characters*/
        String nameBase = "";
        if (prependSample == true) {
            nameBase += Utils.padZeros(Long.toString(sample), 10);
            nameBase += '_';
        }
        nameBase += Utils.getPolishedFilespec(headerData, 2, 10);

        String xtension = ".ksodat";

        /*Check whether file happens to be binary file*/
        DOS2Binary dtb = new DOS2Binary("");
        try {
            dtb.analyzeFromData(Utils.getAsIntArray(fileData));
            xtension = ".xex";
        } catch (IOException | DOS2BinaryException e) {
            /*Intentionally blank*/
        }

        if (nameBase.toUpperCase().endsWith(xtension.toUpperCase())) {
            xtension = "";
        }

        File odf = new File(outdir);
        outdir = odf.getCanonicalPath();

        String retVal = "";

        /*Filespec is constructed*/
        if (outdir.endsWith(TurboDecoder.SP)) {
            retVal = outdir + nameBase + xtension;
        } else {
            retVal = outdir + TurboDecoder.SP + nameBase + xtension;
        }

        return retVal;
    }

    /**
     *
     * @param eventInfo
     */
    @Override
    public void blockDecodeEvent(Object eventInfo) {
        if (eventInfo != null) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, (String) eventInfo, DecoderMessage.SEV_DETAIL),
                    true);
        }
        log.impulse(true);
    }

    private int getBlockType(int[] data) {

        int dataLen = data[0] + (data[1] << 8);

        if (dataLen == 3_072) {
            return BLOCK_FULL;
        }
        if (dataLen == 0) {
            return BLOCK_EOF;
        }
        if (dataLen < 3_072) {
            return BLOCK_PART;
        }
        return BLOCK_BAD;

    }

    private String headerToString(int[] header) {
        byte nameBytes[] = new byte[10];
        for (int i = 2; i < 12; i++) {
            nameBytes[i - 2] = (byte) header[i];
        }
        return new String(nameBytes);
    }
}
