package turbodecoder;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/*
 TURGEN SYSTEM, conversion of files to turbo systems and standard tape records
 Copyright (C) 2006-2020 Michael Kalouš

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 */

public class TurboDecoder {
    
    /**
     * Version string
     */
    private final String TURBODECODER_VERSION_STRING = "Turbo Decoder 0.0.1";

    /**
     * Directory with TurboDecoder.jar file
     */
    private String dirPrefix;

    /**
     * Directory with configuration
     */
    private String configDir;

    /**
     * Owner of all dialogs and frames, takes care about storing/restoring
     * bounds etc.
     */
    private DialogManager dlm;

   
    /*Some global strings*/

    public static final String LN = System.getProperty("line.separator");
    public static final String SP = System.getProperty("file.separator");
    public static final String OS_NAME = System.getProperty("os.name");
    private String programInfoString = null;

    
    private static TurboDecoder singletonInstance=null;
    
    public static TurboDecoder getInstance() {
        if (singletonInstance==null) {
            singletonInstance=new TurboDecoder();
        }
        return singletonInstance;
    }
    
    private TurboDecoder() {
        
    }
    
    /**
     * @param args Command line parameters
     */
    public static void main(String[] args) {
        
        try {
            
            TurboDecoder decoder = TurboDecoder.getInstance();
            
            /*Retrieve or create config directory*/
            decoder.retrieveConfigDir();

            /*Redirect stdout and stderr*/
            if (System.getProperty("turbodecoder.noRedirect")==null) {
                decoder.redirect();
            }
           

            /*Where is TurboDecoder.jar*/
            decoder.retrieveDirPrefix();
            
            /*Create GUI*/
            decoder.createDialogs();
            
            /*Show main window*/
            decoder.getDlm().setDecoderTransientFrameVisible(true);
            

        } catch (Exception xxe) {
            xxe.printStackTrace();
            System.exit(-1);
        }
        
        
        
    }
    
    public DialogManager getDlm() {
        return this.dlm;
    }
    
    
    
    private void createDialogs() {
        /*Initialize main parts of GUI*/
        dlm = DialogManager.getInstance();
        dlm.initDialogs();
        dlm.loadBounds();
    }
    
    public String getConfigDir() {
        return configDir;
    }

    /**
     * Terminate program
     */
    void exit() {

        if (dlm != null) {
            dlm.saveBounds();
        }

        System.exit(0);
    }

    /**
     * Forcible termination
     */
    void forcedExit() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    exit();
                }
            });
        } catch (InterruptedException | InvocationTargetException e1) {
            e1.printStackTrace();
        }
    }

    void redirect() {
        try {
            /*Check size*/
            File f = new File(configDir + "turbodecoder.log");

            /*If the file is larger than 4 MB then wrap around*/
            if (f.exists() && f.isFile() && f.length() > 4 * 1_024 * 1_024) {
                f.delete();
            }

            FileOutputStream fw = new FileOutputStream(configDir + "turgen.log", true);
            PrintStream os = new PrintStream(fw);
            System.setOut(os);
            System.setErr(os);
            System.out.println(new Date(System.currentTimeMillis()).toString());
        } catch (FileNotFoundException e) {
            /*No remedy*/
            e.printStackTrace();
        }
    }

   
    /**
     * Locate program directory 1. Try an equivalent of argv[0] 2. Try current
     * directory 3. Fail
     */
    void retrieveDirPrefix() throws Exception {
        File jarFile;

        /*From JAR file*/
        try {
            URL u = TurboDecoder.class.getProtectionDomain().getCodeSource().getLocation();
            URI i = new URI(u.toString());
            String s = i.getPath();

            jarFile = new File(s);
            dirPrefix = jarFile.getParentFile().getAbsolutePath().concat(System.getProperty("file.separator"));

            File f = new File(dirPrefix + "turbodecoder.jar");
            if (f.exists() && f.isFile()) {
                return;
            }
        } catch (URISyntaxException e) {
            /*Intentionally blank*/
        }

        /*From user's working directory*/
        dirPrefix = System.getProperty("user.dir") + System.getProperty("file.separator");
        final File f1 = new File(dirPrefix + "turbodecoder.jar");

        /*If decoder.jar does not exist, then*/
        if (!(f1.exists())) {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(null, "Unable to locate program directory");
                }
            });
            System.exit(-1);
        }

    }

    /**
     * Retrieve or create configuration directory
     */
    void retrieveConfigDir() {

        configDir = System.getProperty("user.home");
        configDir += SP + ".turbodecoder";
        /*Create directory if necessary*/
        File f = new File(configDir);
        if (f.exists() == false) {
            f.mkdir();
        }
        /*configDir string will contain trailing directory separator*/
        configDir += SP;

    }

    
    /**
     * Returns basic information about TurboDecoder System
     *
     * @return Information about TurboDecoder System
     */
    public String getProgramInfoString() {

        if (programInfoString != null) {
            return programInfoString;
        }

        StringBuilder retVal = new StringBuilder(64);
        retVal.append("<HTML>" + TURBODECODER_VERSION_STRING + "<BR><BR>");
        retVal.append("Turbo Decoder<BR>");
        retVal.append("Operating system: ");
        retVal.append(OS_NAME).append("<BR>");
        retVal.append("Program directory: ");
        retVal.append(dirPrefix.substring(0, dirPrefix.length() - 1)).append("<BR>");
        retVal.append("Directory with configuration: ");
        retVal.append(configDir.substring(0, configDir.length() - 1)).append("<BR>");
        retVal.append("File separator: ");
        retVal.append(SP);
        retVal.append("<BR>");
        retVal.append("Java version: ");
        retVal.append(System.getProperty("java.version"));
        retVal.append("<BR>");
        
        retVal.append("Java home: ");
        retVal.append(System.getProperty("java.home"));
        retVal.append("</HTML>");
       

        programInfoString = retVal.toString();
        return programInfoString;

    }

    public String getDirPrefix() {
        return dirPrefix;
    }
    
    public String getVersionString() {
        return TURBODECODER_VERSION_STRING;
    }

}
