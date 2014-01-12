package clarity.parser.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clarity.match.Match;
import clarity.model.StringTable;
import clarity.parser.Handler;
import clarity.parser.HandlerHelper;
import clarity.parser.messages.CCLAMsg_CreateStringTable;

public class ClaCreateStringTableHandler implements Handler<CCLAMsg_CreateStringTable> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Override
    public void apply(int peekTick, CCLAMsg_CreateStringTable message, Match match) {
        HandlerHelper.traceMessage(log, peekTick, message);
        StringTable table = new StringTable(message.getName(), message.getMaxEntries());
        match.getStringTables().add(table);
    }

}
