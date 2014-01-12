package clarity.parser.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clarity.match.Match;
import clarity.model.Entity;
import clarity.model.Handle;
import clarity.parser.Handler;
import clarity.parser.HandlerHelper;

import com.dota2.proto.DotaModifiers.CDOTAModifierBuffTableEntry;
import com.dota2.proto.DotaModifiers.DOTA_MODIFIER_ENTRY_TYPE;

public class ModifierBuffTableEntryHandler implements Handler<CDOTAModifierBuffTableEntry> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Override
    public void apply(int peekTick, CDOTAModifierBuffTableEntry message, Match match) {
        int entityIndex = message.getParent() & 0x7FF;
        int modifierIndex = message.getIndex();
        CDOTAModifierBuffTableEntry prev = match.getModifiers().get(entityIndex, modifierIndex);
        Entity parent = match.getEntities().getByHandle(message.getParent());
        Entity caster = match.getEntities().getByHandle(message.getCaster());
        Entity ability = match.getEntities().getByHandle(message.getAbility());
        String mName = "NULL";
        if (message.getEntryType() == DOTA_MODIFIER_ENTRY_TYPE.DOTA_MODIFIER_ENTRY_TYPE_ACTIVE) {
            match.getModifiers().set(entityIndex, modifierIndex, message);
            mName = match.getStringTables().forName("ModifierNames").getNameByIndex(message.getModifierClass());
            log.debug("{} {} [serial={}, handle={}, entityIdx={}, index={}, class={}, parent={}({}), caster={}({}), ability={}({})]",
                match.getReplayTimeAsString(),
                "MODIFIER_ADD",
                message.getSerialNum(),
                Handle.forIndexAndSerial(modifierIndex, message.getSerialNum()),
                entityIndex,
                modifierIndex,
                mName,
                message.getParent(),
                parent == null ? "NOT_FOUND" : parent.getDtClass().getDtName(),
                message.getCaster(),
                caster == null ? "NOT_FOUND" : caster.getDtClass().getDtName(),
                message.getAbility(),
                !message.hasAbility() ? "NONE" : (ability == null ? "NOT_FOUND" : ability.getDtClass().getDtName()) 
            );
        } else {
            if (prev != null) {
                match.getModifiers().remove(entityIndex, modifierIndex);
                mName = match.getStringTables().forName("ModifierNames").getNameByIndex(prev.getModifierClass()); 
            } else {
                mName = "NOT_FOUND";
            }
            log.debug("{} {} [serial={}, handle={}, entityIdx={}, index={}, class={}, parent={}({})]",
                match.getReplayTimeAsString(),
                prev == null ? "MODIFIER_DEL_UNABLE" : "MODIFIER_DEL",
                message.getSerialNum(),
                Handle.forIndexAndSerial(modifierIndex, message.getSerialNum()),
                entityIndex,
                modifierIndex,
                mName,
                message.getParent(),
                parent == null ? "NOT_FOUND" : parent.getDtClass().getDtName()
            );
            if (prev == null && !log.isTraceEnabled()) {
                log.debug("{}", message);
            }
        }
        HandlerHelper.traceMessage(log, match.getTick(), message);;
    }

    
}
