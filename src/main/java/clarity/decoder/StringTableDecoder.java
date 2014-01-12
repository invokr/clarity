package clarity.decoder;

import java.util.LinkedList;

import org.javatuples.Triplet;

import com.google.protobuf.ByteString;

public class StringTableDecoder {

    private static final int MAX_NAME_LENGTH = 0x400;
    private static final int KEY_HISTORY_SIZE = 32;
    
    private final BitStream stream;
    private final int bitsPerIndex;
    private final int numEntries;
    private final boolean userDataFixedSize;
    private final int userDataSizeBits;
    
    private final LinkedList<String> keyHistory;
    private final boolean mysteryFlag;
    private final StringBuffer nameBuf;
    
    private int nRead = 0;
    private int index = -1;
    
    public StringTableDecoder(byte[] data, int numEntries, int maxEntries, boolean userDataFixedSize, int userDataSizeBits) {
        this.stream = new BitStream(data);
        this.bitsPerIndex = Util.calcBitsNeededFor(maxEntries - 1);
        this.numEntries = numEntries;
        this.userDataFixedSize = userDataFixedSize;
        this.userDataSizeBits = userDataSizeBits;
        this.keyHistory = new LinkedList<String>();
        this.mysteryFlag = stream.readBit();
        this.nameBuf = new StringBuffer();
    }
    
    public Triplet<Integer, String, ByteString> read() {
        if (nRead == numEntries) {
            return null;
        }
        
        // read index
        if (stream.readBit()) {
            index++;
        } else {
            index = stream.readNumericBits(bitsPerIndex);
        }
        // read name
        nameBuf.setLength(0);
        if (stream.readBit()) {
            if (mysteryFlag && stream.readBit()) {
                throw new RuntimeException("mystery_flag assert failed!");
            }
            if (stream.readBit()) {
                int basis = stream.readNumericBits(5);
                int length = stream.readNumericBits(5);
                nameBuf.append(keyHistory.get(basis).substring(0, length));
                nameBuf.append(stream.readString(MAX_NAME_LENGTH - length));
            } else {
                nameBuf.append(stream.readString(MAX_NAME_LENGTH));
            }
            if (keyHistory.size() == KEY_HISTORY_SIZE) {
                keyHistory.remove(0);
            }
            keyHistory.add(nameBuf.toString());
        }
        // read value
        ByteString value = null;
        if (stream.readBit()) {
            int bitLength = 0;
            if (userDataFixedSize) {
                bitLength = userDataSizeBits;
            } else {
                bitLength = stream.readNumericBits(14) * 8;
            }

            value = ByteString.copyFrom(stream.readBits(bitLength));
        }
        
        nRead++;
        return new Triplet<Integer, String, ByteString>(index, nameBuf.toString(), value);
    }
}
