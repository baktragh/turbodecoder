package turbodecoder;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import javax.swing.SwingUtilities;

/*
 * Turbo Decoder, program for decoding atari turbo cassette tapes.
 */

public class TurboDecoder {
    
    /**
     * Version string
     */
    private final String TURBODECODER_VERSION_STRING = "Turbo Decoder 1.0.1";

    private String configDir;
    private DialogManager dlm;
 
    /*Some global strings*/
    public static final String LN = System.getProperty("line.separator");
    public static final String SP = System.getProperty("file.separator");
    public static final String OS_NAME = System.getProperty("os.name");
    private String programInfoString = null;

    /*Let us be a single child*/
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

            /*Redirect stdout and stderr unless a property is set*/
            if (System.getProperty("turbodecoder.noRedirect")==null) {
                decoder.redirect();
            }
           
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
        retVal.append("Turbo Decoder, tool for decoding files from 8-bit Atari turbo cassette tapes.<BR>");
        retVal.append("Operating system: ");
        retVal.append(OS_NAME).append("<BR>");
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
 
    public String getVersionString() {
        return TURBODECODER_VERSION_STRING;
    }

}
