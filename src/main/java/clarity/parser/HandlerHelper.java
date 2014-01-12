package clarity.parser;

import org.slf4j.Logger;

public class HandlerHelper {

    public static void traceMessage(Logger log, int peekTick, Object message) {
        log.trace("peek: {} {}\n{}", peekTick, message.getClass().getSimpleName(), message);
    }
    
}
