package clarity.parser.handler;

import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clarity.match.Match;
import clarity.model.StringTable;
import clarity.parser.Handler;
import clarity.parser.HandlerHelper;
import clarity.parser.messages.CCLAMsg_UpdateStringTable;

import com.google.protobuf.ByteString;

public class ClaUpdateStringTableHandler implements Handler<CCLAMsg_UpdateStringTable> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Override
    public void apply(int peekTick, CCLAMsg_UpdateStringTable message, Match match) {
        HandlerHelper.traceMessage(log, peekTick, message);
        StringTable table = match.getStringTables().forName(message.getTableName());
        for (Triplet<Integer, String, ByteString> u : message.getUpdates()) {
            table.set(u.getValue0(), u.getValue1(), u.getValue2());
        }
    }

}
