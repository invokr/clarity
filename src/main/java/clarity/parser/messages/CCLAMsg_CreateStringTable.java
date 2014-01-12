package clarity.parser.messages;

public class CCLAMsg_CreateStringTable {

    private final String name;
    private final int maxEntries;
    
    public CCLAMsg_CreateStringTable(String name, int maxEntries) {
        this.name = name;
        this.maxEntries = maxEntries;
    }

    public String getName() {
        return name;
    }

    public int getMaxEntries() {
        return maxEntries;
    }
    
}
