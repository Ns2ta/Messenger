package util;

import java.util.concurrent.atomic.AtomicLong;

public final class IdGenerator {
    private static final AtomicLong SEQ = new AtomicLong(1);

    private IdGenerator() {}

    public static long nextId() {
        return SEQ.getAndIncrement();
    }
}
