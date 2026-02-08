package util;

import java.time.Instant;

public final class TimeProvider {
    private TimeProvider() {}

    public static Instant now() {
        return Instant.now();
    }
}
