package turbodecoder.decoder;

import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class Turbo2000FileDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "T2000";
    /**
     * Associated decoder
     */
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

        if (config != null && config.genPreferAdaptiveSpeedDetection == true) {
            decoder = new SuperTurboBlockDecoder(d, config, 256);
        } else {
            decoder = new Turbo2000BlockDecoder(d, config, 256);
        }

        decoder.setBlockDecoderListener(this);

        /*Arrays for header and data*/
        int[] header;
        int[] data;

        /*Results of decoding*/
        BlockDecodeResult bdr;
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

            /*Block physically OK*/
            if (BlockDecodeResult.isCodeOK(errCode)) {
                /*Check whether it seems to be T2K header. If true, break
                  the headerDecode loop*/
                if (bdr.getData()[0] == 0) {
                    break;
                }
            }

            continue;

        }

        /*Header was decoded*/
        header = bdr.getData();

        /*Write header to the log*/
        log.addMessage(
                new DecoderMessage(MSG_PFX, "HEADER: " + Utils.t2kHeaderToString(header) + " <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        /*Obtain file size from the header*/
        int size = header[14] + header[15] * 256;
        size += 2;

        /*Decode data block*/
        bdr = decoder.decodeBlock(size);
        errCode = bdr.getErrorCode();

        /*Immediate break ?*/
        if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return false;
        }

        /*Block physically or logically bad*/
        if (BlockDecodeResult.isCodePhysicalError(errCode)) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return true;
        }


        /*Data block was decoded correctly*/
        data = bdr.getData();

        /*Check the first byte of the block*/
        if (data[0] != 255) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, "ERROR: First byte of data is not 255", DecoderMessage.SEV_ERROR),
                    true);
            return true;
        }

        /*Construct file specifier from the header*/
        String fspec = constructFilespec(outdir, header, header[1], firstFileSample, config.genPrependSampleNumber, config.csTurboAlwaysSaveAsBinary);

        File f = new File(fspec);
        f.delete();


        /*Flush output*/
        FileOutputStream fos;
        BufferedOutputStream bos;
        DataOutputStream dos = null;

        try {

            fos = new FileOutputStream(fspec);
            bos = new BufferedOutputStream(fos);
            dos = new DataOutputStream(bos);

            /*Not binary file, write full data*/
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

            if (saveAsBinary == false) {
                for (int i = 1; i < size - 1; i++) {
                    dos.writeByte(data[i]);
                }
            } else {
                /*Binary file*/
                dos.writeByte(255);
                dos.writeByte(255);

                /*Start address*/
                dos.writeByte(header[12]);
                dos.writeByte(header[13]);

                int load = header[12] + 256 * header[13];
                load += (size - 2);
                load--;

                /*End address*/
                dos.writeByte(load % 256);
                dos.writeByte(load / 256);

                for (int i = 1; i < size - 1; i++) {
                    dos.writeByte(data[i]);
                }
                /*Run*/
                dos.writeByte(736 % 256);
                dos.writeByte(736 / 256);
                dos.writeByte(737 % 256);
                dos.writeByte(737 / 256);

                dos.writeByte(header[16]);
                dos.writeByte(header[17]);
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
                new DecoderMessage(MSG_PFX, "SAVE: " + fspec + " <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_SAVE),
                true);

        /*Save header if asked*/
        if (config.csTurboSaveHeaderToExtraFile == true) {
            saveHeader(fspec + ".theader", header);
        }

        return true;
    }

    String constructFilespec(String outdir, int[] data, int tp, long sample, boolean prependSample, boolean alwaysBinary) throws Exception {

        /*First, we construct namebase, removing all
         *dangerous characters*/
        String nameBase = "";
        if (prependSample == true) {
            nameBase += Utils.padZeros(Long.toString(sample), 10);
            nameBase += '_';
        }
        nameBase += Utils.getPolishedFilespec(data, 2, 10);

        String xtension;

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

        String retVal;

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

    private void saveHeader(String hdrSpec, int[] headerData) {
        try {
            try (FileWriter fw = new FileWriter(hdrSpec)) {
                fw.write(Utils.t2kHeaderToHeaderFile(headerData));
                fw.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
