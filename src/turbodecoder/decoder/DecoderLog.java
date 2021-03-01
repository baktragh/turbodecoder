package turbodecoder.decoder;

public interface DecoderLog {

    public void addMessage(DecoderMessage msg, boolean fromExternalThread);

    public void clearAllMessages(boolean fromExternalThread);

    public void impulse(boolean fromExternalThread);
}
