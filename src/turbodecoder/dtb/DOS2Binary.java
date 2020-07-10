package turbodecoder.dtb;

import java.io.*;
import java.util.*;
import turbodecoder.Utils;

/**
 * DOS 2 Binary file.
 */
public class DOS2Binary {

    /**
     * File name
     */
    private final String filename;

    /**
     * All segments
     */
    private final ArrayList<Segment> segmentList;

    /**
     * Indicates that file has been analyzed
     */
    private boolean isAnalyzed;

    /**
     * Total length of the binary file
     */
    private int fileLength;

    /**
     * Create new instance
     *
     * @param _filename File name
     */
    public DOS2Binary(String _filename) {
        filename = _filename;
        segmentList = new ArrayList<>();
        isAnalyzed = false;
    }

    /**
     * Listing of segments
     *
     * @return Array of segment string representations
     */
    public String[] getListing() {

        /*Array of the strings*/
        String[] s = new String[segmentList.size()];

        for (int i = 0; i < segmentList.size(); i++) {
            s[i] = segmentList.get(i).toString();
        }

        return s;
    }

    /**
     *
     * @throws IOException
     * @throws DOS2BinaryException
     */
    public void analyzeFromFile() throws IOException, DOS2BinaryException {

        /*Check size. Maximum size is up to 16 MB*/
        File f = new File(filename);
        if (f.exists() && f.isFile()) {
            long l = f.length();
            if (l > 16 * 1_024 * 1_024) {
                throw new DOS2BinaryException(filename, "Binary file is too long. File size exceeds 16 MB.", 0);
            }
        }

        RandomAccessFile raf;
        raf = new RandomAccessFile(filename, "r");

        /*Nacteni vsech dat ze souboru*/
        byte[] filebData = new byte[(int) raf.length()];
        raf.readFully(filebData);
        raf.close();

        /*Konverze z pole bajtu na pole intu. Skoda ze Java nema neznamenkove
         *typy,ale neda se nic delat*/
        int[] fileData = Utils.getAsIntArray(filebData);

        analyze(fileData, true);

    }

    /**
     *
     * @param fileData
     * @throws IOException
     * @throws DOS2BinaryException
     */
    public void analyzeFromData(int[] fileData) throws IOException, DOS2BinaryException {
        analyzeFromData(fileData, true);
    }

    /**
     *
     * @param fileData
     * @param headerRequired
     * @throws IOException
     * @throws DOS2BinaryException
     */
    public void analyzeFromData(int[] fileData, boolean headerRequired) throws IOException, DOS2BinaryException {
        analyze(fileData, headerRequired);
    }

    /**
     * Analyze binary file. Populate list of segments
     *
     * @throws IOException,DOS2BinaryException
     */
    private void analyze(int[] fileData, boolean headerRequired) throws IOException, DOS2BinaryException {

        int pos = 0;
        fileLength = fileData.length;
        int b1, b2;
        int w1, w2;

        /*Begin analysis*/
 /*If a header is required to be present, check for header (255 255)*/
        if (headerRequired == true) {
            if (fileData[0] != 255 || fileData[1] != 255) {
                throw new DOS2BinaryException(filename, "Binary file header not found. First two bytes do not have values of 255 [0xFF]", 0);
            }
            pos = 2;
        } else if (fileData[0] == 255 && fileData[1] == 255) {
            pos = 2;
        } else {
            pos = 0;
        }

        /*Segment header must have at least 2 bytes*/
        while (pos < fileLength) {

            int lastSegPos = pos;

            try {

                /*Is there another 255 255*/
                b1 = fileData[pos];
                b2 = fileData[pos + 1];

                /*If so, update position*/
                if (b1 == 255 && b2 == 255) {
                    pos += 2;
                }

                /*Get first address and last address*/
                w1 = fileData[pos] + 256 * fileData[pos + 1];
                pos += 2;

                w2 = fileData[pos] + 256 * fileData[pos + 1];
                pos += 2;

                /*Check for negative segment size*/
                if (w2 < w1) {
                    throw new DOS2BinaryException(filename, getNegativeSegmentSizeMessage(w1, w2), lastSegPos);
                }

                /*Create new segment*/
                int[] newSegmentData = new int[w2 - w1 + 1];
                System.arraycopy(fileData, pos, newSegmentData, 0, newSegmentData.length);
                Segment s = new Segment(w1, newSegmentData, lastSegPos);

                /*Add segment to the list*/
                this.segmentList.add(s);

                /*Advance*/
                pos += newSegmentData.length;

            } catch (ArrayIndexOutOfBoundsException ae) {
                throw new DOS2BinaryException(filename, "Segment or segment header continues beyond end of binary file", pos);
            }

        }/*End of main loop*/

        isAnalyzed = true;

    }

    /**
     * List segments to standard output
     */
    void listToConsole() {
        for (int i = 0; i < segmentList.size(); i++) {
            System.out.println(segmentList.get(i).toString());
        }
    }

    /**
     * Create monolithic binary file)
     *
     * @param outname Output file
     * @param extraAdress Address of the jump segment emulation code
     * @param extra Generate jump segment emulation code
     * @throws turbodecoder.dtb.DOS2BinaryProcessingException
     * @throws IOException,NumberFormatException
     */
    public void createMonolithicBinary(String outname, String extraAdress, boolean extra) throws DOS2BinaryProcessingException, IOException, NumberFormatException {

        /*Check if there is at least one segment*/
        if (segmentList.size() < 1) {
            throw new DOS2BinaryProcessingException("Unable to create monolithic binary file. The input binary file has no segments.");
        }
        
        /*Validate the extra code address*/
        int extraCodeAddress = -1;
        
        if (extra == true) {
            
            /*Try to get decimal number*/
            try {
                extraCodeAddress = Integer.parseInt(extraAdress);
            } catch (NumberFormatException e) {
                extraCodeAddress = -1;
            }
            /*Check range*/
            if (extraCodeAddress < 0 || extraCodeAddress > 65_535) {
                throw new DOS2BinaryProcessingException(("Unable to create monolithic binary file. Adddress of the code that replaces INIT segments is not valid (0-65535)"));
            }
            /*Check if the extra code fits*/
            if (extraCodeAddress>65_536-getExtraCodeForMergeLength()) {
                throw new DOS2BinaryProcessingException("Unable to create monolithic binary file. Code that replaces INIT segments would span beyond address of 65535");
            }

        }

        Segment[] segments = new Segment[segmentList.size()];
        for (int i = 0; i < segments.length; i++) {
            segments[i] = segmentList.get(i);
        }

        /*Main storage array. Unused bytes are marked with -1.
        This is used to detect overlapping segments. List of intervals would
        be nice, but this implementation is sufficient*/
        int[] fileData = new int[65_536];

        /*Reset main storage array*/
        for (int i = 0; i < 65_536; i++) {
            fileData[i] = -1;
        }

        /*Copy segments to the main storage array*/
        for (Segment seg : segments) {
            int firstAddress = seg.getFirstAddress();
            int[] segData = seg.getData();
            int length = segData.length;

            /*For pure data segments, it is simple, we copy the data and
            check for overlaps. This is why we do this byte by byte*/
            if (seg.hasNoVector()) {
                for (int k = 0; k < length; k++) {
                    /*Check for overlap*/
                    if (fileData[firstAddress + k] != -1) {
                        throw new DOS2BinaryProcessingException("Unable to create monolithic binary file. Segments of the binary file overlap. Segment: " + seg.toString());
                    }
                    /*Copy data*/
                    fileData[firstAddress + k] = segData[k];
                }
                continue;
            }

            /*Segment with jump vectors*/
 /*Get portions that are not jump vectors*/
            Segment.SegmentPortionCrate[] portions = seg.getNonVectorPortions();

            for (Segment.SegmentPortionCrate portion : portions) {
                if (portion != null) {
                    for (int j = 0; j < portion.portionData.length; j++) {
                        if (fileData[portion.address + j] != -1) {
                            throw new DOS2BinaryProcessingException("Unable to create monolithic binary file. Segments of the binary file overlap. Segment: " + seg.toString());
                        }
                        fileData[portion.address + j] = portion.portionData[j];
                    }
                }
            }
        }

        /*Determine run address*/
        int runVector = -1;

        /*Create JSR jumps*/
        if (extra == true) {

            int pos = extraCodeAddress;

            Iterator<Segment> it = segmentList.iterator();

            while (it.hasNext()) {
                Segment seg = it.next();
                if (seg.hasNoVector() == true) {
                    continue;
                }

                if (seg.hasFullInitVector() == true) {
                    int vect = seg.getInitVector();
                    fileData[pos] = 32;
                    pos++;
                    fileData[pos] = vect % 256;
                    pos++;
                    fileData[pos] = vect / 256;
                    pos++;
                }

                if (seg.hasFullRunVector() == true) {
                    runVector = seg.getRunVector();
                }
            }

            /*Run - generate JMP*/
            if (runVector > 0) {
                fileData[pos] = 76;
                pos++;
                fileData[pos] = (runVector % 256);
                pos++;
                fileData[pos] = (runVector / 256);
                pos++;
            }

        } /*Only browse for possible run segments*/ else {
            Iterator<Segment> it = segmentList.iterator();
            while (it.hasNext()) {
                Segment seg = it.next();
                if (seg.hasFullRunVector() == true) {
                    runVector = seg.getRunVector();
                }
            }
        }

        /*Determine first and last address of the only DATA segment*/
        int fa = -1;
        int la = -1;

        for (int i = 0; i < 65_536; i++) {
            if (fileData[i] != -1) {
                fa = i;
                break;
            }
        }

        for (int i = 65_535; i > -1; i--) {
            if (fileData[i] != -1) {
                la = i;
                break;
            }
        }

        if (fa == -1 || la == -1) {
            throw new DOS2BinaryProcessingException("Unable to create monolithic binary file. Internal error when determining lowest and highest address.");
        }

        /*Untouched addresses must be zeroed*/
        for (int i = fa; i < la; i++) {
            if (fileData[i] == -1) {
                fileData[i] = 0;
            }
        }

        /*Writing monolithic binary file*/
        RandomAccessFile raf;

        raf = new RandomAccessFile(outname, "rw");
        raf.setLength(0);

        /*Header*/
        raf.writeByte(255);
        raf.writeByte(255);
        raf.writeByte(fa % 256);
        raf.writeByte(fa / 256);
        raf.writeByte(la % 256);
        raf.writeByte(la / 256);

        /*Merged data*/
        for (int i = fa; i <= la; i++) {
            raf.writeByte(fileData[i]);
        }

        /*RUN segment*/
        raf.writeByte(736 % 256);
        raf.writeByte(736 / 256);
        raf.writeByte(737 % 256);
        raf.writeByte(737 / 256);

        int finalRunVector = 0;
        if (extra == true) {
            finalRunVector = extraCodeAddress;
        }
        if (extra == false && runVector > 0) {
            finalRunVector = runVector;
        }
        if (extra == false && runVector < 0) {
            finalRunVector = fa;
        }

        raf.writeByte(finalRunVector % 256);
        raf.writeByte(finalRunVector / 256);

        raf.close();

    }

    /**
     * Get number of data segments
     *
     * @return Number of data segments
     */
    public int getSegmentWithoutVectorCount() {
        Iterator<Segment> it = segmentList.iterator();
        int count = 0;
        while (it.hasNext()) {
            Segment seg = it.next();
            if (seg.hasNoVector() == true) {
                count++;
            }
        }
        return count;
    }

    /**
     * Number of segments with RUN or INIT Vector
     *
     * @return Number of segments
     */
    public int getSegmentWithVectorCount() {
        Iterator<Segment> it = segmentList.iterator();
        int count = 0;
        while (it.hasNext()) {
            Segment seg = it.next();
            if (!seg.hasNoVector() == true) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get offsets of segments that have INIT vectors
     *
     * @return Array of offsets
     */
    public int[] getInitLocations() {

        Iterator<Segment> it = segmentList.iterator();
        int count = 0;
        while (it.hasNext()) {
            Segment seg = it.next();
            if (seg.hasFullInitVector()) {
                count++;
            }
        }

        int[] initLocations = new int[count];

        it = segmentList.iterator();
        int index = 0;
        while (it.hasNext()) {
            Segment seg = it.next();
            if (seg.hasFullInitVector()) {
                initLocations[index] = seg.getEndRba();
                index++;
            }
        }

        return initLocations;
    }

    /**
     * Get file statistics
     *
     * @return Array with file statistics
     */
    public int[] getFileStatistics() {
        int[] retVal = new int[7];

        retVal[0] = fileLength;
        /*File size*/
        retVal[1] = segmentList.size();
        /*Total numbe of segments*/
        retVal[2] = getSegmentWithoutVectorCount();
        /*Segments without vector*/
        retVal[3] = getSegmentWithVectorCount();
        /*Segments with vector*/
        retVal[4] = 0;
        /*Segments <1KB*/
        retVal[5] = 0;
        /*Segments 1-3 KB*/
        retVal[6] = 0;
        /*Bigger segments*/
        int l = this.segmentList.size();
        int b;
        Segment s;

        Iterator<Segment> segIter = segmentList.iterator();

        while (segIter.hasNext()) {
            s = segIter.next();
            b = s.getData().length;
            if (b <= 1_024) {
                retVal[4] += b;
            }
            if (b > 1_024 && b <= 3_072) {
                retVal[5] += b;
            }
            if (b > 3_072) {
                retVal[6] += b;
            }
        }
        return retVal;
    }

    /**
     * Test whether some data segment covers defined memory area. Useful to test
     * compatibility with some binary loaders
     *
     * @param firstAdr First address of tested memory area
     * @param lastAdr Last address of tested memory area
     * @return true if some segment covers defined memory area
     */
    public boolean coversMemory(int firstAdr, int lastAdr) {

        int l = segmentList.size();
        Segment s;
        boolean b;
        for (int i = 0; i < l; i++) {
            b = true;
            s = segmentList.get(i);
            if ((s.getFirstAddress() < firstAdr) && (s.getLastAddress() < firstAdr)) {
                b = false;
            }
            if ((s.getFirstAddress() > lastAdr) && (s.getLastAddress() > lastAdr)) {
                b = false;
            }
            if (b == true) {
                return b;
            }
        }

        return false;
    }

    /**
     * Test whether file contain at least one INIT vector
     *
     * @return true When file contains at least one INIT vector
     */
    public boolean hasInitVector() {
        Iterator<Segment> it = segmentList.iterator();
        while (it.hasNext()) {
            Segment seg = it.next();
            if (seg.hasInitVector() == true) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @return
     */
    public boolean hasRunVector() {
        Iterator<Segment> it = segmentList.iterator();
        while (it.hasNext()) {
            Segment seg = it.next();
            if (seg.hasRunVector() == true) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @return
     */
    public String getFileName() {
        return filename;
    }

    /**
     * Determine whether the binary file is monolithic
     *
     * @return True when the binary file is monolithic
     */
    public boolean isMonolithic() {

        int segmentCount = getTotalSegmentCount();

        /*More than two segments or INIT vector are show-stoppers*/
        if (segmentCount > 2 || hasInitVector()) {
            return false;
        }

        /*One segment is always OK, unless there is a partial RUN vector*/
        Iterator<Segment> segIter = segmentList.iterator();
        Segment s1 = segIter.next();
        if (segmentCount == 1) {
            if (!s1.hasPartialRunVector()) {
                return true;
            }
            return false;
        }

        /*If there are two segments, we must ensure that:
          1. There is at least one byte of non-vector data
          2. Only one of the segments has non-vector data
          3. There is exactly one full specified RUN vector
         */
        Segment s2 = segIter.next();

        /* Both have non-vector data - not monolithic*/
        if (s1.hasNonVectorData() == true && s2.hasNonVectorData() == true) {
            return false;
        }
        /* None has non-vector data - not monolithic*/
        if (!s1.hasNonVectorData() && !s2.hasNonVectorData()) {
            return false;
        }
        /* Both have RUN vector - not monolithic*/
        if (s1.hasRunVector() && s2.hasRunVector()) {
            return false;
        }
        /* None have full RUN vector - not monolithic*/
        if (!s1.hasFullRunVector() && !s2.hasFullRunVector()) {
            return false;
        }

        /*All conditions met*/
        return true;

    }

    /**
     * Determine whether the binary file is compatible. The compatibility check
     * is performed by a visitor class
     *
     * @param ccVisitor Visitor class
     * @return True if the binary file is compatible. False otherwise.
     */
    public boolean isCompatible(CompatibilityCheckVisitor ccVisitor) {
        return ccVisitor.isCompatible(this);
    }

    /**
     *
     * @return
     */
    public int getTotalSegmentCount() {
        return segmentList.size();
    }

    /**
     * Get number of bytes required to replace effects of INIT segments and RUN
     * segment
     *
     * @return
     */
    public int getExtraCodeForMergeLength() {
        int initCounter = 0;
        int hasRun = 0;

        Iterator<Segment> it = segmentList.iterator();

        while (it.hasNext()) {
            Segment seg = it.next();
            if (seg.hasFullInitVector()) {
                initCounter++;
            }
            if (seg.hasFullRunVector()) {
                hasRun = 1;
            }
        }

        return (initCounter + hasRun) * 3;

    }

    /**
     *
     * @return
     */
    public boolean isIsAnalyzed() {
        return isAnalyzed;
    }

    /**
     *
     * @return
     */
    public Iterator<Segment> getSegmentListIterator() {
        return segmentList.iterator();
    }

    private String getNegativeSegmentSizeMessage(int w1, int w2) {

        StringBuilder sb = new StringBuilder();
        sb.append("Segment with negative size found ");
        sb.append('(');
        sb.append(Utils.padZeros(Integer.toString(w1), 5));
        sb.append('-');
        sb.append(Utils.padZeros(Integer.toString(w2), 5));
        sb.append(" [");
        sb.append(Utils.padZeros(Integer.toHexString(w1), 4).toUpperCase());
        sb.append('-');
        sb.append(Utils.padZeros(Integer.toHexString(w2), 4).toUpperCase());
        sb.append("])");

        return sb.toString();

    }

    /**
     *
     * @return
     */
    public int getFileLength() {
        return fileLength;
    }

    /**
     *
     */
    public void createArtificialRunVector() {
        Iterator<Segment> it = segmentList.iterator();

        Segment seg = null;

        /*Get first segment*/
        while (it.hasNext()) {
            seg = it.next();
            break;
        }
        
        if (seg==null) return;

        int[] segData = new int[2];
        segData[0] = seg.getFirstAddress() % 256;
        segData[1] = seg.getFirstAddress() / 256;

        Segment runVectorSegment = new Segment(736, segData, 0);
        this.segmentList.add(runVectorSegment);

    }


    /**
     * Get information required to perform conversion of monolithic binary file
     *
     * @return MonolithicConversionInfoCrate with information
     * @throws DOS2BinaryProcessingException
     */
    public MonolithicConversionInfoCrate getMonolithicBinaryFileConversionInfo() throws DOS2BinaryProcessingException {

        /* Check if the file is monolithic*/
        if (!isMonolithic()) {
            throw new DOS2BinaryProcessingException(("Internal error: getMonolithichBinaryFileConversionInfo() called on non-monolithic binary file"));
        }

        /*Create crate*/
        MonolithicConversionInfoCrate crate = new MonolithicConversionInfoCrate();

        /*Populate the crate*/
        Iterator<Segment> segIter = segmentList.iterator();
        int segmentCount = getTotalSegmentCount();

        Segment s1 = segIter.next();

        /*With one segment*/
        if (segmentCount == 1) {
            crate.data = s1.getData();
            if (s1.hasRunVector() == true) {
                crate.runAddress = s1.getRunVector();
            } else {
                crate.runAddress = s1.getFirstAddress();
            }
            crate.loadAddress = s1.getFirstAddress();
        } /*With two segments*/ else {
            Segment s2 = segIter.next();

            if (s1.hasNonVectorData() == true) {
                crate.data = s1.getData();
                crate.runAddress = s2.getRunVector();
                crate.loadAddress = s1.getFirstAddress();
            } else {
                crate.data = s2.getData();
                crate.runAddress = s1.getRunVector();
                crate.loadAddress = s2.getFirstAddress();
            }

        }

        return crate;

    }

    /**
     *
     */
    public static class MonolithicConversionInfoCrate {

        /**
         * Data
         */
        public int[] data;
        /**
         * Run address
         */
        public int runAddress;
        /**
         * Load address
         */
        public int loadAddress;
    }

}
