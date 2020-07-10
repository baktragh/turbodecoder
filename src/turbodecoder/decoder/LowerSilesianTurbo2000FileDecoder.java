package turbodecoder.decoder;

import java.io.*;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 *
 * @author  
 */
public class LowerSilesianTurbo2000FileDecoder implements FileDecoder, BlockDecoderListener {

    private BlockDecoder blockDecoder;
    private BlockDecoder blockDecoder2;
    private String msgPrefix;
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

        switch (config.lowerSilesianTurbo2000FileFormat) {
            case DecoderConfig.PL_LOWER_SILESIAN_TURBO_2000_FORMAT_AUTOTURBO: {

                blockDecoder = new LowerSilesianTurbo2000BlockDecoder(d, config, 256, LowerSilesianTurbo2000BlockDecoder.MANGLE_NONE);
                blockDecoder.setBlockDecoderListener(this);
                return decodeAutoTurbo(outdir, d, config);
            }
            case DecoderConfig.PL_LOWER_SILESIAN_TURBO_2000_FORMAT_UE_PROTECTED:
            case DecoderConfig.PL_LOWER_SILESIAN_TURBO_2000_FORMAT_UE_UNPROTECTED: {
                blockDecoder = new LowerSilesianTurbo2000BlockDecoder(d, config, 24, LowerSilesianTurbo2000BlockDecoder.MANGLE_NONE);
                blockDecoder.setBlockDecoderListener(this);
                return decodeUnknownExterminator(outdir, d, config);
            }
            case DecoderConfig.PL_LOWER_SILESIAN_TURBO_2000_FORMAT_FC10_PROTECTED: {
                blockDecoder = new LowerSilesianTurbo2000BlockDecoder(d, config, 256, LowerSilesianTurbo2000BlockDecoder.MANGLE_NONE);
                blockDecoder.setBlockDecoderListener(this);
                blockDecoder2 = new LowerSilesianTurbo2000BlockDecoder(d, config, 256, LowerSilesianTurbo2000BlockDecoder.MANGLE_FUNNY_COPY_10);
                blockDecoder2.setBlockDecoderListener(this);
                return decodeFunnyCopy10Protected(outdir, d, config);
            }
        }

        return false;

    }

    String constructFilespec(String outdir, int[] data, int tp, long sample, boolean prependSample, boolean hasName) throws Exception {

        /*First we prepend sample number. We do this always if there is no file name*/
        String nameBase = "";
        if (prependSample == true || hasName == false) {
            nameBase += Utils.padZeros(Long.toString(sample), 10);
            nameBase += '_';
        }

        if (hasName) {
            /*Then we add polished name taken from the header*/
            nameBase += Utils.getPolishedFilespec(data, 2, 10);
        } else {
            nameBase += "no_name";
        }

        /*Then we handle output directory*/
        File odf = new File(outdir);
        outdir = odf.getCanonicalPath();
        String retVal;

        String xtension = ".xex";
        if (nameBase.toUpperCase().endsWith(xtension.toUpperCase())) {
            xtension = "";
        }

        /*And append file extension*/
        if (outdir.endsWith(TurboDecoder.SP)) {
            retVal = outdir + nameBase + xtension;
        } else {
            retVal = outdir + TurboDecoder.SP + nameBase + xtension;
        }
        return retVal;

    }

    private boolean decodeAutoTurbo(String outdir, PulseDecoder d, DecoderConfig config) throws Exception {
        /*Arrays for header and data*/
        int[] header;
        int[] data;

        /*Message prefix*/
        msgPrefix = "LST2000 AT";

        /*Results of decoding*/
        BlockDecodeResult bdr;
        int errCode;

        /*Try to decode header*/
        while (true) {

            /*Decode 19 bytes block*/
            firstFileSample = d.getCurrentSample();

            bdr = blockDecoder.decodeBlock(19);
            errCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Block physically OK*/
            if (BlockDecodeResult.isCodeOK(errCode)) {
                /*Check whether it seems to be Lower silesiau Turbo 2000 header. If true, break
                  the headerDecode loop*/
                if (bdr.getData()[0] == 0) {
                    break;
                }
            }

        }

        /*Header was decoded*/
        header = bdr.getData();

        /*Write header to the log*/
        log.addMessage(
                new DecoderMessage(msgPrefix, "HEADER: " + Utils.t2kHeaderToString(header) + " <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        /*Obtain file size from the header*/
        int size = header[14] + header[15] * 256;

        /*Add two bytes - check sum and indentification byte*/
        size += 2;

        /*Decode data block*/
        bdr = blockDecoder.decodeBlock(size);
        errCode = bdr.getErrorCode();

        /*Immediate break ?*/
        if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return false;
        }

        /*Block physically or logically bad*/
        if (BlockDecodeResult.isCodePhysicalError(errCode)) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return true;
        }

        /*Data block was decoded correctly*/
        data = bdr.getData();

        /*Check the first byte of the block*/
        if (data[0] != 255) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, "ERROR: First byte of data is not 255", DecoderMessage.SEV_ERROR),
                    true);
            return true;
        }

        /*Construct file specifier from the header*/
        String fspec = constructFilespec(outdir, header, header[1], firstFileSample, config.genPrependSampleNumber, true);

        File f = new File(fspec);
        f.delete();

        /*Flush output*/
        FileOutputStream fos;
        BufferedOutputStream bos = null;

        try {

            fos = new FileOutputStream(fspec);
            bos = new BufferedOutputStream(fos);

            for (int i = 1; i < size - 1; i++) {
                bos.write(data[i]);
            }

            /*Finish file operation*/
            bos.flush();
            bos.close();
        } catch (IOException e) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
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
                new DecoderMessage(msgPrefix, "SAVE: " + fspec + " <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_SAVE),
                true);
        return true;

    }

    private boolean decodeUnknownExterminator(String outdir, PulseDecoder d, DecoderConfig config) throws Exception {

        BlockDecodeResult bdr;
        int errorCode;

        int[] dummyRunSegment;
        int[] segmentHeader;

        /*Message prefix*/
        msgPrefix = "LST2000 UE";

        if (config.lowerSilesianTurbo2000FileFormat == DecoderConfig.PL_LOWER_SILESIAN_TURBO_2000_FORMAT_UE_PROTECTED) {
            /*Try to decode dummy RUN segment*/
            while (true) {
                firstFileSample = d.getCurrentSample();
                bdr = blockDecoder.decodeBlock(3);
                errorCode = bdr.getErrorCode();

                /*Immediate break ?*/
                if (BlockDecodeResult.isCodeImmediateBreak(errorCode)) {
                    log.addMessage(
                            new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                            true);
                    return false;
                }

                /*Is block phyisically and logically OK?*/
                if (BlockDecodeResult.isCodeOK(errorCode)) {
                    dummyRunSegment = bdr.getData();
                    if (dummyRunSegment[0] == 0xE0 && dummyRunSegment[1] == 0x02) {
                        break;
                    }
                }
            }

            /*Dummy run segment decoded OK*/
            log.addMessage(
                    new DecoderMessage(msgPrefix, "Initial dummy RUN segment header found <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                    true);
        }

        /*Keep reading segment header and segment data pairs*/
        FileDataCache cache = new FileDataCache(16_384);
        cache.add(255);
        cache.add(255);

        while (true) {

            /*Read segment header*/
            bdr = blockDecoder.decodeBlock(5);
            errorCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errorCode)) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Any other error*/
            if (!BlockDecodeResult.isCodeOK(errorCode)) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, "ERROR: Segment header block not found or corrupt<" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_ERROR),
                        true);
                return true;
            }

            /*Check validity of the segment header*/
            segmentHeader = bdr.getData();
            int firstAddr = segmentHeader[0] + segmentHeader[1] * 256;
            int lastAddr = segmentHeader[2] + segmentHeader[3] * 256;

            /*Is that termination segment header*/
            if (firstAddr == 0 && lastAddr == 0) {
                break;
            }

            /*Is that valid segment header*/
            if (lastAddr < firstAddr) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, "ERROR: Segment header block corrupt - negative segment size  <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_ERROR),
                        true);
                return true;
            }

            /*Place segment header to the cache*/
            cache.add(segmentHeader, 4);
            if (config.genVerboseMessages == true) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, "Segment header: " + Integer.toString(firstAddr) + "-" + Integer.toString(lastAddr) + " <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_DETAIL),
                        true);
            }

            /*Read segment data*/
            bdr = blockDecoder.decodeBlock(lastAddr - firstAddr + 1 + 1);
            errorCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errorCode)) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Any other error*/
            if (!BlockDecodeResult.isCodeOK(errorCode)) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, "ERROR: Segment data block not found or corrupt <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_ERROR),
                        true);
                return true;
            }

            /*Place segment data to the cache*/
            cache.add(bdr.getData(), bdr.getData().length - 1);
            if (config.genVerboseMessages == true) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, "Segment data: " + Integer.toString(bdr.getData().length) + " bytes <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_DETAIL),
                        true);
            }

        }

        String fspec = constructFilespec(outdir, null, 0, firstFileSample, config.genPrependSampleNumber, false);

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
                    new DecoderMessage(msgPrefix, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
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
                new DecoderMessage(msgPrefix, "SAVE: " + fspec + " <" + bdr.getFullErrorMessage() + ">", DecoderMessage.SEV_INFO),
                true);
        return true;
    }

    /**
     * Decode Funny Copy 1.0 protected format
     */
    private boolean decodeFunnyCopy10Protected(String outdir, PulseDecoder d, DecoderConfig config) throws Exception {
        /*Arrays for header and data*/
        int[] header;
        int[] data;

        /*Message prefix*/
        msgPrefix = "LST2000 FC10P";

        /*Results of decoding*/
        BlockDecodeResult bdr;
        int errCode;

        /*Try to decode header*/
        while (true) {

            /*Decode 19 bytes block - header of the loader*/
            firstFileSample = d.getCurrentSample();

            bdr = blockDecoder.decodeBlock(19);
            errCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Block physically OK*/
            if (BlockDecodeResult.isCodeOK(errCode)) {
                /*Check whether it seems to be Lower silesiau Turbo 2000 header. If true, break
                  the headerDecode loop*/
                if (bdr.getData()[0] == 0) {
                    break;
                }
            }

        }

        /*Header was decoded*/
        header = bdr.getData();

        /*Write header to the log*/
        log.addMessage(
                new DecoderMessage(msgPrefix, "HEADER: " + Utils.t2kHeaderToString(header) + " <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        /*Obtain file size from the header*/
        int size = header[14] + header[15] * 256;

        /*Add two bytes - check sum and indentification byte*/
        size += 2;

        /*Decode data block*/
        bdr = blockDecoder.decodeBlock(size);
        errCode = bdr.getErrorCode();

        /*Immediate break ?*/
        if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return false;
        }

        /*Block physically or logically bad*/
        if (BlockDecodeResult.isCodePhysicalError(errCode)) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return true;
        }

        /*Data block was decoded correctly*/
        data = bdr.getData();

        /*Check the first byte of the block*/
        if (data[0] != 255) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, "ERROR: Special loader not decoded. First byte of data is not 255", DecoderMessage.SEV_ERROR),
                    true);
            return true;
        }

        int mangledPartSize = (data[0x0F + 1] + 256 * data[0x10 + 1]) - 80;

        /*Report that protected format loader has been loaded*/
        log.addMessage(
                new DecoderMessage(msgPrefix, "Special loader decoded. Mangled data size: " + Integer.toString(mangledPartSize) + " bytes. <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        /*Load block of 82 bytes. Purpose of the block is simply unknown*/
 /*Decode data block*/
        bdr = blockDecoder.decodeBlock(82);
        errCode = bdr.getErrorCode();

        /*Immediate break ?*/
        if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return false;
        }

        /*Block physically or logically bad*/
        if (BlockDecodeResult.isCodePhysicalError(errCode)) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                    true);
            return true;
        }

        /*Report that block of unknown purpose has been loaded*/
        log.addMessage(
                new DecoderMessage(msgPrefix, "Pre-data block decoded <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                true);

        /*Load mangled blocks that hold the binary file*/
        FileDataCache cache = new FileDataCache();

        int maxPossibleDataSize = mangledPartSize;
        int totalDataBytesDecoded = 0;

        while (true) {

            /*Decode data block*/
            bdr = blockDecoder2.decodeBlock(maxPossibleDataSize + 2, totalDataBytesDecoded == 0);
            errCode = bdr.getErrorCode();

            /*Immediate break ?*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Block physically or logically bad*/
            if (BlockDecodeResult.isCodePhysicalError(errCode)) {
                log.addMessage(
                        new DecoderMessage(msgPrefix, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return true;
            }

            int validBytes = bdr.getValidBytes();
            totalDataBytesDecoded += (validBytes - 2);

            /*Report that block had been loaded*/
            log.addMessage(
                    new DecoderMessage(msgPrefix, "Mangled data block decoded: " + Integer.toString(validBytes - 2) + " bytes. <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                    true);
            data = bdr.getData();

            /*Place valid bytes to the data cache*/
            for (int i = 0; i < validBytes - 2; i++) {
                cache.add(data[i + 1]);
            }

            if (totalDataBytesDecoded >= mangledPartSize) {
                break;
            }

        }

        /*Construct file specifier from the header*/
        String fspec = constructFilespec(outdir, header, header[1], firstFileSample, config.genPrependSampleNumber, true);

        File f = new File(fspec);
        f.delete();

        /*Flush output*/
        FileOutputStream fos;
        BufferedOutputStream bos = null;

        try {

            fos = new FileOutputStream(fspec);
            bos = new BufferedOutputStream(fos);

            int[] finalData = cache.getBytes();

            for (int i = 0; i < finalData.length; i++) {
                bos.write(finalData[i]);
            }

            /*Finish file operation*/
            bos.flush();
            bos.close();
        } catch (IOException e) {
            log.addMessage(
                    new DecoderMessage(msgPrefix, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
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
                new DecoderMessage(msgPrefix, "SAVE: " + fspec, DecoderMessage.SEV_INFO),
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
                    new DecoderMessage("LST2000", (String) eventInfo, DecoderMessage.SEV_DETAIL),
                    true);
        }
        log.impulse(true);
    }

}
