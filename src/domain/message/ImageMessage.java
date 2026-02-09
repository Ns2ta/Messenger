package domain.message;

import java.time.Instant;

public class ImageMessage extends Message {
    private final String pathOrName;

    public ImageMessage(long id, long chatId, long senderId, Instant timestamp, String pathOrName) {
        super(id, chatId, senderId, timestamp);
        this.pathOrName = pathOrName;
    }

    public String getPathOrName() { return pathOrName; }

    @Override
    public String preview() {
        return "[image] " + pathOrName;
    }
}
