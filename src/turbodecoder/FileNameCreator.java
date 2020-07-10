package turbodecoder;

import java.util.StringTokenizer;

/**
 *
 * @author  
 */
public class FileNameCreator {

    /**
     *
     * @param fileName File name
     * @return File name without periods except the last one
     */
    public static String removeMultiplePeriods(String fileName) {

        /*Check if the period is there*/
        int pos = fileName.lastIndexOf('.');
        if (pos == -1) {
            return fileName;
        }

        StringBuilder sb = new StringBuilder();
        char[] nameChars = fileName.toCharArray();

        /*All periods before position of the last period are filtered out*/
        for (int i = 0; i < pos; i++) {
            if (nameChars[i] != '.') {
                sb.append(nameChars[i]);
            }
        }

        /*The rest of the string is copied as is*/
        for (int i = pos; i < nameChars.length; i++) {
            sb.append(nameChars[i]);
        }

        return sb.toString();

    }

    /**
     *
     * @param name
     * @param maxTotalLength
     * @param maxExtLength
     * @param periodCounts
     * @param config
     * @return
     */
    public static String createFileName(String name, int maxTotalLength, int maxExtLength, boolean periodCounts, NameCreatorConfig config) {

        /*Use working variable for name*/
        String workName = name;
        String workExt = "";

        /*Capitalization first*/
        if (config.capitalize == true) {
            workName = workName.toUpperCase();
        }

        /*Then remove spaces if requested*/
        if (config.removeSpaces == true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workName.length(); i++) {
                if (!Character.isWhitespace(workName.charAt(i))) {
                    sb.append(workName.charAt(i));
                }
            }
            workName = sb.toString();
        }

        /*Check for emptiness*/
        if (workName.isEmpty()) {
            return workName;
        }

        int charsForName = maxTotalLength;

        /*Use extension if requested*/
        if (config.createExtension == true) {

            /*Determine if there is an extension. Extension is a portion of name after the last period*/
            int periodPos = workName.lastIndexOf('.');

            /*No period, no extension*/
            if (periodPos == -1) {
                workExt = "";
            } /*Period, possibly extension*/ else {
                int numAfter = workName.length() - 1 - periodPos;
                if (numAfter == 0) {
                    workExt = "";
                } else {
                    workExt = workName.substring(periodPos + 1, periodPos + 1 + numAfter);
                }

            }

            /*Shorten the extension if needed*/
            if (workExt.length() > maxExtLength) {
                workExt = workExt.substring(0, maxExtLength);
            }

            charsForName -= maxExtLength;
            if (periodCounts) {
                charsForName--;
            }

            /*Get the resulting name. Characters up to the first period if
              it exists
             */
            if (periodPos >= 0) {
                workName = workName.substring(0, periodPos == 0 ? 0 : periodPos);
            }
        } /*Do not use extension - limit us up to the last period*/ else {
            int periodPos = workName.lastIndexOf('.');

            if (periodPos == 0) {
                workName = "";
            }
            if (periodPos > 0) {
                workName = workName.substring(0, periodPos);
            }
        }

        /*No characters left for name, return just extension*/
        if (charsForName <= 0) {
            return "." + workExt;
        }

        /*Use the characters left to create a name*/
 /*When using TOSEC convention, use only characters up to left parenthesis*/
        if (config.useTOSECConvention) {
            int lParenPos = workName.indexOf('(');

            if (lParenPos == 0) {
                workName = "";
            }
            if (lParenPos > 0) {
                workName = workName.substring(0, lParenPos);
            }
        }

        if (workName.length() > charsForName) {
            workName = workName.substring(0, charsForName);
        }

        if (config.createExtension && !workExt.isEmpty()) {
            return workName + "." + workExt;
        } else {
            return workName;
        }

    }

    /**
     *
     */
    public static class NameCreatorConfig {

        /**
         *
         */
        public boolean createExtension;

        /**
         *
         */
        public boolean useTOSECConvention;

        /**
         *
         */
        public boolean removeSpaces;

        /**
         *
         */
        public boolean capitalize;

        /**
         *
         * @param createExtension
         * @param useTOSECConvention
         * @param removeSpaces
         * @param capitalize
         */
        public NameCreatorConfig(boolean createExtension, boolean useTOSECConvention, boolean removeSpaces, boolean capitalize) {
            this.createExtension = createExtension;
            this.useTOSECConvention = useTOSECConvention;
            this.removeSpaces = removeSpaces;
            this.capitalize = capitalize;
        }

        /**
         *
         * @return
         */
        public String toPropertyValueString() {
            StringBuilder sb = new StringBuilder();
            sb.append(createExtension ? "1" : "0");
            sb.append('%');
            sb.append(useTOSECConvention ? "1" : "0");
            sb.append('%');
            sb.append(removeSpaces ? "1" : "0");
            sb.append('%');
            sb.append(capitalize ? "1" : "0");
            
            return sb.toString();
        }

        /**
         *
         * @param value
         */
        public void readFromPropertyValue(String value) {
            StringTokenizer tk = new StringTokenizer(value, "%");
            try {
                if (tk.hasMoreTokens()) {
                    int i = Integer.parseInt(tk.nextToken());
                    createExtension = (i == 1);
                }
                if (tk.hasMoreTokens()) {
                    int i = Integer.parseInt(tk.nextToken());
                    useTOSECConvention = (i == 1);
                }
                if (tk.hasMoreTokens()) {
                    int i = Integer.parseInt(tk.nextToken());
                    removeSpaces = (i == 1);
                }
                if (tk.hasMoreTokens()) {
                    int i = Integer.parseInt(tk.nextToken());
                    capitalize = (i == 1);
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

}
