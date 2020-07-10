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
public class KBlockFileDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "T2K KB";
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

        if (config != null && config.genPreferAdaptiveSpeedDetection == true) {
            decoder = new SuperTurboBlockDecoder(d, config, 256);
        } else {
            decoder = new Turbo2000BlockDecoder(d, config, 256);
        }

        this.log = log;
        decoder.setBlockDecoderListener(this);

        int[] header;
        int[] data;

        BlockDecodeResult bdr = null;
        int errCode;

        /*Try to decode header*/
        headerDecode:
        while (true) {

            /*Decode 19 bytes block*/
            firstFileSample = d.getCurrentSample();
            bdr = decoder.decodeBlock(19);
            errCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Kilobyte blocks header found ?*/
            if (BlockDecodeResult.isCodeOK(errCode)) {
                if (bdr.getData()[0] == 0) {
                    break;
                }
            }

            /*Header not decoded for any other reasons, retry*/
            continue;

        }

        /*Header decoded*/
        header = bdr.getData();

        /*Write the header to the log*/
        log.addMessage(
                new DecoderMessage(MSG_PFX, "HEADER: " + Utils.kblockHeaderToString(header) + "<" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        byte[] ba = null;
        ByteArrayOutputStream baos = null;
        DataOutputStream dos = null;

        try {

            baos = new ByteArrayOutputStream();
            dos = new DataOutputStream(baos);

            boolean quitme = false;
            boolean expectEOF = false;
            boolean error = false;

            int blockCount = 0;

            outerLoop:
            while (quitme == false) {

                /*Read block*/
                bdr = decoder.decodeBlock(1_026);
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

                /*If not a perfect block, scream*/
                if (!BlockDecodeResult.isCodePerfect(errCode)) {
                    log.addMessage(
                            new DecoderMessage(MSG_PFX, "BLOCK: " + Integer.toString(blockCount) + ": <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                            true);
                }

                data = bdr.getData();

                /*According to the block type, do the resolution*/
                switch (data[0]) {
                    /*Full block*/
                    case 255: {
                        if (expectEOF == true) {
                            log.addMessage(
                                    new DecoderMessage(MSG_PFX, "ERROR: Expected EOF block, but found FULL block {" + Long.toString(bdr.getSample()) + "}", DecoderMessage.SEV_ERROR),
                                    true);
                            error = true;
                            break outerLoop;
                        }
                        for (int i = 1; i < 1_025; i++) {
                            dos.write(data[i]);
                        }
                        break;
                    }
                    /*EOF block*/
                    case 250: {
                        quitme = true;
                        break;
                    }
                    /*Others*/
                    default: {

                        if (expectEOF == true) {
                            log.addMessage(
                                    new DecoderMessage(MSG_PFX, "ERROR: Expected EOF block, but found PARTIAL block {" + Long.toString(bdr.getSample()) + "}", DecoderMessage.SEV_ERROR),
                                    true);
                            error = true;
                            break outerLoop;
                        }

                        if (data[0] >= 251) {
                            int hi = data[0] - 251;
                            int lo = data[1_024];
                            int ln = hi * 256 + lo;
                            for (int i = 0; i < ln; i++) {
                                dos.write(data[1 + i]);
                            }
                            expectEOF = true;
                        } else {
                            log.addMessage(
                                    new DecoderMessage(MSG_PFX, "ERROR: Unknown block type {" + Long.toString(bdr.getSample()) + "}", DecoderMessage.SEV_ERROR),
                                    true);
                            error = true;
                            break outerLoop;
                        }
                    }
                }

            }


            /*We are out of the outerLoop*/
            ba = baos.toByteArray();
            dos.close();

            if (error == true) {
                if (errCode == PulseDecoder.PD_EOF || errCode == PulseDecoder.PD_ERROR || errCode == WavePulseDecoder.PD_USER_BREAK) {
                    return false;
                } else {
                    return true;
                }
            }

        } catch (IOException e) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR), true);
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

        String xtension = ".kbdat";

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
}
