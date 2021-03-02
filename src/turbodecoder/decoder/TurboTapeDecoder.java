package turbodecoder.decoder;

import turbodecoder.decoder.pulse.PulseDecoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 * Turbo Tape (and also B-TAPE) decoder
 */
public class TurboTapeDecoder implements FileDecoder, BlockDecoderListener {

    private static final String MSG_PFX = "TTAPE";

    private static final int FD_RESULT_OK = 0;
    private static final int FD_RESULT_TERMINATE = 1;
    private static final int FD_RESULT_GARBLED = 2;
    private static final int FD_RESULT_BADSEQ = 3;
    private final ArrayList<TTBlock> storedBlocks;
    private TTBlock lastStoredBlock;

    private DecoderLog log;
    private BlockDecoder decoder;
    private PulseDecoder pulseDecoder;
    private long firstFileSample;
    private DecoderConfig dConfig;

    /**
     *
     */
    public TurboTapeDecoder() {
        storedBlocks = new ArrayList<>();
        clearState();
    }

    private void clearState() {
        storedBlocks.clear();
        lastStoredBlock = null;

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

        /*Create block decoder*/
        decoder = new SuperTurboBlockDecoder(d, config, 64);
        pulseDecoder = d;
        dConfig = config;

        int errCode;
        BlockDecodeResult bdr;
        this.log = log;

        FileDecodeResult result = null;
        decoder.setBlockDecoderListener(this);

        /*First - try to decode block*/
        fileStart:

        while (true) {

            /*Clear state*/
            clearState();
            firstFileSample = pulseDecoder.getCurrentSample();

            /*Decode initial block and check the result*/
            bdr = decoder.decodeBlock(1_026);
            errCode = bdr.getErrorCode();

            /*Immediate termination*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {

                log.addMessage(
                        new DecoderMessage(MSG_PFX, bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                        true);
                return false;
            }

            /*Block successfully decoded*/
            if (BlockDecodeResult.isCodeOK(errCode)) {

                /*Create TTBlock*/
                TTBlock block = new TTBlock(bdr.getData());
                /*Check if it is starting block*/
                if (block.getSequenceNumber() != 1) {
                    continue;
                }

                /*Store that block*/
                storeBlock(block);

                /*Inform user that block found*/
                this.log.addMessage(
                        new DecoderMessage(MSG_PFX, "HEADER: " + block.getTitle() + " <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                        true);


                /*If EOF, flush and return true*/
                if (block.isEof() == true) {
                    flushFile(outdir);
                    return true;
                }

                /*Determine the method and continue to
                  *appropriate phase*/
                int md = block.getMode();
                if (md == TTBlock.TAPE_MODE_LS || md == TTBlock.TAPE_MODE_SS) {
                    result = decode_sd(false);
                } else {
                    result = decode_sd(true);
                }

                int rcode = result.code;

                switch (rcode) {
                    case FD_RESULT_OK: {
                        flushFile(outdir);
                        return true;
                    }
                    case FD_RESULT_TERMINATE: {
                        if (result.bdr != null) {
                            this.log.addMessage(
                                    new DecoderMessage(MSG_PFX, result.bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                                    true);
                        }
                        return false;
                    }
                    case FD_RESULT_GARBLED: {
                        if (result.bdr != null) {
                            this.log.addMessage(
                                    new DecoderMessage(MSG_PFX, "FATAL ERROR: " + result.bdr.getFullErrorMessage(), bdr.getCodeSeverity()),
                                    true);
                        }
                        return true;
                    }
                    case FD_RESULT_BADSEQ: {
                        this.log.addMessage(
                                new DecoderMessage(MSG_PFX, "FATAL ERROR: Incorrect sequence of blocks", DecoderMessage.SEV_ERROR),
                                true);
                        return true;
                    }
                }

            }
            /*Garbled block found*/
            continue;

        }/*Outer loop*/


    }

    /**
     * Decode file that is stored in double block mode
     */
    private FileDecodeResult decode_sd(boolean modeD) {

        final int ST_START = 0;
        final int ST_P1 = 3;
        final int ST_P2 = 4;

        int state = ST_START;


        /*Finite machine*/
        while (true) {

            switch (state) {

                /*State start ----------------------------------------------*/
                case ST_START: {

                    /*Input letter*/
                    BlockAttemptResult bar = attemptBlock(true, true, 15);

                    /*Transitions*/
                    switch (bar.code) {

                        /*Immediate termination*/
                        case BlockAttemptResult.TERMINATE: {
                            return new FileDecodeResult(FD_RESULT_TERMINATE, bar.bdr);
                        }

                        /*Physical error*/
                        case BlockAttemptResult.GARBLED: {
                            return new FileDecodeResult(FD_RESULT_GARBLED, bar.bdr);
                        }

                        /*Bad sequence*/
                        case BlockAttemptResult.BADSEQ: {
                            return new FileDecodeResult(FD_RESULT_BADSEQ, bar.bdr);
                        }

                        /*Duplicate*/
                        case BlockAttemptResult.DUPLICATE: {
                            state = ST_P1;
                            break;
                        }

                        /*Successor*/
                        case BlockAttemptResult.SUCCESSOR: {
                            /*Store*/
                            storeBlock(bar.block);
                            /*Other state*/
                            state = ST_P1;
                            break;
                        }

                    }

                    break;

                }

                /*State P1 ----------------------------------------------------*/
                case ST_P1: {

                    /*Check whether last stored block was EOF block*/
                    if (lastStoredBlock.isEof() == true) {
                        return new FileDecodeResult(FD_RESULT_OK, null);
                    }

                    /*Input letter*/
                    BlockAttemptResult bar = attemptBlock(true, modeD, 15);

                    /*Transitions*/
                    switch (bar.code) {

                        /*Immediate termination*/
                        case BlockAttemptResult.TERMINATE: {
                            return new FileDecodeResult(FD_RESULT_TERMINATE, bar.bdr);
                        }

                        /*Physical error*/
                        case BlockAttemptResult.GARBLED: {
                            return new FileDecodeResult(FD_RESULT_GARBLED, bar.bdr);
                        }

                        /*Bad sequence*/
                        case BlockAttemptResult.BADSEQ: {
                            return new FileDecodeResult(FD_RESULT_BADSEQ, bar.bdr);
                        }

                        /*Duplicate*/
                        case BlockAttemptResult.DUPLICATE: {
                            state = ST_P2;
                            break;
                        }

                        /*Successor*/
                        case BlockAttemptResult.SUCCESSOR: {
                            /*Store*/
                            storeBlock(bar.block);
                            /*Other state*/
                            state = ST_P1;
                            break;
                        }

                    }

                    break;

                }
                /*State P2 --------------------------------------------------------*/
                case ST_P2: {


                    /*Only for D mode, in this state,only accessor is
                      *accepted*/

 /*Input letter*/
                    BlockAttemptResult bar = attemptBlock(true, false, 15);

                    /*Transitions*/
                    switch (bar.code) {

                        /*Immediate termination*/
                        case BlockAttemptResult.TERMINATE: {
                            return new FileDecodeResult(FD_RESULT_TERMINATE, bar.bdr);
                        }

                        /*Physical error*/
                        case BlockAttemptResult.GARBLED: {
                            return new FileDecodeResult(FD_RESULT_GARBLED, bar.bdr);
                        }

                        /*Bad sequence*/
                        case BlockAttemptResult.BADSEQ: {
                            return new FileDecodeResult(FD_RESULT_BADSEQ, bar.bdr);
                        }

                        /*Successor*/
                        case BlockAttemptResult.SUCCESSOR: {
                            /*Store*/
                            storeBlock(bar.block);
                            /*Other state*/
                            state = ST_P1;
                            break;
                        }

                    }

                    break;
                }

            }

        }

    }

    /**
     * Attempt to read block
     *
     * @param successorAccepted Indicates whether successor is accepted
     * @param duplicateAccepted Indicates whether duplicate is accepted
     * @param garbledTimeout How many seconds of garbled data result into
     * timeout
     * @return Crate with result
     */
    private BlockAttemptResult attemptBlock(boolean successorAccepted, boolean duplicateAccepted, int garbledTimeout) {

        /*Mark position in the stream*/
        long errorMark = pulseDecoder.getCurrentSample();
        long sequenceMark;

        while (true) {

            /*Try to decode a block*/
            sequenceMark = pulseDecoder.getCurrentSample();

            BlockDecodeResult bdr = decoder.decodeBlock(1_026);
            int errCode = bdr.getErrorCode();

            /*Immediate termination*/
            if (BlockDecodeResult.isCodeImmediateBreak(errCode)) {
                return new BlockAttemptResult(BlockAttemptResult.TERMINATE, bdr, null);
            }

            /*Physical error*/
            if (BlockDecodeResult.isCodePhysicalError(errCode)) {
                /*Check the distance*/
                long distance = pulseDecoder.getCurrentSample() - errorMark;
                /*More than specified distance - garbled block*/
                if (distance > garbledTimeout * 44_100) {
                    return new BlockAttemptResult(BlockAttemptResult.GARBLED, bdr, null);
                } /*Another chance*/ else {
                    log.addMessage(
                            new DecoderMessage(MSG_PFX, "PROBLEM: <" + bdr.getFullErrorMessage() + ">", bdr.getCodeSeverity()),
                            true);
                    continue;
                }
            }

            /*Physically correct block available*/
            TTBlock block = new TTBlock(bdr.getData());

            /*Successor ?*/
            if (lastStoredBlock.isPredecesorOf(block) && successorAccepted == true) {

                if (!BlockDecodeResult.isCodePerfect(errCode)) {
                    log.addMessage(
                            new DecoderMessage(MSG_PFX, "PROBLEM: " + "<" + bdr.getFullErrorMessage() + ">" + " BLOCK: " + Integer.toString(block.getSequenceNumber()), bdr.getCodeSeverity()),
                            true);
                }

                return new BlockAttemptResult(BlockAttemptResult.SUCCESSOR, bdr, block);
            }

            /*Duplicate ?*/
            if (lastStoredBlock.isEqual(block) && duplicateAccepted == true) {

                /*2nd block should replace first if 2nd is perfect and first not*/
                if (BlockDecodeResult.isCodePerfect(errCode)) {
                    if (!lastStoredBlock.isPerfect()) {
                        replaceLastStoredBlock(block);
                        log.addMessage(
                                new DecoderMessage(MSG_PFX, "RESTORE FROM DUPLICATE: BLOCK:" + Integer.toString(block.getSequenceNumber()), DecoderMessage.SEV_WARNING),
                                true);
                    }
                }

                return new BlockAttemptResult(BlockAttemptResult.DUPLICATE, bdr, block);
            }

            /*Not successor or duplicate or unacceptable block*/
            pulseDecoder.setCurrentSample((int) sequenceMark);    //Revert
            return new BlockAttemptResult(BlockAttemptResult.BADSEQ, bdr, null);

        }

    }

    private void flushFile(String outdir) {

        String fspec;
        boolean perfect = true;
        BufferedOutputStream bos = null;

        /*Get file data as one big int[] array*/
        int[] fileData = getAllValidData();

        try {

            /*Construct file specifier*/
            fspec = constructFilespec(outdir, lastStoredBlock.getName(), fileData, dConfig.genPrependSampleNumber);

            /*Open file for output and write data to it*/
            FileOutputStream fos = new FileOutputStream(fspec);
            bos = new BufferedOutputStream(fos);

            int l = fileData.length;
            for (int i = 0; i < l; i++) {
                bos.write(fileData[i]);
            }

            bos.flush();
            bos.close();

            /*Check whether blocks are perfect*/
            Iterator<TTBlock> it = storedBlocks.iterator();
            while (it.hasNext() == true) {
                if (it.next().isPerfect() == false) {
                    perfect = false;
                    break;
                }
            }

            if (perfect == true) {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "SUCCESS: File decoded OK", DecoderMessage.SEV_INFO),
                        true);
            } else {
                log.addMessage(
                        new DecoderMessage(MSG_PFX, "WARNING: File decoded with problems", DecoderMessage.SEV_WARNING),
                        true);
            }

            log.addMessage(
                    new DecoderMessage(MSG_PFX, "SAVE: " + fspec, DecoderMessage.SEV_SAVE),
                    true);

        } catch (Exception e) {
            log.addMessage(
                    new DecoderMessage(MSG_PFX, Utils.getExceptionMessage(e), DecoderMessage.SEV_ERROR),
                    true);
            try {
                bos.close();
            } catch (IOException xe) {
                xe.printStackTrace();
            }
            e.printStackTrace();
        }

    }

    private void storeBlock(TTBlock block) {
        storedBlocks.add(block);
        lastStoredBlock = block;

    }

    private void replaceLastStoredBlock(TTBlock block) {
        storedBlocks.remove(storedBlocks.size() - 1);
        storeBlock(block);
    }

    String constructFilespec(String outdir, char[] name, int[] fileData, boolean prependSample) throws Exception {

        /*First, we construct namebase, removing all
         *dangerous characters*/
        String nameBase = "";
        if (prependSample == true) {
            nameBase += Utils.padZeros(Long.toString(firstFileSample), 10);
            nameBase += '_';
        }
        nameBase += Utils.getPolishedFilespec(name, 0, 12);

        File odf = new File(outdir);
        outdir = odf.getCanonicalPath();

        String retVal = "";

        /*Filespec is constructed*/
        if (outdir.endsWith(TurboDecoder.SP)) {
            retVal = outdir + nameBase;
        } else {
            retVal = outdir + TurboDecoder.SP + nameBase;
        }

        return retVal;
    }

    private int[] getAllValidData() {

        int blockCount = storedBlocks.size();
        int totalBytes = 0;

        for (int i = 0; i < blockCount; i++) {
            totalBytes += storedBlocks.get(i).validBytes;
        }

        int[] fileData = new int[totalBytes];
        int[] vd;
        int k = 0;

        for (int i = 0; i < blockCount; i++) {
            vd = storedBlocks.get(i).getValidData();
            for (int j = 0; j < vd.length; j++) {
                fileData[k] = vd[j];
                k++;
            }
        }

        return fileData;
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

    /**
     * Polished wrapper for Turbo Tape block
     */
    private static class TTBlock {

        public static final int TAPE_MODE_SS = 128;
        public static final int TAPE_MODE_LS = 0;
        public static final int TAPE_MODE_SD = 192;
        public static final int TAPE_MODE_LD = 64;
        /**
         * Sequence number
         */
        private final int sequenceNumber;
        private final boolean perfect;
        /**
         * Tape mode (SS,LS,SD,LD)
         */
        private final int mode;
        /**
         * Turbo tape file name
         */
        private final char[] name;
        /**
         * Number of valid bytes
         */
        private final int validBytes;
        /**
         * End of file tag
         */
        private final boolean eof;
        /**
         * User data
         */
        private final int[] userData;

        /**
         * Create TTBlock using raw decoded data
         */
        TTBlock(int[] rawData) {
            name = new char[12];
            userData = new int[1_008];
            /*Sequence number*/
            sequenceNumber = rawData[0];
            /*Tape mode*/
            mode = rawData[1];
            /*File name*/
            for (int i = 6; i < 14; i++) {
                name[i - 6] = (char) rawData[i];
            }
            name[8] = '.';
            for (int i = 14; i < 17; i++) {
                name[i - 5] = (char) rawData[i];
            }
            /*Valid bytes*/
            validBytes = rawData[2] + ((rawData[3] & 0x000_007F) * 256);
            /*EOF tag*/
            if ((rawData[3] & 128) == 128) {
                eof = true;
            } else {
                eof = false;
            }
            /*User data*/
            for (int i = 17; i < 1_025; i++) {
                userData[i - 17] = rawData[i];
            }
            /*Perfect ?*/
            perfect = Utils.checkSTBlock(rawData);
        }

        public boolean isEof() {
            return eof;
        }

        public int getMode() {
            return mode;
        }

        public int getSequenceNumber() {
            return sequenceNumber;
        }

        /**
         * Test TTBlock for equality
         *
         * @param block TTBlock to be tested
         * @return true when blocks are the same, false otherwise
         */
        boolean isEqual(TTBlock block) {
            
            /*Simple comparations*/
            if (block.sequenceNumber != sequenceNumber) {
                return false;
            }
            if (block.mode != mode) {
                return false;
            }
            if (block.validBytes != validBytes) {
                return false;
            }
            if (block.eof != eof) {
                return false;
            }
            
            /*User data. Length is ALWAYS the same*/
            for (int i = 0; i < userData.length; i++) {
                if (block.userData[i] != userData[i]) {
                    return false;
                }
            }
            /*File name. Length is ALWAYS the same*/
            for (int i = 0; i < name.length; i++) {
                if (block.name[i] != name[i]) {
                    return false;
                }
            }
            
            /*None failed, blocks are the same*/
            return true;
            
        }

        /**
         * Test TTBlock whether it is predecesor of other block
         *
         * @param block Other block
         * @return true when this block is predecesor of other block
         */
        boolean isPredecesorOf(TTBlock block) {
            if (sequenceNumber != block.sequenceNumber - 1) {
                return false;
            }
            if (mode != block.mode) {
                return false;
            }
            for (int i = 0; i < name.length; i++) {
                if (name[i] != block.name[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("TTBLOCK:");
            sb.append("BLOCK:");
            sb.append(sequenceNumber);
            sb.append(" MODE:");
            sb.append(mode);
            sb.append(" NAME: ");
            sb.append(new String(name));
            sb.append(" VBYTES: ");
            sb.append(validBytes);
            sb.append(" EOF:");
            sb.append(eof);
            return sb.toString();
        }

        public String getTitle() {
            StringBuilder sb = new StringBuilder();
            sb.append(new String(name));
            sb.append(" (");
            sb.append(mode);
            sb.append(')');
            return sb.toString();
        }

        public char[] getName() {
            return name;
        }

        public int[] getValidData() {
            int a = validBytes - 16;
            int[] retVal = new int[a];
            System.arraycopy(userData, 0, retVal, 0, a);
            return retVal;
        }

        boolean isPerfect() {
            return perfect;
        }
    }
    /**
     * Crate that holds result of decoding of file
     */
    private static class FileDecodeResult {
        
        int code;
        BlockDecodeResult bdr;
        
        FileDecodeResult(int code, BlockDecodeResult bdr) {
            this.code = code;
            this.bdr = bdr;
        }
    }
    /**
     * Create for result of try to decode block
     */
    private static class BlockAttemptResult {
        
        static final int SUCCESSOR = 0;
        static final int DUPLICATE = 1;
        static final int TERMINATE = 2;
        static final int BADSEQ = 3;
        static final int GARBLED = 4;
        
        BlockDecodeResult bdr;
        TTBlock block;
        int code;
        
        BlockAttemptResult(int code, BlockDecodeResult bdr, TTBlock block) {
            this.code = code;
            this.bdr = bdr;
            this.block = block;
        }
    }

}
