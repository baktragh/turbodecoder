package turbodecoder.dtb;

import java.io.File;

/**
 * Vyjimka je vyhozena, jestlize soubor nema spravny format. Zprava obsahuje
 * popis chyby.
 */
public class DOS2BinaryException extends Exception {

    /**
     * Zprava
     */
    private final String message;
    private final int offset;
    private final String filename;

    /**
     *
     * @param filename
     * @param message
     * @param offset
     */
    public DOS2BinaryException(String filename, String message, int offset) {
        this.message = message;
        this.offset = offset;
        File f = new File(filename);
        this.filename = f.getName();
    }

    /**
     * Pozadavek na vraceni zpravy
     *
     * @return Zprava
     */
    @Override
    public String getMessage() {
        return getMessageString();
    }

    /**
     *
     * @return
     */
    @Override
    public String toString() {
        return getClass().getName() + " " + getMessageString();
    }

    private String getMessageString() {
        StringBuilder sb = new StringBuilder();
        sb.append(filename);
        sb.append(": ");
        sb.append(message);
        if (!message.endsWith(".")) {
            sb.append('.');
        }
        sb.append(" Offset: ");
        sb.append(offset);
        sb.append(" [0x");
        sb.append(Integer.toHexString(offset).toUpperCase());
        sb.append(']');
        return sb.toString();
    }

    /**
     *
     * @return
     */
    public String getFormattedMessage() {
        return getFormattedMessage(false, null);
    }

    /**
     *
     * @param corruptHeader
     * @return
     */
    public String getFormattedMessage(boolean corruptHeader) {
        return getFormattedMessage(corruptHeader, null);
    }

    /**
     *
     * @param corruptHeader
     * @param extraHeader
     * @return
     */
    public String getFormattedMessage(boolean corruptHeader, String extraHeader) {
        StringBuilder sb = new StringBuilder();
        sb.append("<HTML>");

        if (extraHeader != null) {
            sb.append("<b>");
            sb.append(extraHeader);
            sb.append("</b><BR>");
        }

        if (corruptHeader == true) {
            sb.append("<b>Binary file is corrupt</b><BR><BR>");
        }

        sb.append(getClass().getName());
        sb.append(": ");
        sb.append(filename);
        sb.append("<BR>");
        sb.append(message);
        if (!message.endsWith(".")) {
            sb.append('.');
        }
        sb.append("<BR>");
        sb.append(" Offset: ");
        sb.append(offset);
        sb.append(" [0x");
        sb.append(Integer.toHexString(offset).toUpperCase());
        sb.append(']');
        sb.append("</HTML>");
        return sb.toString();
    }
}
