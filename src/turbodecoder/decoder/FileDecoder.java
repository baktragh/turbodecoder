package turbodecoder.decoder;

interface FileDecoder {

    public static final int CS_TURBO_2000 = 0;
    public static final int CS_TURBO_2000_KB = 1;
    public static final int CS_SUPER_TURBO = 2;
    public static final int CS_TURBO_TAPE = 3;
    public static final int PL_KSO_TURBO_2000 = 4;
    public static final int PL_TURBO_BLIZZARD = 5;
    public static final int PL_TURBO_ROM = 6;
    public static final int PL_ATARI_SUPER_TURBO = 7;
    public static final int PL_HARD_TURBO = 8;
    public static final int PL_LOWER_SILESIA_TURBO_2000 = 9;

    public static final String[] turboSystemBriefNames = {"t2000", "t2kkb", "st", "ttape", "ksot2000", "blizzard", "turborom", "ast", "hard", "lst2000"};

    boolean decodeFile(String outdir, DecoderLog log, PulseDecoder d, DecoderConfig config) throws Exception;

}
