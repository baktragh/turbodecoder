package turbodecoder.decoder;

/**
 * Auto-growing storage for cached file data
 *
 */
public class FileDataCache {

    private short[] storage;
    private int pointer;

    /**
     * Create new cache with default initial capacity
     */
    public FileDataCache() {
        this(1_024);
    }

    /**
     * Create new file data cache
     *
     * @param initialCapacity Initial capacity
     */
    public FileDataCache(int initialCapacity) {
        storage = new short[initialCapacity];
        pointer = 0;
    }

    /**
     * Add single byte
     *
     * @param byteToAdd Byte to add
     */
    public void add(int byteToAdd) {

        /* Check capacity*/
        if (pointer == storage.length - 1) {
            increaseCapacity(512);
        }
        /* Add instruction*/
        storage[pointer] = (short) byteToAdd;
        pointer++;

    }

    /**
     * Add bytes
     *
     * @param bytesToAdd Bytes to add
     */
    public void add(int[] bytesToAdd) {
        add(bytesToAdd, bytesToAdd.length);
    }

    /**
     * Add bytes
     *
     * @param bytesToAdd Bytes to add
     * @param numBytes How many bytes to add
     */
    public void add(int[] bytesToAdd, int numBytes) {

        /* Check capacity*/
        int free = storage.length - 1 - pointer;
        if (free < numBytes) {
            increaseCapacity(numBytes * 2);
        }
        for (int i = 0; i < numBytes; i++) {
            storage[pointer] = (short) bytesToAdd[i];
            pointer++;
        }

    }

    /**
     *
     * @return
     */
    public int[] getBytes() {
        int[] bytes = new int[pointer];
        for (int i = 0; i < pointer; i++) {
            bytes[i] = storage[i];
        }
        return bytes;
    }

    /**
     *
     * @return
     */
    public int getByteCount() {
        return pointer;
    }

    /**
     *
     */
    public void reset() {
        pointer = 0;
    }

    private void increaseCapacity(int increment) {
        int newSize = storage.length + increment;
        short[] newStorage = new short[newSize];
        System.arraycopy(storage, 0, newStorage, 0, pointer);
        storage = newStorage;

    }

}
