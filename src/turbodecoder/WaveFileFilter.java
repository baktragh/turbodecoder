package turbodecoder;

import java.io.File;

public class WaveFileFilter extends javax.swing.filechooser.FileFilter {

        @Override
        public boolean accept(File file) {
            
            if (!file.isFile()) return true;
            
            String p = file.getName().toLowerCase();
            if (p.endsWith(".xex") || p.endsWith(".com") ||  p.endsWith(".obj")) {
                return true;
            }
            return false;
        }

        @Override
        public String getDescription() {
            return "Atari binary files (.xex,.com,.obj)";
        }
        
    }