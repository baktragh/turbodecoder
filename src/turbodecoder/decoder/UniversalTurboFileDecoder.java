package turbodecoder.decoder;

import turbodecoder.decoder.pulse.PulseDecoder;
import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class UniversalTurboFileDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "UniT";
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

        this.log = log;
        /*Create block decoders*/
        BlockDecoder headerDecoder = new SuperTurboBlockDecoder(d, config, true, 1_024);
        decoder = new SuperTurboBlockDecoder(d, config, 1_024);

        headerDecoder.setBlockDecoderListener(this);
        decoder.setBlockDecoderListener(this);

        /*Arrays for header and data*/
        int[] header = null;
        int[] data = null;
        int aux = CS_SUPER_TURBO;

        /*Result of decoding*/
        BlockDecodeResult bdr = null;
        int errCode;

        /*Try to decode header*/
        headerDecode:
        while (true) {


            /*Decode header block (can be 19 or 29 bytes)*/
            firstFileSample = d.getCurrentSample();
            bdr = headerDecoder.decodeBlock(0);
            errCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Whatever header found?*/
            if (BlockDecodeResult.isCodeOK(errCode) && (bdr.getValidBytes() == 19 || bdr.getValidBytes() == 29)) {
                aux = bdr.getAux();
                if (bdr.getData()[0] == 183 && aux == CS_SUPER_TURBO) {
                    break;
                }
                if (bdr.getData()[0] == 0 && aux == CS_TURBO_2000) {
                    break;
                }
            }

            /*Header not decoded for any other reasons, retry*/
            continue;

        }

        /*Either header was decoded*/
        header = bdr.getData();
        int fSize = 0;
        int fLoad = 0;
        int fRun = 0;
        int fLastAddr = 0;

        /*Write header to the log*/
        log.addMessage(
                new DecoderMessage(MSG_PFX, "HEADER: " + ((aux == CS_SUPER_TURBO) ? Utils.stHeaderToString(header) : Utils.t2kHeaderToString(header)) + " <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);


        /*Obtain file size*/
        if (aux == CS_SUPER_TURBO) {
            fSize = header[24] + header[25] * 256;
            fLoad = header[22] + header[23] * 256;
            fRun = header[26] + header[27] * 256;
            fLastAddr = fLoad + fSize - 1;
        } else {
            fSize = header[14] + header[15] * 256;
            fLoad = header[12] + header[13] * 256;
            fRun = header[16] + header[17] * 256;
            fLastAddr = fLoad + fSize - 1;
        }


        /*Decode data block*/
        bdr = decoder.decodeBlock(fSize + 2);
        errCode = bdr.getErrorCode();


        /*Immediate break ?*/
        if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return false;
        }

        /*Block physically bad*/
        if (BlockDecodeResult.isCodePhysicalError(errCode)) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return true;
        }

        /*Block physically correct*/
        data = bdr.getData();

        if ((aux == CS_SUPER_TURBO && data[0] != 237) || (aux == CS_TURBO_2000 && data[0] != 255)) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, "ERROR: First byte of data is not 237 or 255", DecoderMessage.SEV_ERROR),
                    true);
            return true;
        }

        String fspec = constructFilespec(outdir, header, header[1], firstFileSample, config.genPrependSampleNumber, config.csTurboAlwaysSaveAsBinary, aux == CS_SUPER_TURBO ? 20 : 10);

        File f = new File(fspec);
        f.delete();


        /*Flush output*/
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        DataOutputStream dos = null;

        try {
            fos = new FileOutputStream(fspec);
            bos = new BufferedOutputStream(fos);
            dos = new DataOutputStream(bos);

            /*Save as binary or not*/
            boolean saveAsBinary = false;

            switch (header[1]) {

                /*Native binary format always as binary*/
                case 3: {
                    saveAsBinary = true;
                    break;
                }
                /*Binary turbo already is a binary*/
                case 4: {
                    saveAsBinary = false;
                    break;
                }
                /*BASIC - never save as binary*/
                case 255: {
                    saveAsBinary = false;
                    break;
                }

                /*Other files - let us determine the configuration*/
                default: {
                    saveAsBinary = config.csTurboAlwaysSaveAsBinary;
                    break;
                }
            }

            /*Not binary file,write full data*/
            if (saveAsBinary == false) {
                for (int i = 0; i < fSize; i++) {
                    dos.writeByte(data[i + 1]);
                }
            } else {
                /*Binary file*/
                dos.writeByte(255);
                dos.writeByte(255);

                /*Load address*/
                dos.writeByte(fLoad % 256);
                dos.writeByte(fLoad / 256);

                /*End address*/
                dos.writeByte(fLastAddr % 256);
                dos.writeByte(fLastAddr / 256);

                for (int i = 0; i < fSize; i++) {
                    dos.writeByte(data[i + 1]);
                }
                /*Run*/
                dos.writeByte(736 % 256);
                dos.writeByte(736 / 256);
                dos.writeByte(737 % 256);
                dos.writeByte(737 / 256);

                dos.writeByte(fRun % 256);
                dos.writeByte(fRun / 256);
            }

            /*Finish file operation*/
            dos.flush();
            dos.close();
        } catch (IOException e) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
                    true);
            try {
                dos.close();
            } catch (IOException xe) {
                /*Intentionally left blank*/
            }
            return true;
        }

        log.addMessage(
                new DecoderMessage(MSG_PFX, "SAVE: " + fspec + " <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_INFO),
                true);

        /*Save header if asked*/
        if (config.csTurboSaveHeaderToExtraFile == true) {
            saveHeader(fspec + ".theader", header, aux);
        }

        return true;
    }

    String constructFilespec(String outdir, int[] data, int tp, long sample, boolean prependSample, boolean alwaysBinary, int maxChars) throws Exception {

        String nameBase = "";
        if (prependSample == true) {
            nameBase += Utils.padZeros(Long.toString(sample), 10);
            nameBase += '_';
        }
        nameBase += Utils.getPolishedFilespec(data, 2, maxChars);

        String xtension = "";

        /*We will determine file extension according to file type*/
        if (alwaysBinary == true) {
            xtension = ".xex";
        } else {
            switch (tp) {
                case 254: {

                }
                case 255: {
                    xtension = ".bas";
                    break;
                }
                case 3: {
                }
                case 4: {
                    xtension = ".xex";
                    break;
                }
                default: {
                    xtension = ".dat";
                }

            }
        }

        File odf = new File(outdir);
        outdir = odf.getCanonicalPath();

        if (nameBase.toUpperCase().endsWith(xtension.toUpperCase())) {
            xtension = "";
        }

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

    private void saveHeader(String hdrSpec, int[] headerData, int aux) {
        try {
            try (FileWriter fw = new FileWriter(hdrSpec)) {
                if (aux == CS_SUPER_TURBO) {
                    fw.write(Utils.stHeaderToHeaderFile(headerData));
                } else {
                    fw.write(Utils.t2kHeaderToHeaderFile(headerData));
                }
                fw.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
