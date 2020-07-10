package turbodecoder.decoder;

import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class TurboRomFileDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "TurboROM";
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

        if (config.turboROMFileFormat == DecoderConfig.PL_TURBO_ROM_FILE_FORMAT_BINARY) {
            return decodeBinary(outdir, log, d, config);
        } else {
            return decodeBasic(outdir, log, d, config);
        }

    }

    private boolean decodeBinary(String outdir, DecoderLog log, PulseDecoder d, DecoderConfig config) throws Exception {

        BlockDecoder headerDecoder = new TurboRomBlockDecoder(d, config, true, 0);
        BlockDecoder dataDecoder = null;

        /*Arrays for header and data*/
        int[] header;
        int[] data;

        /*Results of decoding*/
        BlockDecodeResult bdr;
        int errCode;

        headerDecoder.setBlockDecoderListener(this);

        /*Try to decode header*/
        headerDecode:
        while (true) {

            /*Decode 41 bytes block - header*/
            firstFileSample = d.getCurrentSample();

            bdr = headerDecoder.decodeBlock(41);
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
                if (checkHeaderBinary(bdr.getData()) == true) {
                    break;
                }
            }

            continue;

        }

        /*Header was decoded*/
        header = bdr.getData();

        /*Obtain basic information about the header*/
        int fSize = header[12] + header[13] * 256;
        int fInitAddr = (header[36] == 0) ? (header[8] + header[9] * 256) : -1;
        int fRunAddr = header[6] + header[7] * 256;
        int fLoadAddr = header[10] + header[11] * 256;
        int fProgramType = header[35];
        int fCheckSum = header[5];

        /*Write header to the log*/
        log.addMessage(
                new DecoderMessage(MSG_PFX, "HEADER: " + getHeaderStringBinary(header, fSize, fInitAddr, fRunAddr, fLoadAddr) + " <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);


        /*Decode data block*/
        dataDecoder = new TurboRomBlockDecoder(d, config, false, fCheckSum);
        dataDecoder.setBlockDecoderListener(this);
        bdr = dataDecoder.decodeBlock(fSize);
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

        /*Construct file specifier from the header*/
        String fspec = constructFilespec(outdir, header, firstFileSample, config.genPrependSampleNumber, ".xex");

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
            if (fProgramType != 1) {
                for (int i = 0; i < fSize; i++) {
                    dos.writeByte(data[i]);
                }
            } else {
                /*Binary file*/
                dos.writeByte(255);
                dos.writeByte(255);

                /*Start address*/
                dos.writeByte(fLoadAddr % 256);
                dos.writeByte(fLoadAddr / 256);

                /*End address*/
                dos.writeByte((fLoadAddr + fSize - 1) % 256);
                dos.writeByte((fLoadAddr + fSize - 1) / 256);

                for (int i = 0; i < fSize; i++) {
                    dos.writeByte(data[i]);
                }

                /*Init if specified*/
                if (fInitAddr != -1) {
                    dos.writeByte(738 % 256);
                    dos.writeByte(738 / 256);
                    dos.writeByte(739 % 256);
                    dos.writeByte(739 / 256);
                    dos.writeByte(fInitAddr % 256);
                    dos.writeByte(fInitAddr / 256);
                }

                /*Run*/
                dos.writeByte(736 % 256);
                dos.writeByte(736 / 256);
                dos.writeByte(737 % 256);
                dos.writeByte(737 / 256);
                dos.writeByte(fRunAddr % 256);
                dos.writeByte(fRunAddr / 256);
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
        return true;
    }

    private boolean decodeBasic(String outdir, DecoderLog log, PulseDecoder d, DecoderConfig config) throws Exception {

        BlockDecoder headerDecoder = new TurboRomBlockDecoder(d, config, true, 0);
        BlockDecoder dataDecoder = null;

        /*Arrays for header and data*/
        int[] header;
        int[] data;

        /*Results of decoding*/
        BlockDecodeResult bdr;
        int errCode;

        headerDecoder.setBlockDecoderListener(this);

        /*Try to decode header*/
        headerDecode:
        while (true) {

            /*Decode 78 bytes block - header*/
            firstFileSample = d.getCurrentSample();

            bdr = headerDecoder.decodeBlock(78);
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
                if (checkHeaderBasic(bdr.getData()) == true) {
                    break;
                }
            }

            continue;

        }

        /*Header was decoded*/
        header = bdr.getData();

        /*Obtain basic information about the header*/
        int fSize = header[12] + header[13] * 256;
        int fCheckSum = header[5];

        /*Write header to the log*/
        log.addMessage(
                new DecoderMessage(MSG_PFX, "HEADER: " + getHeaderStringBasic(header, fSize) + " <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        /*Decode data block*/
        dataDecoder = new TurboRomBlockDecoder(d, config, false, fCheckSum);
        dataDecoder.setBlockDecoderListener(this);
        bdr = dataDecoder.decodeBlock(fSize);
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

        /*Construct file specifier from the header*/
        String fspec = constructFilespec(outdir, header, firstFileSample, config.genPrependSampleNumber, ".bas");

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

            /*Update and write the header part*/
            int baseAddr = (header[60] + 256 * header[61]);

            for (int i = 0; i < 7; i++) {
                int addr = header[60 + 2 * i] + 256 * header[60 + 2 * i + 1];
                addr -= baseAddr;
                dos.writeByte(addr % 256);
                dos.writeByte(addr / 256);
            }

            /*Write main part of the BASIC file*/
            for (int i = 0; i < fSize; i++) {
                dos.writeByte(data[i]);
            }

            /*Finish file operation*/
            dos.flush();
            dos.close();

        } catch (IOException e) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR), true);
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
        return true;
    }

    String constructFilespec(String outdir, int[] header, long sample, boolean prependSample, String xtension) throws Exception {

        /*First, we construct namebase, removing all
         *dangerous characters*/
        String nameBase = "";
        if (prependSample == true) {
            nameBase += Utils.padZeros(Long.toString(sample), 10);
            nameBase += '_';
        }
        char[] nameChars = new char[20];
        for (int i = 15; i < 35; i++) {
            nameChars[i - 15] = (char) header[i];
        }
        Utils.internalToAscii(nameChars);
        nameBase += Utils.getPolishedFilespec(nameChars, 0, 20);

        File odf = new File(outdir);
        outdir = odf.getCanonicalPath();

        String retVal;

        if (nameBase.toUpperCase().endsWith(xtension.toUpperCase())) {
            xtension = "";
        }

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

    private boolean checkHeaderBinary(int[] header) {

        String message = null;

        /*Check header length*/
        if (header[3] != 40 || header[4] != 0) {
            message = "ERROR:Malformed header-Length bytes not 40, 0";

        } /*Padding 1*/ else if (header[14] != 0) {
            message = "ERROR:Malformed header-Padding byte at offset 14 not zero";

        } /*Program type*/ else if (header[35] != 1) {
            message = "ERROR:Malformed header-Program type byte not 1";

        } /*Padding zeros*/ else if (header[37] != 0 || header[38] != 0 || header[39] != 0) {
            message = "ERROR:Malformed header-Padding bytes at offset 37 not zero";

        } /*RTS*/ else if (header[40] != 96) {
            message = "ERROR:Malformed header-Last byte is not RTS opcode";

        }

        if (message != null) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, message, DecoderMessage.SEV_ERROR),
                    true);
            return false;
        }

        return true;
    }

    private String getHeaderStringBinary(int[] header, int fSize, int fInitAddr, int fRunAddr, int fLoadAddr) {

        /*Prepare file name*/
        char[] nameChars = new char[20];
        for (int i = 15; i < 35; i++) {
            nameChars[i - 15] = (char) header[i];
        }
        Utils.internalToAscii(nameChars);

        StringBuilder sb = new StringBuilder();
        sb.append(nameChars);
        sb.append(' ');
        sb.append("LO:");
        sb.append(fLoadAddr);
        sb.append(' ');
        sb.append("LN:");
        sb.append(fSize);
        sb.append(' ');
        sb.append("RU:");
        sb.append(fRunAddr);

        if (fInitAddr != -1) {
            sb.append(' ');
            sb.append("IN:");
            sb.append(fInitAddr);
        }

        return sb.toString();

    }

    private boolean checkHeaderBasic(int[] header) {

        String message = null;

        /*Check header length*/
        if (header[3] != 77 || header[4] != 0) {
            message = "ERROR:Malformed header-Length bytes not 77, 0";

        } /*Check INIT address is zero*/ else if (header[8] != 0 || header[9] != 0) {
            message = "ERROR:Malformed header-INIT address not 0";

        } /*Check padding byte*/ else if (header[14] != 0) {
            message = "ERROR:Malformed header-Padding byte at offset 14 not zero";

        } /*Check program type flag*/ else if (header[35] != 0) {
            message = "ERROR:Malformed header-Program type flag not 0";

        } /*Check INIT flag*/ else if (header[36] != 1) {
            message = "ERROR:Malformed header-INIT flag not set to 1";

        } /*Check another padding bytes*/ else if (header[37] != 0 || header[38] != 0 || header[39] != 0) {
            message = "ERROR:Malformed header-Bytes at offsets 37-39 not zeros";

        }

        if (message != null) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, message, DecoderMessage.SEV_ERROR),
                    true);
            return false;
        }

        return true;
    }

    private String getHeaderStringBasic(int[] header, int fSize) {

        /*Prepare file name*/
        char[] nameChars = new char[20];
        for (int i = 15; i < 35; i++) {
            nameChars[i - 15] = (char) header[i];
        }
        Utils.internalToAscii(nameChars);

        StringBuilder sb = new StringBuilder();
        sb.append(nameChars);
        sb.append(' ');
        sb.append("LN:");
        sb.append(fSize);

        return sb.toString();
    }

}
