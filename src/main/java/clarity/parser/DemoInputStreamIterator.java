package clarity.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;

import clarity.decoder.StringTableDecoder;
import clarity.model.UserMessageType;
import clarity.parser.messages.CCLAMsg_CreateStringTable;
import clarity.parser.messages.CCLAMsg_UpdateStringTable;

import com.dota2.proto.Demo.CDemoClassInfo;
import com.dota2.proto.Demo.CDemoFileHeader;
import com.dota2.proto.Demo.CDemoFileInfo;
import com.dota2.proto.Demo.CDemoFullPacket;
import com.dota2.proto.Demo.CDemoPacket;
import com.dota2.proto.Demo.CDemoSendTables;
import com.dota2.proto.Demo.CDemoStop;
import com.dota2.proto.Demo.CDemoStringTables;
import com.dota2.proto.Demo.CDemoStringTables.items_t;
import com.dota2.proto.Demo.CDemoStringTables.table_t;
import com.dota2.proto.Demo.CDemoSyncTick;
import com.dota2.proto.Demo.CDemoUserCmd;
import com.dota2.proto.Demo.EDemoCommands;
import com.dota2.proto.DotaModifiers.CDOTAModifierBuffTableEntry;
import com.dota2.proto.Netmessages.CNETMsg_Tick;
import com.dota2.proto.Netmessages.CSVCMsg_CreateStringTable;
import com.dota2.proto.Netmessages.CSVCMsg_GameEventList;
import com.dota2.proto.Netmessages.CSVCMsg_PacketEntities;
import com.dota2.proto.Netmessages.CSVCMsg_SendTable;
import com.dota2.proto.Netmessages.CSVCMsg_ServerInfo;
import com.dota2.proto.Netmessages.CSVCMsg_TempEntities;
import com.dota2.proto.Netmessages.CSVCMsg_UpdateStringTable;
import com.dota2.proto.Netmessages.NET_Messages;
import com.dota2.proto.Netmessages.SVC_Messages;
import com.dota2.proto.Networkbasetypes.CSVCMsg_GameEvent;
import com.dota2.proto.Networkbasetypes.CSVCMsg_UserMessage;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import de.matthiasmann.coroutines.CoIterator;
import de.matthiasmann.coroutines.SuspendExecution;

public class DemoInputStreamIterator extends CoIterator<Peek> {

    private static final long serialVersionUID = 2620755470688322730L;

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final class StringTableInfo {
        private final String name;
        private final int maxEntries;
        private final boolean userDataFixedSize;
        private final int userDataSize;
        private final int userDataSizeBits;
        public StringTableInfo(String name, int maxEntries, boolean userDataFixedSize, int userDataSize, int userDataSizeBits) {
            this.name = name;
            this.maxEntries = maxEntries;
            this.userDataFixedSize = userDataFixedSize;
            this.userDataSize = userDataSize;
            this.userDataSizeBits = userDataSizeBits;
        }
    }

    private final List<StringTableInfo> stringTableInfo = new ArrayList<StringTableInfo>();
    
    private final CodedInputStream s; // main stream
    private int n = -1;
    private int tick = 0;
    private int peekTick = 0;
    private boolean full = false;

    public DemoInputStreamIterator(InputStream s) {
        this(CodedInputStream.newInstance(s));
    }

    public DemoInputStreamIterator(CodedInputStream s) {
        this.s = s;
    }
    
    private void producePeek(Object message) throws SuspendExecution {
        produce(new Peek(++n, tick, peekTick, full, message));
    }
    
    @Override
    protected void run() throws SuspendExecution {
        try {
            processTop();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processTop() throws IOException, SuspendExecution {
        while (!s.isAtEnd()) {
            int kind = s.readRawVarint32();
            boolean isCompressed = (kind & EDemoCommands.DEM_IsCompressed_VALUE) != 0;
            kind &= ~EDemoCommands.DEM_IsCompressed_VALUE;
            peekTick = s.readRawVarint32();
            int size = s.readRawVarint32();
            byte[] data = s.readRawBytes(size);
            if (isCompressed) {
                data = Snappy.uncompress(data);
            }
            GeneratedMessage message = parseTopLevel(kind, data);
            if (message == null) {
                log.warn("unknown top level message of kind {}", kind);
                continue;
            }
            if (message instanceof CDemoPacket) {
                processEmbed(((CDemoPacket) message).getData().toByteArray());
            } else if (message instanceof CDemoSendTables) {
                processEmbed(((CDemoSendTables) message).getData().toByteArray());
            } else if (message instanceof CDemoFullPacket) {
                full = true;
                CDemoFullPacket fullMessage = (CDemoFullPacket)message;
                processUncompressedStringTable(fullMessage.getStringTable());
                processEmbed(fullMessage.getPacket().getData().toByteArray());
                full = false;
            } else if (message instanceof CDemoStringTables) {
                processUncompressedStringTable((CDemoStringTables) message);
            } else {
                producePeek(message);
            }
        }
    }
    
    private void processEmbed(byte[] data) throws IOException, SuspendExecution {
        CodedInputStream ss = CodedInputStream.newInstance(data);
        while (!ss.isAtEnd()) {
            int subKind = ss.readRawVarint32();
            int subSize = ss.readRawVarint32();
            byte[] subData = ss.readRawBytes(subSize);
            GeneratedMessage subMessage = parseEmbedded(subKind, subData);
            if (subMessage == null) {
                //log.warn("unknown embedded message of kind {}", subKind);
                continue;
            }
            if (subMessage instanceof CNETMsg_Tick) {
                tick = ((CNETMsg_Tick) subMessage).getTick();
            } else if (subMessage instanceof CSVCMsg_CreateStringTable) {
                processCreateStringTable((CSVCMsg_CreateStringTable) subMessage);
            } else if (subMessage instanceof CSVCMsg_UpdateStringTable) {
                processUpdateStringTable((CSVCMsg_UpdateStringTable) subMessage);
            } else if (subMessage instanceof CSVCMsg_UserMessage) {
                processUserMessage((CSVCMsg_UserMessage) subMessage);
            } else {
                producePeek(subMessage);
            }
        }
    }
    
    private void processUncompressedStringTable(CDemoStringTables message) throws IOException, SuspendExecution {
        Iterator<table_t> iter = message.getTablesList().iterator();
        while (iter.hasNext()) {
            table_t t = iter.next();
            if ("ActiveModifiers".equals(t.getTableName())) {
                for (items_t i : t.getItemsList()) {
                    GeneratedMessage modMsg = CDOTAModifierBuffTableEntry.parseFrom(i.getData());
                    producePeek(modMsg);
                }
            } else {
                List<Triplet<Integer, String, ByteString>> updates = new LinkedList<Triplet<Integer, String, ByteString>>();
                List<items_t> items = t.getItemsList();
                for (int i = 0; i < items.size(); i++) {
                    updates.add(new Triplet<Integer, String, ByteString>(i, items.get(i).getStr(), items.get(i).getData()));
                }
                producePeek(new CCLAMsg_UpdateStringTable(t.getTableName(), updates));
            }
        }
    }
    
    private void processCreateStringTable(CSVCMsg_CreateStringTable message) throws IOException, SuspendExecution {
        producePeek(new CCLAMsg_CreateStringTable(message.getName(), message.getMaxEntries()));
        stringTableInfo.add(new StringTableInfo(message.getName(), message.getMaxEntries(), message.getUserDataFixedSize(), message.getUserDataSize(), message.getUserDataSizeBits()));
        processCompressedStringTable(stringTableInfo.size() - 1, message.getNumEntries(), message.getStringData().toByteArray());
    }
    
    private void processUpdateStringTable(CSVCMsg_UpdateStringTable message) throws IOException, SuspendExecution {
        processCompressedStringTable(message.getTableId(), message.getNumChangedEntries(), message.getStringData().toByteArray());
    }
    
    private void processCompressedStringTable(int id, int numEntries, byte[] data) throws IOException, SuspendExecution {
        StringTableInfo info = stringTableInfo.get(id);
        StringTableDecoder ssis = new StringTableDecoder(data, numEntries, info.maxEntries, info.userDataFixedSize, info.userDataSizeBits);
        Triplet<Integer, String, ByteString> e = null;
        if ("ActiveModifiers".equals(info.name)) {
            while((e = ssis.read()) != null) {
                if (e.getValue2() != null) {
                    GeneratedMessage modMsg = CDOTAModifierBuffTableEntry.parseFrom(e.getValue2());
                    producePeek(modMsg);
                }
            }
        } else {
            List<Triplet<Integer, String, ByteString>> updates = new LinkedList<Triplet<Integer, String, ByteString>>();
            while((e = ssis.read()) != null) {
                updates.add(e);
            }
            if (updates.size() > 0) {
                producePeek(new CCLAMsg_UpdateStringTable(info.name, updates));
            }
        }
    }
    
    private void processUserMessage(CSVCMsg_UserMessage userMessage) throws IOException, SuspendExecution {
        UserMessageType umt = UserMessageType.forId(userMessage.getMsgType());
        if (umt == null) {
            log.warn("unknown usermessage of kind {}", userMessage.getMsgType());
        } else if (umt.getClazz() == null) {
            log.warn("no protobuf class for usermessage of type {}", umt);
        } else { 
            GeneratedMessage decodedUserMessage = umt.parseFrom(userMessage.getMsgData());
            producePeek(decodedUserMessage);
        }
    }
    
    
    private GeneratedMessage parseTopLevel(int kind, byte[] data) throws InvalidProtocolBufferException {
        switch (EDemoCommands.valueOf(kind)) {
        case DEM_ClassInfo:
            return CDemoClassInfo.parseFrom(data);
            // case DEM_ConsoleCmd:
            // return CDemoConsoleCmd.parseFrom(data);
            // case DEM_CustomData:
            // return CDemoCustomData.parseFrom(data);
            // case DEM_CustomDataCallbacks:
            // return CDemoCustomDataCallbacks.parseFrom(data);
        case DEM_FileHeader:
            return CDemoFileHeader.parseFrom(data);
        case DEM_FileInfo:
            return CDemoFileInfo.parseFrom(data);
        case DEM_FullPacket:
            return CDemoFullPacket.parseFrom(data);
        case DEM_Packet:
            return CDemoPacket.parseFrom(data);
        case DEM_SendTables:
            return CDemoSendTables.parseFrom(data);
        case DEM_SignonPacket:
            return CDemoPacket.parseFrom(data);
        case DEM_StringTables:
            return CDemoStringTables.parseFrom(data);
        case DEM_Stop:
            return CDemoStop.parseFrom(data);
        case DEM_SyncTick:
            return CDemoSyncTick.parseFrom(data);
        case DEM_UserCmd:
            return CDemoUserCmd.parseFrom(data);
        default:
            return null;
        }
    }
    
    private GeneratedMessage parseEmbedded(int kind, byte[] data) throws InvalidProtocolBufferException {
        switch (kind) {
//        case NET_Messages.net_SetConVar_VALUE:
//            return CNETMsg_SetConVar.parseFrom(data);
//        case NET_Messages.net_SignonState_VALUE:
//            return CNETMsg_SignonState.parseFrom(data);
        case NET_Messages.net_Tick_VALUE:
            return CNETMsg_Tick.parseFrom(data);

//        case SVC_Messages.svc_ClassInfo_VALUE:
//            return CSVCMsg_ClassInfo.parseFrom(data);
        case SVC_Messages.svc_CreateStringTable_VALUE:
            return CSVCMsg_CreateStringTable.parseFrom(data);
        case SVC_Messages.svc_GameEvent_VALUE:
            return CSVCMsg_GameEvent.parseFrom(data);
        case SVC_Messages.svc_GameEventList_VALUE:
            return CSVCMsg_GameEventList.parseFrom(data);
//        case SVC_Messages.svc_Menu_VALUE:
//            return CSVCMsg_Menu.parseFrom(data);
        case SVC_Messages.svc_PacketEntities_VALUE:
            return CSVCMsg_PacketEntities.parseFrom(data);
        case SVC_Messages.svc_SendTable_VALUE:
            return CSVCMsg_SendTable.parseFrom(data);
        case SVC_Messages.svc_ServerInfo_VALUE:
            return CSVCMsg_ServerInfo.parseFrom(data);
//        case SVC_Messages.svc_SetView_VALUE:
//            return CSVCMsg_SetView.parseFrom(data);
//        case SVC_Messages.svc_Sounds_VALUE:
//            return CSVCMsg_Sounds.parseFrom(data);
        case SVC_Messages.svc_TempEntities_VALUE:
            return CSVCMsg_TempEntities.parseFrom(data);
        case SVC_Messages.svc_UpdateStringTable_VALUE:
            return CSVCMsg_UpdateStringTable.parseFrom(data);
        case SVC_Messages.svc_UserMessage_VALUE:
            return CSVCMsg_UserMessage.parseFrom(data);
//        case SVC_Messages.svc_VoiceInit_VALUE:
//            return CSVCMsg_VoiceInit.parseFrom(data);
//        case SVC_Messages.svc_VoiceData_VALUE:
//            return CSVCMsg_VoiceData.parseFrom(data);

        default:
            return null;
        }
    }

    

}
