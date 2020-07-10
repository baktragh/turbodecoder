package turbodecoder.decoder;

import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;
import turbodecoder.dtb.DOS2Binary;
import turbodecoder.dtb.DOS2BinaryException;

/**
 *
 * @author  
 */
public class TurboBlizzardFileDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "BLIZZARD";
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

        decoder = new TurboBlizzardBlockDecoder(d, config, false, 256);
        BlockDecoder syncBlockDecoder = new TurboBlizzardBlockDecoder(d, config, true, 2_048);

        this.log = log;
        decoder.setBlockDecoderListener(this);
        syncBlockDecoder.setBlockDecoderListener(this);

        int[] header;
        int[] data;
        int foundBlockType;

        BlockDecodeResult bdr = null;
        int errCode;

        /*Try to decode synchronization block*/
        synchroDecode:
        while (true) {

            /*Decode synchronization block which is empty*/
            firstFileSample = d.getCurrentSample();
            bdr = syncBlockDecoder.decodeBlock(0);
            errCode = bdr.getErrorCode();

            /*Check result - immediate break?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Check result for some soft error*/
            if (!BlockDecodeResult.isCodeOK(errCode)) {
                continue;
            }

            break;
        }

        log.addMessage(
                new DecoderMessage(MSG_PFX, "SYNC BLOCK: <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        /*Try to decode header block*/
        if (config.genPreferAdaptiveSpeedDetection == true) {
            ((TurboBlizzardBlockDecoder) decoder).pickTransferSpeed((TurboBlizzardBlockDecoder) syncBlockDecoder);
        }

        headerDecode:
        while (true) {

            /*Decode header which is 77+1 safety byte*/
            bdr = decoder.decodeBlock(77);
            errCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Turbo Blizzard header? There is not much else to check*/
            if (BlockDecodeResult.isCodeOK(errCode)) {
                header = bdr.getData();
                break;
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

            outerLoop:
            while (quitme == false) {

                /*Read block 1028 bytes + 1 safety byte*/
                bdr = decoder.decodeBlock(1_028);
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
                        for (int i = 2; i < 1_026; i++) {
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
        nameBase += Utils.getPolishedFilespec(headerData, 0, 76);

        String xtension = ".blizdat";

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

        /*Data length*/
        int dataLen = data[0] + (data[1] << 8);

        /*Check byte that must be always 0*/
        if (data[1_026] != 0) {
            return BLOCK_BAD;
        }

        /*Check length*/
        if (dataLen == 1_024) {
            return BLOCK_FULL;
        }
        if (dataLen == 0) {
            return BLOCK_EOF;
        }
        if (dataLen < 1_024) {
            return BLOCK_PART;
        }
        return BLOCK_BAD;

    }

    private String headerToString(int[] header) {
        byte nameBytes[] = new byte[76];
        for (int i = 0; i < 76; i++) {
            nameBytes[i] = (byte) header[i];
        }
        return new String(nameBytes);
    }
}
