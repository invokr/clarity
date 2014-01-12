package clarity.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import clarity.match.Match;

public class Peek {

    private static final Logger log = LoggerFactory.getLogger(Peek.class);
    
    private final int id;
    private int tick;
    private final int peekTick;
    private final boolean full;
    private final Object message;

    public Peek(int id, int tick, int peekTick, boolean full, Object message) {
        this.id = id;
        this.tick = tick;
        this.peekTick = peekTick;
        this.full = full;
        this.message = message;
    }
    
    public int getId() {
        return id;
    }

    public int getTick() {
        return tick;
    }

    public int getPeekTick() {
        return peekTick;
    }
    
    public boolean isFull() {
        return full;
    }
    
    public Object getMessage() {
        return message;
    }

    public void applySkew(int skew) {
        tick -= skew;
    }
    
    public void apply(Match match) {
        match.setTick(tick);
        trace();
        HandlerRegistry.apply(tick, message, match);
    }
    
    public void trace() {
        log.trace("id: {}, peekTick: {}, tick: {}, full: {}, messageType: {}", id, peekTick, tick, full, message.getClass().getSimpleName());
    }

}
