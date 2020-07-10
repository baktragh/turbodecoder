package turbodecoder;

import java.io.*;
import java.util.*;
import turbodecoder.dtb.DOS2Binary;
import turbodecoder.dtb.DOS2BinaryException;


public class Utils {

    /**
     * Return string representation of T2K-KB header block
     * @return 
     */
    public static String kblockHeaderToString(int[] header) {
        byte[] nm = new byte[16];
        for (int i = 0; i < 16; i++) {
            nm[i] = (byte) header[2 + i];
        }
        return new String(nm);
    }

    /**
     * Create header for Czechoslovak Turbo 2000 or Super Turbo
     *
     * @param isSt Indicates Super Turbo
     * @param name File name
     * @param type File type
     * @param load Load address
     * @param length Length
     * @param run Run address
     * @return Header
     */
    public static int[] createT2KStHeader(boolean isSt, String name, int type, int load, int length, int run) {

        int totalHeaderLength = isSt ? 29 : 19;
        int nameLength = isSt ? 20 : 10;

        int[] retVal = new int[totalHeaderLength];

        retVal[0] = isSt ? 183 : 0;
        retVal[1] = type;

        int[] finalNameBytes;

        /*Try from hexdump*/
        int[] hexDumpNameBytes = Utils.getNameFromHexDump(name, nameLength, 0x20);

        /*If not from hexdump, then normal name*/
        if (hexDumpNameBytes == null) {
            char[] nameChars = name.toCharArray();
            int[] nameBytes = new int[nameLength];
            for (int i = 0; i < nameBytes.length; i++) {
                nameBytes[i] = 0x20;
            }
            for (int i = 0; i < nameLength && i < name.length(); i++) {
                nameBytes[i] = nameChars[i];
            }
            finalNameBytes = nameBytes;
        } /*Otherwise we use the hexdump*/ else {
            finalNameBytes = hexDumpNameBytes;
        }

        System.arraycopy(finalNameBytes, 0, retVal, 2, nameLength);

        retVal[2 + nameLength + 0] = load % 256;
        retVal[2 + nameLength + 1] = load / 256;
        retVal[2 + nameLength + 2] = length % 256;
        retVal[2 + nameLength + 3] = length / 256;
        retVal[2 + nameLength + 4] = run % 256;
        retVal[2 + nameLength + 5] = run / 256;

        int chsum = retVal[0];
        for (int i = 1; i < (totalHeaderLength - 1); i++) {
            chsum ^= retVal[i];
        }
        retVal[totalHeaderLength - 1] = chsum;

        return retVal;
    }

    /**
     * Verify checksum of Super turbo block
     * @return 
     */
    public static boolean checkSTBlock(int[] dta) {

        int ln = dta.length;
        if (ln < 2) {
            return false;
        }

        int sum = dta[0];
        for (int i = 1; i < ln - 1; i++) {
            sum ^= dta[i];
        }

        if (sum != dta[ln - 1]) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Verify checksum of T2K block
     * @return 
     */
    public static boolean checkT2KBlock(int[] dta) {

        int ln = dta.length;
        if (ln < 2) {
            return false;
        }

        int sum = dta[0];
        for (int i = 1; i < ln - 1; i++) {
            sum ^= dta[i];
        }

        if (sum != dta[ln - 1]) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Return string representation of ST header
     * @return 
     */
    public static String stHeaderToString(int[] dta) {
        StringBuilder sb = new StringBuilder();

        /*Name*/
        byte[] nm = new byte[20];
        for (int i = 2; i < 22; i++) {
            nm[i - 2] = (byte) dta[i];
        }
        String s = new String(nm);
        sb.append(s);
        /*Addr*/
        sb.append(", LO:");
        sb.append(dta[22] + 256 * dta[23]);
        sb.append(", LN:");
        sb.append(dta[24] + 256 * dta[25]);
        sb.append(", RU:");
        sb.append(dta[26] + 256 * dta[27]);
        sb.append(", TP:");
        sb.append(dta[1]);

        return sb.toString();
    }

    /**
     * Return string representation of T2K header
     * @return 
     */
    public static String t2kHeaderToString(int[] dta) {
        StringBuilder sb = new StringBuilder();

        byte[] nm = new byte[10];
        for (int i = 2; i < 12; i++) {
            nm[i - 2] = (byte) dta[i];
        }
        String s = new String(nm);
        sb.append(s);
        sb.append(", LO:");
        sb.append(dta[12] + 256 * dta[13]);
        sb.append(", LN:");
        sb.append(dta[14] + 256 * dta[15]);
        sb.append(", RU:");
        sb.append(dta[16] + 256 * dta[17]);
        sb.append(", TP:");
        sb.append(dta[1]);

        return sb.toString();
    }

    /**
     *
     * @param dta
     * @return
     */
    public static String stHeaderToHeaderFile(int[] dta) {
        StringBuilder sb = new StringBuilder();

        /*Eyecatcher*/
        sb.append("cs.superturbo.header,");

        /*Name*/
        byte[] nm = new byte[20];
        for (int i = 2; i < 22; i++) {
            nm[i - 2] = (byte) dta[i];
        }
        String s = new String(nm);
        sb.append(s);
        sb.append(',');

        sb.append(dta[1]);
        sb.append(',');
        sb.append(dta[22] + 256 * dta[23]);
        sb.append(',');
        sb.append(dta[24] + 256 * dta[25]);
        sb.append(',');
        sb.append(dta[26] + 256 * dta[27]);

        /*File name in hexadecimals*/
        sb.append(',');
        for (int i = 0; i < 20; i++) {
            sb.append(String.format("%02X ", dta[2 + i]));
        }

        return sb.toString();
    }

    /**
     *
     * @param dta
     * @return
     */
    public static String t2kHeaderToHeaderFile(int[] dta) {
        StringBuilder sb = new StringBuilder();

        /*Eyecatcher*/
        sb.append("cs.turbo2000.header,");

        /*Name*/
        byte[] nm = new byte[10];
        for (int i = 2; i < 12; i++) {
            nm[i - 2] = (byte) dta[i];
        }
        String s = new String(nm);
        sb.append(s);
        sb.append(',');

        /*Type*/
        sb.append(dta[1]);
        sb.append(',');
        /*Load*/
        sb.append(dta[12] + 256 * dta[13]);
        sb.append(',');
        /*Run*/
        sb.append(dta[14] + 256 * dta[15]);
        /*Length*/
        sb.append(',');
        sb.append(dta[16] + 256 * dta[17]);
        /*File name in hexadecimals*/
        sb.append(',');
        for (int i = 0; i < 10; i++) {
            sb.append(String.format("%02X ", dta[2 + i]));
        }

        return sb.toString();
    }

    /**
     * Get a message string for some Exception
     * @return 
     */
    public static String getExceptionMessage(Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName());
        String m = ex.getMessage();
        if (m != null) {
            sb.append(':');
            sb.append(' ');
            sb.append(m);
        }

        return sb.toString();

    }

    /**
     * Get HTML formatted message for some exception with title specified
     * @return 
     */
    public static String getTitledExceptionMessage(String title, Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("<HTML><B>");
        sb.append(title);
        sb.append("</B><BR>");
        sb.append(Utils.getExceptionMessage(ex));
        sb.append("</HTML>");
        return sb.toString();

    }

    /**
     *
     * @param title
     * @param ex
     * @return
     */
    public static String getTitledExceptionTextMessage(String title, Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(title);
        sb.append(TurboDecoder.LN);
        sb.append(Utils.getExceptionMessage(ex));
        return sb.toString();

    }

    /**
     * Replace some elements in the string by other string
     * @param s Input String
     * @param what String to be replaced
     * @param replacement Replacement String
     * @return String after replacement
     */
    public static String replaceElements(String s, String what, String replacement) {

        int whatLength = what.length();
        int idx;

        StringBuilder sb = new StringBuilder();

        /*If the string that will be replaced has no length, we do nothing*/
        if (what.length() == 0) {
            return s;
        }

        /*1000 iterations is maximum*/
        int iterations = 0;

        while (s.length() > 0 && iterations < 1_000) {

            iterations++;

            /*idx holds index of first letter of what*/
            idx = s.indexOf(what);

            /*If what is not in the input string, we are done*/
            if (idx == -1) {
                sb.append(s);
                break;
            }
            /*We add everything before what to the buffer*/
            sb.append(s.substring(0, idx));
            /*From input string we remove everything to the end of what*/
            s = s.substring(idx + whatLength);
            /*Replaced string to the buffer*/
            sb.append(replacement);

        }

        return sb.toString();
    }

    /**
     * This will convert command line - plain string with tokens separated by
     * space to array of strings."x x" is one token
     * @param cmdLine Command line
     * @return Array of strings
     */
    public static String[] getCommandArrayFromCommandLine(String cmdLine) {

        /*Length of input string*/
        int l = cmdLine.length();
        /*Vector of commands*/
        ArrayList<String> cmdV = new ArrayList<>();
        /*Inside parenthesis ?*/
        int depth = 0;
        /*Current position*/
        int index;

        StringBuilder sb = new StringBuilder();

        char ch;

        for (index = 0; index < l; index++) {
            ch = cmdLine.charAt(index);
            /*SPACE, token separator when depth=0*/
            if (ch == ' ' && depth == 0) {
                cmdV.add(sb.toString());
                sb.setLength(0);
                continue;
            }
            if (ch == '"' && depth == 1) {
                depth = 0;
                continue;
            }
            if (ch == '"' && depth == 0) {
                depth = 1;
                continue;
            }
            sb.append(ch);
        }
        cmdV.add(sb.toString());

        String[] cmdArray = new String[cmdV.size()];
        cmdV.toArray(cmdArray);

        return cmdArray;
    }

    /**
     *
     * @param cmdArray
     */
    public static void printCommandLine(String[] cmdArray) {

        int l = cmdArray.length;
        if (l < 1) {
            System.out.println("<EMPTY COMMAND>");
            return;
        }

        System.out.print("Command: " + cmdArray[0] + "|");
        System.out.print("Parameters: ");
        for (int i = 1; i < l; i++) {
            System.out.print(cmdArray[i]);
            if (i < l - 1) {
                System.out.print(',');
            }
        }
        System.out.println();
    }

    /**
     *
     * @param v
     * @return
     */
    public static String getHtmlFormatedMessageList(ArrayList<String> v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        int l = v.size();
        StringBuilder buf = new StringBuilder();
        buf.append("<HTML>");
        for (String s : v) {
            buf.append(s);
            buf.append("<BR>");
        }
        buf.append("</HTML>");
        return buf.toString();

    }

    /**
     *
     * @param filespec
     * @param ext
     * @return
     */
    public static String changeFileExtension(String filespec, String ext) {

        if (filespec.isEmpty()) {
            return filespec;
        }

        File f = new File(filespec);

        String parent = f.getParent();
        String name = f.getName();

        /*Ext should be at least 2 chars long*/
        if (ext.length() < 2) {
            return filespec + ".badExtension";
        }

        if (name.toLowerCase().endsWith(ext.toLowerCase())) {
            return filespec;
        }

        int l = name.length();
        int i = name.lastIndexOf('.');

        /*No dot in name*/
        if (i < 0) {
            filespec += ext;
            return filespec;
        }
        /*Dot at the end or dot is file name*/
        if (i == l - 1) {
            filespec += ext.substring(1);
            return filespec;
        }
        /*Dot at the beginning but not in the end*/
        if (i == 0) {
            filespec += ext;
            return filespec;
        }
        /*Dot in the middle*/
        name = name.substring(0, i);

        /*Parent exists, does not end with file separator*/
        if (parent != null && !(parent.endsWith(TurboDecoder.SP))) {
            return parent + TurboDecoder.SP + name + ext;
        }
        /*Parent exists, trailing file separator present*/
        if (parent != null && (parent.endsWith(TurboDecoder.SP))) {
            return parent + name + ext;
        }
        /*Paren does not exist*/
        return name + ext;

    }


    /**
     * Verify whether file name is compatible with 8.3 convention
     * @return 
     */
    public static boolean verifyBTapeFileName(String fn) {

        /*Length*/
        if (fn.length() > 12 || fn.length() == 0) {
            return false;
        }

        /*Dots*/
        int dp1 = fn.indexOf('.');
        int dp2 = fn.lastIndexOf('.');

        /*No dot */
        if (dp1 == -1) {
            if (fn.length() > 8) {
                return false;
            } else {
                return true;
            }
        }

        /*Only one dot, index must be <=8*/
        if (dp1 != dp2 || dp1 > 8) {
            return false;
        }

        /*Extension <=3 chars*/
        int li = fn.length() - 1;
        if (li - dp1 > 3) {
            return false;
        }

        /*OK*/
        return true;
    }

    /**
     *
     * @param fn
     * @param nma
     * @return
     */
    public static boolean createBTapeFilename(String fn, int[] nma) {

        /*verify file name*/
        if (Utils.verifyBTapeFileName(fn) == false) {
            return false;
        }

        /*Formatting according to  B-TAPE*/
        int ln = fn.length();
        int li = ln - 1;
        int di = fn.indexOf('.');

        /*No dot*/
        if (di == -1) {
            for (int z = 0; z < ln; z++) {
                nma[z] = fn.charAt(z);
            }
            return true;
        }

        /*Dot present*/
        for (int j = 0; j < di; j++) {
            nma[j] = fn.charAt(j);
        }

        /*File name*/
        int cntr = 0;
        for (int k = di + 1; k < ln; k++) {
            nma[8 + cntr] = fn.charAt(k);
            cntr++;
        }

        /*Done*/
        return true;
    }


    /**
     *
     * @param fname
     * @param ldrStart
     * @param ldrEnd
     * @param blkStart
     * @param blkEnd
     * @param maxSegments
     * @return
     */
    public static String checkForBinaryLoader(String fname, int ldrStart, int ldrEnd, int blkStart, int blkEnd, int maxSegments) {
        return checkForBinaryLoader(fname, ldrStart, ldrEnd, blkStart, blkEnd, maxSegments, false, false, null);
    }

    /**
     *
     * @param fname
     * @param ldrStart
     * @param ldrEnd
     * @param blkStart
     * @param blkEnd
     * @param maxSegments
     * @param initSegmentsHarm
     * @return
     */
    public static String checkForBinaryLoader(String fname, int ldrStart, int ldrEnd, int blkStart, int blkEnd, int maxSegments, boolean initSegmentsHarm) {
        return checkForBinaryLoader(fname, ldrStart, ldrEnd, blkStart, blkEnd, maxSegments, initSegmentsHarm, false, null);
    }

    /**
     *
     * @param fname
     * @param ldrStart
     * @param ldrEnd
     * @param blkStart
     * @param blkEnd
     * @param maxSegments
     * @param initSegmentsHarm
     * @param nullIfOk
     * @param db
     * @return
     */
    public static String checkForBinaryLoader(String fname, int ldrStart, int ldrEnd, int blkStart, int blkEnd, int maxSegments, boolean initSegmentsHarm, boolean nullIfOk, DOS2Binary db) {

        if (db == null) {
            /*First, try to load binary file*/
            File f = new File(fname);

            if (!(f.exists() && f.isFile())) {
                return "Unable to check loader: Input file does not exist or is not a regular file.";
            }

            DOS2Binary dtb;

            try {
                dtb = new DOS2Binary(fname);
                dtb.analyzeFromFile();
            } catch (IOException e) {
                return Utils.getExceptionMessage(e);

            } catch (DOS2BinaryException d2be) {
                return d2be.getFormattedMessage(true, "Unable to check loader");
            }

            db = dtb;
        }

        /*Check number of segments*/
        if (db.getTotalSegmentCount() > maxSegments) {
            return LOADER_TOO_MUCH_SEGMENTS;
        }

        /*Check if INIT harm*/
        if (initSegmentsHarm == true) {
            if (db.hasInitVector()) {
                return LOADER_HAS_INIT_SEGMENTS;
            }
        }

        /*Check whether some segment covers memory occupied by the loader*/
        boolean b1 = false;
        boolean b2 = false;

        b1 = db.coversMemory(ldrStart, ldrEnd);
        b2 = db.coversMemory(blkStart, blkEnd);

        if (b1 == true || b2 == true) {
            return LOADER_DESTROY;
        }

        /*This file will pass, except INIT segments*/
 /*If "silent mode", just return null*/
        if (nullIfOk) {
            return null;
        }

        /*Check for INIT segment*/
        if (db.hasInitVector() == true) {
            return LOADER_PROB_NOT_DESTROY;
        } else {
            return LOADER_NOT_DESTROY;
        }
    }

    private static final String LOADER_TOO_MUCH_SEGMENTS = "Binary file has too many segments to be loaded with the loader.";
    private static final String LOADER_DESTROY = "<HTML><FONT COLOR=\"RED\">Binary file will destroy the loader,<BR> because DATA segments of the binary file overlap with memory area(s) used by the loader.</FONT></HTML>";
    private static final String LOADER_PROB_NOT_DESTROY = "<HTML>Input binary file will probably not destroy the loader.<BR><BR>DATA segments do not overlap with memory area(s) used by the loader.<BR> However, the binary file contains INIT segment(s).</HTML>";
    private static final String LOADER_NOT_DESTROY = "<HTML><FONT COLOR=\"BLUE\">Binary file will not destroy the loader.</FONT></HTML>";
    private static final String LOADER_HAS_INIT_SEGMENTS = "INIT segment(s) will prevent execution of the binary file by the loader.";

    /**
     *
     * @param data
     * @param ofset
     * @param count
     * @return
     */
    public static String getPolishedFilespec(char[] data, int ofset, int count) {

        StringBuilder sb = new StringBuilder();

        for (int i = ofset; i < ofset + count; i++) {

            char ch = data[i];
            if (data[i] > 128) {
                ch = (char) (data[i] - 128);
            }
            if (Character.isLetterOrDigit(ch) || ch == '.') {
                sb.append(ch);
                continue;
            }
            if (Character.isWhitespace(ch)) {
                sb.append(" ");
                continue;
            }
            if (!Character.isLetterOrDigit(ch)) {
                sb.append("0x");
                sb.append(Integer.toHexString(data[i]));
            }
        }

        String s1 = sb.toString().trim();
        s1 = s1.replace(' ', '_');
        return s1;
    }

    /**
     *
     * @param data
     * @param ofset
     * @param count
     * @return
     */
    public static String getPolishedFilespec(int[] data, int ofset, int count) {

        char[] ch = new char[data.length];
        for (int i = 0; i < ch.length; i++) {
            ch[i] = (char) data[i];
        }
        return getPolishedFilespec(ch, ofset, count);
    }

    /**
     *
     * @param dta
     * @return
     */
    public static boolean checkKSOBlock(int[] dta) {
        int ln = dta.length;
        if (ln < 2) {
            return false;
        }

        int sum = dta[0];
        for (int i = 1; i < ln - 1; i++) {
            sum += dta[i];
        }

        if ((sum % 256) != dta[ln - 1]) {
            return false;
        } else {
            return true;
        }
    }

    /**
     *
     * @param s
     * @param numZeros
     * @return
     */
    public static String padZeros(String s, int numZeros) {

        return padChars(s, numZeros, '0');
    }

    /**
     *
     * @param s
     * @param numChars
     * @param padChar
     * @return
     */
    public static String padChars(String s, int numChars, char padChar) {
        int l = s.length();
        int k = numChars - l;
        String str = s;

        if (k < 1) {
            return s;
        }

        for (int i = 0; i < k; i++) {
            str = padChar + str;
        }

        return str;
    }

    /**
     *
     * @param byteArray
     * @return
     */
    public static int[] getAsIntArray(byte[] byteArray) {
        return getAsIntArray(byteArray, byteArray.length);
    }

    /**
     *
     * @param byteArray
     * @param numBytes
     * @return
     */
    public static int[] getAsIntArray(byte[] byteArray, int numBytes) {

        int[] intArray = new int[numBytes];

        byte b;

        for (int i = 0; i < numBytes; i++) {
            /*Orezani znamenka*/
            b = byteArray[i];
            intArray[i] = (b < 0) ? b + 256 : b;
        }

        return intArray;
    }

    /**
     *
     * @param c
     * @return
     */
    public static int ascii2Internal(char c) {

        /*No inverse character*/
        if (c > 128) {
            c -= 128;
        }

        /*Minus 32 block*/
        if (c >= 32 && c <= 95) {
            return c - 32;
        }
        /*Same block*/
        if (c >= 96 && c <= 127) {
            return c;
        }
        /*Plus 64 block*/
        if (c >= 0 && c <= 31) {
            return c + 64;
        }

        /*Non ASCII characters*/
        return 31;

    }

    /**
     *
     * @param numbers
     * @return
     */
    public static String silenceListToString(int[] numbers) {

        StringBuilder sb = new StringBuilder();
        if (numbers == null) {
            return "";
        }
        for (int i = 0; i < numbers.length; i++) {
            int k = numbers[i];
            /*if (k==-1) {
                sb.append('P');
            }*/
 /*else {*/
            if (k < 0) {
                sb.append(-k);
                sb.append('P');
            } else {
                sb.append(k);
            }
            /*}*/
            if (i < numbers.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    /**
     *
     * @param headerSpec
     * @return
     */
    public static String[] loadcsTurboHeaderFromFile(String headerSpec) {

        try {
            String[] retVal = new String[6];

            FileReader fr = new FileReader(headerSpec);
            String line;
            try (BufferedReader br = new BufferedReader(fr)) {
                line = br.readLine().trim();
            }

            /*Check number of tokens*/
            StringTokenizer tk = new StringTokenizer(line, ",");
            int numTokens = tk.countTokens();

            if (numTokens < 6 || numTokens > 7) {
                return null;
            }

            /*Check eyecatcher*/
            String s = tk.nextToken();
            if (!s.equals("cs.turbo2000.header") && !s.equals("cs.superturbo.header")) {
                return null;
            }

            for (int i = 0; i < 6; i++) {
                if (!tk.hasMoreTokens()) {
                    break;
                }
                retVal[i] = tk.nextToken();
            }

            return retVal;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    /**
     *
     * @param data
     * @return
     */
    public static boolean checkBlizzardBlock(int[] data) {
        int ln = data.length;
        if (ln < 3) {
            return false;
        }

        int sum = data[0];
        for (int i = 1; i < ln - 1; i++) {
            sum += data[i];
        }

        if ((sum % 256) != data[ln - 1]) {
            return false;
        } else {
            return true;
        }
    }

    /**
     *
     * @param data
     * @return
     */
    public static boolean checkTurboROMHeader(int[] data) {
        int sum = data[1];
        for (int i = 2; i < data.length; i++) {
            sum ^= data[i];
        }
        if (sum != data[0]) {
            return false;
        } else {
            return true;
        }
    }

    /**
     *
     * @param data
     * @param expectedCheckSum
     * @return
     */
    public static boolean checkTurboROMBlock(int[] data, int expectedCheckSum) {
        int sum = data[0];
        for (int i = 1; i < data.length; i++) {
            sum ^= data[i];
        }
        if (sum != expectedCheckSum) {
            return false;
        } else {
            return true;
        }
    }

    /**
     *
     * @param nameChars
     */
    public static void internalToAscii(char[] nameChars) {
        for (int i = 0; i < nameChars.length; i++) {

            char oldChar = nameChars[i];

            /*No inverse video*/
            if (oldChar >= 128) {
                oldChar -= 128;
            }

            if (oldChar < 63) {
                nameChars[i] = (char) (oldChar + 32);
                continue;
            }

            if (oldChar >= 64 && oldChar <= 95) {
                nameChars[i] = (char) (oldChar - 64);
                continue;
            }

            nameChars[i] = oldChar;
        }
    }

    /**
     *
     * @param data
     * @param expectedCheckSum
     * @return
     */
    public static boolean checkASTBlock(int[] data, int expectedCheckSum) {
        int chsum = data[0];
        for (int i = 1; i < data.length; i++) {
            chsum ^= data[i];
        }
        return (chsum == expectedCheckSum);
    }

    /**
     *
     * @param f
     * @return
     */
    public static String checkIfHeaderCanBeSet(File f) {
        return checkIfHeaderCanBeSet(f, true);
    }

    /**
     * Check if a header can be set using given file specifier
     *
     * @param f File
     * @param firstPart Display also first part of the text
     * @return Error message or null when file can be used
     *
     */
    public static String checkIfHeaderCanBeSet(File f, boolean firstPart) {

        String fName = f.getName();
        String firstPartText = (firstPart == true) ? "Unable to auto set header. " : "";

        if (!f.exists()) {
            return firstPartText + "File \"" + fName + "\" does not exist.";
        }
        if (!f.isFile()) {
            return firstPartText + "File \"" + fName + "\" is not a regular file.";
        }

        return null;

    }


    /**
     *
     * @param headerName
     * @param length
     * @param paddingCode
     * @return
     * @throws NumberFormatException
     */
    public static int[] getNameFromHexDump(String headerName, int length, int paddingCode) throws NumberFormatException {

        /*If not a hex dump, then there is no name*/
        if (!headerName.startsWith("$HEX$")) {
            return null;
        }

        /*Prepare return value*/
        int[] nameBytes = new int[length];
        for (int i = 0; i < nameBytes.length; i++) {
            nameBytes[i] = paddingCode;
        }

        /*Check number of tokens*/
        StringTokenizer st = new StringTokenizer(headerName, " ");
        int count = st.countTokens();

        if (count < 2) {
            return nameBytes;
        }
        st.nextToken();

        int k = 0;
        while (st.hasMoreTokens() && k < length) {
            nameBytes[k] = Integer.parseInt(st.nextToken(), 16);
            k++;
        }

        return nameBytes;

    }

    /**
     * Number a file with a number
     *
     * @param outFile File name
     * @param fileNumber File number
     * @return Numbered file name
     */
    public static String numberFile(String outFile, int fileNumber) {

        File f = new File(outFile);
        String sParent = f.getParent();
        String sChild = f.getName();

        if (sParent == null) {
            return String.format("%03d", fileNumber) + sChild;
        } else {
            return sParent + TurboDecoder.SP + String.format("%03d", fileNumber) + sChild;
        }

    }

    private Utils() {
    }
}
