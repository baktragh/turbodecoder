package turbodecoder.decoder.dsp;

/** Simple implementation of a Schmitt trigger */
public class Schmitt {
    
    private int tolerance=0;
    private int prevSample=0;
    private boolean currentOutput=false;
    private int threshold=0;
    
    public void init(int tolerance,int threshold) {
        this.tolerance=tolerance;
        this.prevSample=0;
        this.currentOutput=false;
        this.threshold=threshold;
    }
    
    public boolean getOutput(int sample) {
        if (currentOutput==true) {
		currentOutput = (sample >= (threshold+(prevSample - tolerance)));
        }
        else {
 		currentOutput = (sample > (threshold+(prevSample + tolerance)));
        }
	prevSample = sample;
	return currentOutput;
        
    }
    
}
