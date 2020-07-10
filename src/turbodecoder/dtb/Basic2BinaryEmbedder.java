package turbodecoder.dtb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import turbodecoder.FileFormatException;
import turbodecoder.Logger;
import turbodecoder.TurboDecoder;
import turbodecoder.Utils;

/**
 * Embed tokenized BASIC file to binary file
 */
public class Basic2BinaryEmbedder {
    private static final int[] launchRoutine = {
        0xa9, 0xfd, 0x8d, 0x01, 0xd3, 0xa2, 0x00, 0x86, 0x09, 0xbd,
        0x3f, 0x10, 0x95, 0x80, 0xe8, 0xe0, 0x0e, 0xd0, 0xf6, 0x38,
        0xb0, 0x0a, 0xa9, 0x01, 0x8d, 0x44, 0x02, 0xa9, 0x9e, 0x8d,
        0x36, 0x02, 0xa2, 0xff, 0x9a, 0xa9, 0xa9, 0x48, 0xa9, 0x79,
        0x48, 0xa9, 0x0a, 0x85, 0xc9, 0xa9, 0x00, 0x8d, 0xf8, 0x03,
        0xa9, 0xb7, 0x48, 0xa9, 0x54, 0x48, 0xa9, 0xa0, 0x85, 0x6a,
        0x4c, 0x94, 0xef, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e
    };

    private final String outputDirectory;
    private final String[] basicFiles;
    private final int baseAddress;
    private final Logger log;
    private final boolean protect;

    /**
     *
     * @param outputDirectory
     * @param basicFiles
     * @param baseAddress
     * @param log
     * @param protect
     */
    public Basic2BinaryEmbedder(String outputDirectory, String[] basicFiles, int baseAddress, Logger log, boolean protect) {
        this.outputDirectory = outputDirectory;
        this.basicFiles = basicFiles;
        this.baseAddress = baseAddress;
        this.log = log;
        this.protect = protect;
    }

    /**
     *
     */
    public void embedFiles() {

        /*Clear report*/
        log.clear();

        /*Embed every BASIC file*/
        for (String basicFile : basicFiles) {
            /*Prepare output file name*/
            File filespec = new File(basicFile);
            String filename = Utils.changeFileExtension(filespec.getName(), ".xex");
            File outFilespec = new File(outputDirectory + TurboDecoder.SP + filename);
            String outName = "";
            try {
                outName = outFilespec.getCanonicalPath();
            } catch (IOException ioe) {
                log.addMessage(filespec.getName() + " [ERROR] Unable to obtain canonical path" + TurboDecoder.LN, true);
                continue;
            }
            /*Embed file*/
            String msg = embedFile(basicFile, outName, protect);
            /*Issue message*/
            log.addMessage(filespec.getName() + " --> " + outName + " " + msg + TurboDecoder.LN, true);
        }
    }

    private String embedFile(String fileName, String target, boolean protect) {
        try {

            int flength;
            byte[] rawBytes;

            int[] fileData;
            try (RandomAccessFile raf = new RandomAccessFile(fileName, "r")) {
                flength = (int) raf.length();
                /*Check length*/
                if (flength < 15) {
                    raf.close();
                    throw new FileFormatException("Input file is too short to be a valid tokenized ATARI BASIC file");
                }   if (flength > 48 * 1_024) {
                    raf.close();
                    throw new FileFormatException("Input file is too long to be a valid tokenized ATARI BASIC file");
                }
                /*Read  all*/
                rawBytes = new byte[flength];
                fileData = new int[rawBytes.length];
                raf.readFully(rawBytes);
            }

            /*Consolidate raw bytes so they can be processed as ints*/
            for (int i = 0; i < rawBytes.length; i++) {
                fileData[i] = rawBytes[i] < 0 ? rawBytes[i] + 256 : rawBytes[i];
            }

            if (fileData[0] != 0 || fileData[1] != 0) {
                throw new FileFormatException("Input file is not a tokenized ATARI BASIC file - first two bytes are not zeros");
            }

            /*Pick significant addresses*/
            int startAddr = baseAddress + fileData[2] + fileData[3] * 256;
            int endAddr = startAddr + flength - 14 + launchRoutine.length - 1;

            /*Check if the file will fit into memory. BASIC ROM starts
             * at 40960
             */
            if (endAddr >= 40_960) {

                int minStartAddr = baseAddress - (endAddr - 40_960) - 1;
                if (minStartAddr < 512) {
                    throw new FileFormatException("The resulting file will not fit below address 40960 ($A000)");
                } else {
                    throw new FileFormatException("The resulting file will not fit below address 40960 ($A000). Highest possible base address is " + Integer.toString(minStartAddr));
                }
            }

            /*Output stream*/
            FileOutputStream fos = new FileOutputStream(target);

            /*Write header*/
            fos.write(255);
            fos.write(255);
            fos.write(startAddr % 256);
            fos.write(startAddr / 256);
            fos.write(endAddr % 256);
            fos.write(endAddr / 256);

            /*Write body of the basic program*/
            fos.write(rawBytes, 14, flength - 14);

            /*Prepare routine*/
            int[] routine = new int[launchRoutine.length];
            System.arraycopy(launchRoutine, 0, routine, 0, launchRoutine.length);

            /*Relocate vector table address*/
            routine[0xA] = (startAddr + flength - 14 + 0x3F) % 256;
            routine[0xB] = (startAddr + flength - 14 + 0x3F) / 256;

            /*Relocate vectors*/
            for (int i = 0; i < 7; i++) {
                int addr = fileData[i * 2] + 256 * fileData[i * 2 + 1];
                addr += baseAddress;
                routine[0x3F + (2 * i)] = addr % 256;
                routine[0x3F + (2 * i) + 1] = addr / 256;
            }

            /*Enable/disable protection*/
            if (protect == true) {
                routine[0x14] = 0xEA;
                routine[0x15] = 0xEA;
            }

            /*Write modified routine*/
            for (int i = 0; i < routine.length; i++) {
                fos.write(routine[i]);
            }

            /*Run address*/
            fos.write(736 % 256);
            fos.write(736 / 256);
            fos.write(737 % 256);
            fos.write(737 / 256);

            fos.write((startAddr + flength - 14) % 256);
            fos.write((startAddr + flength - 14) / 256);

            fos.flush();
            fos.close();

            return "[OK]";
        } catch (IOException | FileFormatException e) {
            e.printStackTrace();
            return "[ERROR]" + TurboDecoder.LN + Utils.getExceptionMessage(e);

        }
    }


}
