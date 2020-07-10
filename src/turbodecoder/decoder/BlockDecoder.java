/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package turbodecoder.decoder;

/**
 *
 * @author  
 */
public interface BlockDecoder {

    /**
     * Decode block using pulse decoder
     *
     * @param length Expected length of the block
     * @param constraint
     * @return BlockDecodeResult crate
     */
    public BlockDecodeResult decodeBlock(int length, Object constraint);

    /**
     *
     * @param length
     * @return
     */
    public BlockDecodeResult decodeBlock(int length);

    /**
     *
     * @param listener
     */
    void setBlockDecoderListener(BlockDecoderListener listener);

}
