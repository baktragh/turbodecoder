/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package turbodecoder.decoder.dsp;

public class DCBlocker {

    int xm1;
    int ym1;
    double timeConstant;

    public DCBlocker(double timeConstant) {
        xm1 = 0;
        ym1 = 0;
        this.timeConstant = timeConstant;
    }

    public int getOutputValue(int inputValue) {
        int tempValue = inputValue - xm1 + (int) Math.round(timeConstant * ym1);
        xm1 = inputValue;
        ym1 = tempValue;
        return tempValue;
    }
    
    public void reset() {
        xm1=0;
        ym1=0;
    }

}
