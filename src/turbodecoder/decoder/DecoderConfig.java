package turbodecoder.decoder;

import java.io.Serializable;

/**
 *
 * @author  
 */
public class DecoderConfig implements Serializable {

    /*Turbo ROM*/

    /**
     *
     */
    public static final int PL_TURBO_ROM_FILE_FORMAT_BINARY = 0;

    /**
     *
     */
    public static final int PL_TURBO_ROM_FILE_FORMAT_BASIC = 1;
    /*Lower Silesian Turbo 2000*/

    /**
     *
     */
    public static final int PL_LOWER_SILESIAN_TURBO_2000_FORMAT_AUTOTURBO = 0;

    /**
     *
     */
    public static final int PL_LOWER_SILESIAN_TURBO_2000_FORMAT_UE_UNPROTECTED = 1;

    /**
     *
     */
    public static final int PL_LOWER_SILESIAN_TURBO_2000_FORMAT_UE_PROTECTED = 2;

    /**
     *
     */
    public static final int PL_LOWER_SILESIAN_TURBO_2000_FORMAT_FC10_PROTECTED = 3;
    /*KSO Turbo 2000*/

    /**
     *
     */
    public static final int PL_KSO_TURBO_2000_FORMAT_NATURAL = 0;

    /**
     *
     */
    public static final int PL_KSO_TURBO_2000_FORMAT_WITH_LOADER = 1;


    /*General settings*/

    /**
     *
     */

    public boolean genPrependSampleNumber;

    /**
     *
     */
    public boolean genIgnoreBadSum;

    /**
     *
     */
    public boolean genPreferAdaptiveSpeedDetection;

    /**
     *
     */
    public boolean genVerboseMessages;

    /*Monitor mode settings*/

    /**
     *
     */

    public boolean monitorSaveAllBytes;

    /*Turbo 2000 and Super Turbo (Czechoslovakia)*/

    /**
     *
     */
    
    public boolean dspBlockDCOffset;
    public int dspSchmittHysteresis;

    public boolean csTurboSaveHeaderToExtraFile;

    /**
     *
     */
    public boolean csTurboAlwaysSaveAsBinary;

    /**
     *
     */
    public int turboROMFileFormat;

    /**
     *
     */
    public int lowerSilesianTurbo2000FileFormat;

    /**
     *
     */
    public int ksoTurbo2000FileFormat;

    /**
     *
     */
    public DecoderConfig() {
        setAllDefaults();
    }

    /**
     *
     */
    public void csTurboDefaults() {
        csTurboSaveHeaderToExtraFile = false;
        csTurboAlwaysSaveAsBinary = false;

    }

    /**
     *
     */
    public void turboROMDefaults() {
        turboROMFileFormat = PL_TURBO_ROM_FILE_FORMAT_BINARY;
    }

    /**
     *
     */
    public void lowerSilesianTurbo2000Defaults() {
        lowerSilesianTurbo2000FileFormat = PL_LOWER_SILESIAN_TURBO_2000_FORMAT_AUTOTURBO;

    }

    /**
     *
     */
    public void ksoTurbo2000Defaults() {
        ksoTurbo2000FileFormat = PL_KSO_TURBO_2000_FORMAT_NATURAL;
    }

    /**
     *
     */
    public void hardTurboDefaults() {

    }

    /**
     *
     */
    public void monitorDefaults() {
        monitorSaveAllBytes = false;
    }
    
    public void dspDefaults() {
        dspBlockDCOffset=true;
        dspSchmittHysteresis=0;
    }

    /**
     *
     */
    public void generalDefaults() {
        genPrependSampleNumber = true;
        genPreferAdaptiveSpeedDetection = false;
        genIgnoreBadSum = false;
        genVerboseMessages = true;
    }

    private void setAllDefaults() {
        generalDefaults();
        monitorDefaults();
        dspDefaults();
        csTurboDefaults();
        turboROMDefaults();
        lowerSilesianTurbo2000Defaults();
        hardTurboDefaults();
        ksoTurbo2000Defaults();
    }


}
