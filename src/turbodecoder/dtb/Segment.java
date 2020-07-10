package turbodecoder.dtb;

/**
 * Segment of a DOS 2 Binary File
 */
public class Segment {

    /**
     * Unknown address
     */
    public static final int UNKNOWN_ADDRESS = -1;

    /**
     * First address
     */
    private final int firstAddress;
    /**
     * Last address
     */
    private final int lastAddress;
    /**
     * Data
     */
    private final int[] data;

    /**
     * Relative byte address of the segment (location in the file)
     */
    private final int startRBA;

    /**
     * Relative byte address of the last byte of the segment (location in the
     * file)
     */
    private final int endRBA;

    /**
     * RUN address
     */
    private final int runAddressLo;
    private final int runAddressHi;
    private final int runAddress;

    /**
     * INIT address
     */
    private final int initAddressLo;
    private final int initAddressHi;
    private final int initAddress;

    private final boolean hasFullRunVector;
    private final boolean hasFullInitVector;
    private final boolean hasPartialRunVector;
    private final boolean hasPartialInitVector;
    private final boolean hasRunVector;
    private final boolean hasInitVector;

    /**
     *
     * @param start
     * @param data
     * @param rba
     */
    public Segment(int start, int[] data, int rba) {

        this.data = new int[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
        this.firstAddress = start;
        this.lastAddress = start + data.length - 1;
        this.startRBA = rba;
        this.endRBA = rba + 4 + data.length - 1;  //rba+header+data

        int addrLo = UNKNOWN_ADDRESS;
        int addrHi = UNKNOWN_ADDRESS;

        /*Run address Low byte*/
        if (firstAddress <= 736 && lastAddress >= 736) {
            addrLo = data[736 - firstAddress];
        }
        /*Run address High byte*/
        if (firstAddress <= 737 && lastAddress >= 737) {
            addrHi = data[737 - firstAddress];
        }

        /*Check for FULL address*/
        if (addrLo != -1 && addrHi != -1) {
            hasFullRunVector = true;
            hasPartialRunVector = false;
            hasRunVector = true;
            runAddressLo = addrLo;
            runAddressHi = addrHi;
            runAddress = runAddressLo + 256 * runAddressHi;

        } else if (addrLo == -1 && addrHi == -1) {
            hasFullRunVector = false;
            hasPartialRunVector = false;
            hasRunVector = false;
            runAddressLo = addrLo;
            runAddressHi = addrHi;
            runAddress = UNKNOWN_ADDRESS;
        } else {
            hasFullRunVector = false;
            hasPartialRunVector = true;
            hasRunVector = true;
            runAddressLo = addrLo;
            runAddressHi = addrHi;
            runAddress = UNKNOWN_ADDRESS;
        }

        addrLo = UNKNOWN_ADDRESS;
        addrHi = UNKNOWN_ADDRESS;

        /*Init address Low byte*/
        if (firstAddress <= 738 && lastAddress >= 738) {
            addrLo = data[738 - firstAddress];
        }
        /*Init address High byte*/
        if (firstAddress <= 739 && lastAddress >= 739) {
            addrHi = data[739 - firstAddress];
        }

        /*Check for FULL address*/
        if (addrLo != -1 && addrHi != -1) {
            hasFullInitVector = true;
            hasPartialInitVector = false;
            hasInitVector = true;
            initAddressLo = addrLo;
            initAddressHi = addrHi;
            initAddress = initAddressLo + 256 * initAddressHi;

        } else if (addrLo == -1 && addrHi == -1) {
            hasFullInitVector = false;
            hasPartialInitVector = false;
            hasInitVector = false;
            initAddressLo = addrLo;
            initAddressHi = addrHi;
            initAddress = UNKNOWN_ADDRESS;
        } else {
            hasFullInitVector = false;
            hasPartialInitVector = true;
            hasInitVector = true;
            initAddressLo = addrLo;
            initAddressHi = addrHi;
            initAddress = UNKNOWN_ADDRESS;
        }

    }

    /**
     * String representation of a segment
     *
     * @return String representation of a segment
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();

        /*Has data portion ?*/
        if (hasNonVectorData()) {
            sb.append("DATA+");
        }

        /*Has RUN vector*/
        if (hasFullRunVector) {
            sb.append("RUN+");
        } else if (hasPartialRunVector) {
            sb.append("RUN(P)+");
        }

        /*Has INIT vector*/
        if (hasFullInitVector) {
            sb.append("INIT+");
        } else if (hasPartialInitVector) {
            sb.append("INIT(P)+");
        }

        int lastIndex = sb.length() - 1;

        if (sb.lastIndexOf("+") == lastIndex) {
            sb.deleteCharAt(lastIndex);
        }
        sb.append(' ');

        /*Address range*/
        sb.append(String.format("%05d-%05d [%04X-%04X] ", firstAddress, lastAddress, firstAddress, lastAddress));

        if (hasFullRunVector) {
            sb.append(String.format("R:%05d [%04X] ", runAddress, runAddress));
        } else if (hasPartialRunVector) {
            String p1 = (runAddressLo == UNKNOWN_ADDRESS) ? "?" : String.format("%03d [%02X]", runAddressLo, runAddressLo);
            String p2 = (runAddressHi == UNKNOWN_ADDRESS) ? "?" : String.format("%03d [%02X]", runAddressHi, runAddressHi);
            sb.append("R(P): (");
            sb.append(p1);
            sb.append(',');
            sb.append(p2);
            sb.append(')');
        }
        if (hasFullInitVector) {
            sb.append(String.format("I:%05d [%04X] ", initAddress, initAddress));
        } else if (hasPartialInitVector) {
            String p1 = (initAddressLo == UNKNOWN_ADDRESS) ? "?" : String.format("%03d [%02X]", initAddressLo, initAddressLo);
            String p2 = (initAddressHi == UNKNOWN_ADDRESS) ? "?" : String.format("%03d [%02X]", initAddressHi, initAddressHi);
            sb.append("I(P): (");
            sb.append(p1);
            sb.append(',');
            sb.append(p2);
            sb.append(')');
        }

        return sb.toString();
    }

    /**
     *
     * @return
     */
    public int[] getFullData() {

        int[] retArray = new int[data.length + 4];
        retArray[0] = firstAddress % 256;
        retArray[1] = firstAddress / 256;
        retArray[2] = lastAddress % 256;
        retArray[3] = lastAddress / 256;
        System.arraycopy(data, 0, retArray, 4, data.length);

        return retArray;
    }

    /**
     *
     * @return
     */
    public int[] getData() {
        return data;
    }

    /**
     *
     * @return
     */
    public boolean hasFullRunVector() {
        return hasFullRunVector;
    }

    /**
     *
     * @return
     */
    public boolean hasFullInitVector() {
        return hasFullInitVector;
    }

    /**
     *
     * @return
     */
    public boolean hasNoVector() {
        return (hasRunVector == false && hasInitVector == false);
    }

    /**
     *
     * @return
     */
    public boolean hasPartialRunVector() {
        return hasPartialRunVector;
    }

    /**
     *
     * @return
     */
    public boolean hasPartialInitVector() {
        return hasPartialInitVector;
    }

    /**
     *
     * @return
     */
    public boolean hasNonVectorData() {
        if (isPureRunSegment() || isPureInitSegment() || isPureRunInitSegment()) {
            return false;
        }
        return true;
    }

    /**
     *
     * @return
     */
    public int getFirstAddress() {
        return firstAddress;
    }

    /**
     *
     * @return
     */
    public int getLastAddress() {
        return lastAddress;
    }

    /**
     *
     * @return
     * @throws DOS2BinaryProcessingException
     */
    public int getRunVector() throws DOS2BinaryProcessingException {
        if (runAddress == UNKNOWN_ADDRESS) {
            throw new DOS2BinaryProcessingException(("Internal Error: getRunVector() call on Segment that has no RUN vector"));
        }
        return runAddress;
    }

    /**
     *
     * @return
     * @throws DOS2BinaryProcessingException
     */
    public int getInitVector() throws DOS2BinaryProcessingException {
        if (initAddress == UNKNOWN_ADDRESS) {
            throw new DOS2BinaryProcessingException(("Internal Error: getInitVector() call on Segment that has no INIT vector"));
        }
        return initAddress;
    }

    /**
     *
     * @return
     */
    public int getRba() {
        return startRBA;
    }

    /**
     *
     * @return
     */
    public int getEndRba() {
        return endRBA;
    }

    /**
     *
     * @return
     */
    public boolean isPureRunSegment() {
        if (firstAddress == 736 && lastAddress == 736) {
            return true;
        }
        if (firstAddress == 736 && lastAddress == 737) {
            return true;
        }
        if (firstAddress == 737 && lastAddress == 737) {
            return true;
        }
        return false;
    }

    /**
     *
     * @return
     */
    public boolean isPureInitSegment() {
        if (firstAddress == 738 && lastAddress == 738) {
            return true;
        }
        if (firstAddress == 738 && lastAddress == 739) {
            return true;
        }
        if (firstAddress == 739 && lastAddress == 739) {
            return true;
        }
        return false;
    }

    /**
     *
     * @return
     */
    public boolean isPureRunInitSegment() {
        return ((firstAddress == 736 && lastAddress == 739) || (firstAddress == 737 && lastAddress == 738));
    }

    SegmentPortionCrate[] getNonVectorPortions() throws DOS2BinaryProcessingException {

        int fa = -1;
        int la = -1;

        if (hasInitVector && hasRunVector) {
            fa = 736;
            la = 739;
        } else if (hasInitVector) {
            fa = 738;
            la = 739;
        } else if (hasRunVector) {
            fa = 736;
            la = 737;

        }

        if (fa == -1 || la == -1) {
            throw new DOS2BinaryProcessingException("Internal error: getNonJumpVectorPortions() call on pure DATA segment");
        }

        int befLength = fa - getFirstAddress();
        int aftLength = getLastAddress() - la;

        SegmentPortionCrate[] portions = new SegmentPortionCrate[2];

        if (befLength > 0) {
            portions[0] = new SegmentPortionCrate(getFirstAddress(), befLength);
            System.arraycopy(data, 0, portions[0].portionData, 0, befLength);
        } else {
            portions[0] = null;
        }

        if (aftLength > 0) {
            portions[0] = new SegmentPortionCrate(la + 1, aftLength);
            System.arraycopy(data, la - getFirstAddress() + 1, portions[1].portionData, 0, aftLength);
        } else {
            portions[1] = null;
        }

        return portions;

    }

    /**
     *
     * @return
     */
    public boolean hasInitVector() {
        return hasInitVector;
    }

    /**
     *
     * @return
     */
    public boolean hasRunVector() {
        return hasRunVector;
    }

    static class SegmentPortionCrate {

        int address;
        int[] portionData;

        SegmentPortionCrate(int address, int length) {
            this.address = address;
            this.portionData = new int[length];
        }
    }

}
