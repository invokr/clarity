package clarity.parser.messages;

import java.util.List;

import org.javatuples.Triplet;

import com.google.protobuf.ByteString;

public class CCLAMsg_UpdateStringTable {

    private final String tableName;
    private final List<Triplet<Integer, String, ByteString>> updates;
    
    public CCLAMsg_UpdateStringTable(String tableName, List<Triplet<Integer, String, ByteString>> updates) {
        this.tableName = tableName;
        this.updates = updates;
    }

    public String getTableName() {
        return tableName;
    }

    public List<Triplet<Integer, String, ByteString>> getUpdates() {
        return updates;
    }
    
}
