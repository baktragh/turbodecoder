package turbodecoder;

import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.swing.*;


/**
 *
 * @author  
 */
public class ParallelGUIUpdater {

    /**
     *
     * @param comps
     * @param b
     */
    public static void setComponentsEnabled(JComponent[] comps, boolean b) {
        try {
            SwingUtilities.invokeAndWait(new ComponentEnabler(comps, b));

        } catch (InterruptedException | InvocationTargetException e1) {

        }
    }

    /**
     *
     * @param s
     */
    public static void showMessage(String s) {
        try {
            SwingUtilities.invokeAndWait(new MessageShower(s));

        } catch (InterruptedException | InvocationTargetException e1) {

        }
    }

    /**
     *
     * @param s
     * @param val
     */
    public static void updateSlider(JSlider s, int val) {
        try {
            SwingUtilities.invokeAndWait(new SliderUpdater(s, val));
        } catch (InterruptedException | InvocationTargetException e1) {

        }
    }

    /**
     *
     * @param w
     */
    public static void pack(Window w) {
        try {
            SwingUtilities.invokeAndWait(new Packer(w));
        } catch (InterruptedException | InvocationTargetException e1) {

        }

    }
    
    public static void addMessageToTextArea(JTextArea ta,String text,boolean clear) {
        try {
            SwingUtilities.invokeAndWait(new TextAreaMessageAdder(ta, text, clear));
        
        } catch (InterruptedException | InvocationTargetException e1) {

        }
    }
    
    private static class TextAreaMessageAdder implements Runnable {
        
        private final JTextArea ta;
        private final String text;
        private final boolean clear;
        
        TextAreaMessageAdder(JTextArea ta,String text,boolean clear) {
            this.ta=ta;
            this.text=text;
            this.clear=clear;
        }
        
        public void run() {
            if (clear) ta.setText("");
            ta.append(text);
            ta.append(TurboDecoder.LN);
        }
        
    }

    private static class SliderUpdater implements Runnable {

        private final JSlider _s;
        private final int _value;

        SliderUpdater(JSlider s, int value) {
            _s = s;
            _value = value;
        }

        @Override
        public void run() {
            _s.setValue(_value);
        }
    }

    

    private static class ComponentEnabler implements Runnable {

        private final JComponent[] comps;
        private final boolean b;

        ComponentEnabler(JComponent[] cmps, boolean _b) {
            comps = cmps;
            b = _b;
        }

        @Override
        public void run() {
            int l = comps.length;
            for (int i = 0; i < l; i++) {
                comps[i].setEnabled(b);
            }
        }
    }

    private static class MessageShower implements Runnable {

        private final String message;

        MessageShower(String s) {
            message = s;
        }

        @Override
        public void run() {
            JOptionPane.showMessageDialog(null, message);
        }
    }

    

    private static class Packer implements Runnable {

        private final Window window;

        Packer(Window w) {
            window = w;
        }

        @Override
        public void run() {
            window.pack();
        }
    }

    

}
