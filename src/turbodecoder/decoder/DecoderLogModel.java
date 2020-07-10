/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package turbodecoder.decoder;

import java.util.ArrayList;
import javax.swing.AbstractListModel;

/**
 *
 * @author  
 */
public class DecoderLogModel extends AbstractListModel<DecoderMessage> {

    private final ArrayList<DecoderMessage> messages = new ArrayList<>();

    /**
     *
     * @return
     */
    @Override
    public int getSize() {
        return messages.size();
    }

    /**
     *
     * @param index
     * @return
     */
    @Override
    public DecoderMessage getElementAt(int index) {
        return messages.get(index);
    }

    void addMessage(DecoderMessage msg) {
        messages.add(msg);
        fireIntervalAdded(this, messages.size() - 1, messages.size() - 1);
    }

    void clearAllMessages() {
        int formerSize = messages.size();
        if (formerSize==0) return;
        messages.clear();
        fireIntervalRemoved(this, 0, formerSize - 1);
    }

    ArrayList<DecoderMessage> getMessages() {
        return messages;
    }
}
