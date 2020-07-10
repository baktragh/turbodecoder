package turbodecoder.decoder;

import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class ASTFileDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "AST";
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
        ASTBlockDecoder blockDecoder = new ASTBlockDecoder(d, config);
        blockDecoder.setBlockDecoderListener(this);


        /*Arrays for header and data*/
        int[] header;

        /*Results of decoding*/
        BlockDecodeResult bdr;
        int errCode;

        /*Try to decode header*/
        headerDecode:
        while (true) {

            firstFileSample = d.getCurrentSample();

            /*Decode 256 bytes block - AST header*/
            blockDecoder.setExpectedChecksum(0);
            bdr = blockDecoder.decodeBlock(256);
            errCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), DecoderMessage.SEV_ERROR), true);
                return false;
            }

            /*Block physically OK*/
            if (BlockDecodeResult.isCodeOK(errCode)) {
                break;
            }

            continue;

        }

        /*Header was decoded*/
        header = bdr.getData();
        /*Write header to the log*/

        log.addMessage(
                new DecoderMessage(MSG_PFX, "HEADER: " + getHeaderString(header) + " <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true
        );


        /*Prepare to keep data of all segments*/
        int[][] segmentData = new int[header[0]][];

        /*For all segments*/
        int lnIndex = 4;

        for (int segNo = 0; segNo < header[0]; segNo++) {

            /*Decode block of a length given by the header and set expected checksum*/
            blockDecoder.setExpectedChecksum(header[201 + segNo]);
            bdr = blockDecoder.decodeBlock(header[lnIndex] + 256 * header[lnIndex + 1]);
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
            segmentData[segNo] = bdr.getData();
            lnIndex += 4;
        }


        /*Construct file specifier from the header*/
        String fspec = constructFilespec(outdir, header, firstFileSample, config.genPrependSampleNumber);

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

            /*Binary file*/
            dos.writeByte(255);
            dos.writeByte(255);

            int saIndex = 2;
            lnIndex = 4;

            /*Write all segments*/
            for (int i = 0; i < header[0]; i++) {

                /*Prepare segment header*/
                int startAddr = header[saIndex] + 256 * header[saIndex + 1];
                int length = header[lnIndex] + 256 * header[lnIndex + 1];
                int endAddr = startAddr + length - 1;

                /*Write segment header*/
                dos.writeByte(header[saIndex]);
                dos.writeByte(header[saIndex + 1]);
                dos.writeByte(endAddr % 256);
                dos.writeByte(endAddr / 256);

                /*Write segment data*/
                for (int j = 0; j < segmentData[i].length; j++) {
                    dos.writeByte(segmentData[i][j]);
                }

                /*Next pair of start address and length*/
                saIndex += 4;
                lnIndex += 4;

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
                new DecoderMessage(MSG_PFX, "SAVE: " + fspec + " <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_SAVE),
                true);
        return true;
    }

    String constructFilespec(String outdir, int[] header, long sample, boolean prependSample) throws Exception {

        /*First, we construct namebase, removing all
         *dangerous characters*/
        String nameBase = "";
        if (prependSample == true) {
            nameBase += Utils.padZeros(Long.toString(sample), 10);
            nameBase += '_';
        }
        /*Prepare file name*/
        char[] nameChars = new char[20];
        for (int i = 180; i < 200; i++) {
            nameChars[i - 180] = (char) header[i];
        }
        Utils.internalToAscii(nameChars);
        nameBase += Utils.getPolishedFilespec(nameChars, 0, 20);

        String xtension;

        if (nameBase.toUpperCase().endsWith(".XEX")) {
            xtension = "";
        } else {
            xtension = ".xex";
        }

        File odf = new File(outdir);
        outdir = odf.getCanonicalPath();

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

    private String getHeaderString(int[] header) {

        /*Prepare file name*/
        char[] nameChars = new char[20];
        for (int i = 180; i < 200; i++) {
            nameChars[i - 180] = (char) header[i];
        }
        Utils.internalToAscii(nameChars);

        StringBuilder sb = new StringBuilder();
        sb.append(nameChars);
        sb.append(' ');
        sb.append("BLKS:");
        sb.append(header[0]);
        return sb.toString();

    }

}
