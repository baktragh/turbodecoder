package turbodecoder;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import turbodecoder.decoder.DecoderFrame;

/**
 * Centralized Frame and Dialog handling
 */
public class DialogManager {

    /**
     *
     * @return
     */
    public static DialogManager getInstance() {
        return new DialogManager();
    }
    /**
     * Center container
     *
     * @param c Container to center
     */
    public static void centerContainer(Container c) {
        Dimension _d = Toolkit.getDefaultToolkit().getScreenSize();
        int _x, _y;
        _x = (_d.width - c.getBounds().width) / 2;
        _y = (_d.height - c.getBounds().height) / 2;
        c.setLocation(_x, _y);
    }

    
    /**
     * Frame for turbo decoder
     */
    DecoderFrame frmDecoder;

    

    private UIPersistor[] residentWindowArray;

    private final HashMap<String,Object> uiPersistenceMap;

    private DialogManager() {
        /*Look and feel*/
        uiPersistenceMap = new HashMap<>();
        tryWindowsLookAndFeel();
    }


    /**
     * Create all dialogs
     */
    public void initDialogs() {

        /*Initialize transient frames*/
        frmDecoder = null;
    }

    /**
     * Restore frame/dialog bounds. If restoration not possible, use defaults
     */
    void loadBounds() {

        FileInputStream fis;
        DataInputStream dis;
        ObjectInputStream ois = null;

        /*Try to load the bounds*/
        try {

            fis = new FileInputStream(TurboDecoder.getInstance().getConfigDir() + "winlayout.ser");
            dis = new DataInputStream(fis);

            int numObjects = dis.readInt();

            for (int i = 0; i < numObjects; i++) {
                String key = dis.readUTF();
                int objectLength = dis.readInt();
                byte[] objectBytes = new byte[objectLength];
                dis.read(objectBytes);

                try {
                    ByteArrayInputStream bais = new ByteArrayInputStream(objectBytes);
                    ois = new ObjectInputStream(bais);
                    Object value = ois.readObject();
                    uiPersistenceMap.put(key, value);
                    ois.close();
                } catch (IOException | ClassNotFoundException ex) {
                    ex.printStackTrace();
                    if (ois != null) {
                        ois.close();
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } /*Now we have the persistence map*/ 
        catch (Exception genE) {
            genE.printStackTrace();
        }
        finally {

        }

    }

    /**
     * Save configuration
     */
    public void saveBounds() {

        FileOutputStream fos;
        DataOutputStream dos;

        closeTransientFrames();

        try {
            fos = new FileOutputStream(TurboDecoder.getInstance().getConfigDir()+ "winlayout.ser");
            dos = new DataOutputStream(fos);

            /*First number of objects*/
            dos.writeInt(uiPersistenceMap.size());

            Set keySet = uiPersistenceMap.keySet();
            Iterator it = keySet.iterator();

            while (it.hasNext()) {

                String key = (String) it.next();
                Object value = uiPersistenceMap.get(key);

                /*Temporary stream*/
                ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
                try (ObjectOutputStream toos = new ObjectOutputStream(baos)) {
                    toos.writeObject(value);
                    toos.flush();
                }
                byte[] objectBytes = baos.toByteArray();

                /*Write key, length and value*/
                dos.writeUTF(key);
                dos.writeInt(objectBytes.length);
                dos.write(objectBytes);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

   

    /**
     *
     * @param visible
     */
    public void setDecoderTransientFrameVisible(boolean visible) {

        if (visible == true) {
            if (frmDecoder == null) {
                frmDecoder = new DecoderFrame();
                getPersistenceForTransientDialog(frmDecoder);
            }
            frmDecoder.setVisible(true);

        } else if (frmDecoder != null) {
            uiPersistenceMap.put(frmDecoder.getPersistenceId(), frmDecoder.getPersistenceData());
            frmDecoder.setVisible(false);
            frmDecoder.dispose();
            frmDecoder = null;
        }
    }

    private void getPersistenceForTransientDialog(UIPersistor p) {
        if (uiPersistenceMap.containsKey(p.getPersistenceId())) {
            try {
                p.setPersistenceData(((Object) (uiPersistenceMap.get(p.getPersistenceId()))));
            } catch (Exception e) {
                e.printStackTrace();
                p.setPersistenceDefaults();
                uiPersistenceMap.put(p.getPersistenceId(), p.getPersistenceData());
            }
        } else {
            p.setPersistenceDefaults();
            uiPersistenceMap.put(p.getPersistenceId(), p.getPersistenceData());
        }
    }

    private void closeTransientFrames() {
        setDecoderTransientFrameVisible(false);
    }
    
    private void tryWindowsLookAndFeel() {

        /*Check if running on Windows. If not, just return*/
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }

        /*Get all look and feel classes*/
        LookAndFeelInfo[] lafInfos = UIManager.getInstalledLookAndFeels();
        String plafClasses[] = new String[lafInfos.length];

        for (int i = 0; i < lafInfos.length; i++) {
            plafClasses[i] = lafInfos[i].getClassName();
        }

        /*Check if there is a look and feel for windows*/
        String windowsLaF = null;

        for (String plafClassName : plafClasses) {
            String lc = plafClassName.toLowerCase();

            /*Windows and not classic*/
            if (lc.contains("windows") && !lc.contains("classic")) {
                windowsLaF = plafClassName;
                break;
            }
            /*Just windows*/
            if (lc.contains("windows")) {
                windowsLaF = plafClassName;
                break;
            }
        }

        /*No Look and Feel found, just return*/
        if (windowsLaF == null) {
            return;
        }

        /*Set Look and Feel found for Windows*/
        try {
            UIManager.setLookAndFeel(windowsLaF);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e1) {
            e1.printStackTrace();
        }

    }

}
