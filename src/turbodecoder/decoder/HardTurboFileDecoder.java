package turbodecoder.decoder;

import turbodecoder.decoder.pulse.PulseDecoder;
import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 * Hard turbo file decoder
 */
public class HardTurboFileDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "HT";
    private BlockDecoder blockDecoder;
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
        blockDecoder = new Turbo2000BlockDecoder(d, config, 256);
        blockDecoder.setBlockDecoderListener(this);

        return decodeNaturalFormat(outdir, d, config);

    }

    String constructFilespec(String outdir, int[] header, int tp, long sample, boolean prependSample, boolean hasName) throws Exception {

        /*First we prepend sample number. We do this always if there is no file name*/
        String nameBase = "";
        if (prependSample == true || hasName == false) {
            nameBase += Utils.padZeros(Long.toString(sample), 10);
            nameBase += '_';
        }

        if (hasName) {
            /*Then we add polished name taken from the header*/
            nameBase += Utils.getPolishedFilespec(header, 1, 39);
        } else {
            nameBase += "no_name";
        }

        /*Then we handle output directory*/
        File odf = new File(outdir);
        outdir = odf.getCanonicalPath();
        String retVal;

        String xtension;
        if (nameBase.toUpperCase().endsWith(".XEX")) {
            xtension = "";
        } else {
            xtension = ".xex";
        }

        /*And append file extension*/
        if (outdir.endsWith(TurboDecoder.SP)) {
            retVal = outdir + nameBase + xtension;
        } else {
            retVal = outdir + TurboDecoder.SP + nameBase + xtension;
        }
        return retVal;

    }

    private boolean decodeNaturalFormat(String outdir, PulseDecoder d, DecoderConfig config) throws Exception {

        BlockDecodeResult bdr;
        int errorCode;

        int[] mainHeader;
        int[] segmentHeader;
        int[] segmentData;

        /*Try to decode main header segment (41 bytes)*/
        while (true) {
            firstFileSample = d.getCurrentSample();
            bdr = blockDecoder.decodeBlock(41);
            errorCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errorCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()), true);
                return false;
            }

            /*Is block phyisically and logically OK?*/
            if (BlockDecodeResult.isCodeOK(errorCode)) {
                mainHeader = bdr.getData();
                if (mainHeader[0] != 0) {
                    continue;
                }
                break;
            }
        }

        /*Get file name, polish it a bit*/
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < 40; i++) {
            if (mainHeader[i] == 155) {
                 mainHeader[i] = ' ';
            }
            sb.append((char) mainHeader[i]);
        }
        String headerFileName = sb.toString();
        log.addMessage(
                new DecoderMessage(MSG_PFX, "Main header: " + headerFileName + "<" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        /*Keep reading segment header and segment data pairs*/
        FileDataCache cache = new FileDataCache(16_384);
        cache.add(255);
        cache.add(255);

        /*Decode segment header, segment data pairs*/
        while (true) {

            /*Read segment header*/
            bdr = blockDecoder.decodeBlock(6);
            errorCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errorCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Any other error*/
            if (!BlockDecodeResult.isCodeOK(errorCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "ERROR: Segment header block not found or corrupt<" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_ERROR),
                        true);
                return true;
            }

            /*Check validity of the segment header*/
            segmentHeader = bdr.getData();

            /*Check identification byte*/
            if (segmentHeader[0] != 255) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "ERROR: Segment header block corrupt - first byte is not 255  <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_ERROR),
                        true);
                return true;
            }

            /*Check segment addresses*/
            int firstAddr = segmentHeader[1] + segmentHeader[2] * 256;
            int lastAddr = segmentHeader[3] + segmentHeader[4] * 256;

            /*Correction of last address*/
            lastAddr -= 1;

            /*Is that termination segment header*/
            if (firstAddr == 65_535) {
                break;
            }

            /*Is that valid segment header*/
            if (lastAddr < firstAddr) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "ERROR: Segment header block corrupt - negative segment size  <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_ERROR),
                        true);
                return true;
            }

            /*Place segment header to the cache*/
            cache.add(firstAddr % 256);
            cache.add(firstAddr / 256);
            cache.add(lastAddr % 256);
            cache.add(lastAddr / 256);
            if (config.genVerboseMessages == true) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "Segment header: " + Integer.toString(firstAddr) + "-" + Integer.toString(lastAddr) + " <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_DETAIL),
                        true);
            }

            /*Read segment data*/
            bdr = blockDecoder.decodeBlock(lastAddr - firstAddr + 1 + 2);
            errorCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errorCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Any other error*/
            if (!BlockDecodeResult.isCodeOK(errorCode)) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "ERROR: Segment data block not found or corrupt <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_ERROR),
                        true);
                return true;
            }

            segmentData = bdr.getData();

            /*Check identification byte*/
            if (segmentData[0] != 255) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "ERROR: Segment data block corrupt - first byte is not 255  <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_ERROR),
                        true);
                return true;
            }

            /*Place segment data to the cache*/
            for (int i = 1; i < segmentData.length - 1; i++) {
                cache.add(segmentData[i]);
            }
            if (config.genVerboseMessages == true) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "Segment data: " + Integer.toString(segmentData.length) + " bytes <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_DETAIL),
                        true);
            }

        }

        String fspec = constructFilespec(outdir, mainHeader, 0, firstFileSample, config.genPrependSampleNumber, true);

        /*Decoding finished - save binary file*/
        FileOutputStream fos;
        BufferedOutputStream bos = null;

        int[] data = cache.getBytes();

        try {

            fos = new FileOutputStream(fspec);
            bos = new BufferedOutputStream(fos);

            for (int i = 0; i < data.length; i++) {
                bos.write(data[i]);
            }

            /*Finish file operation*/
            bos.flush();
            bos.close();
        } catch (IOException e) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
                    true);
            try {
                if (bos != null) {
                    bos.close();
                }
            } catch (IOException xe) {
                xe.printStackTrace();
            }
            return true;
        }

        log.addMessage(
                new DecoderMessage(MSG_PFX, "SAVE: " + fspec + " <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_SAVE),
                true);
        return true;

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
