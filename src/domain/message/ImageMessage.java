package domain.message;

import java.time.Instant;

public class ImageMessage extends Message {
    private String pathOrName;

    public ImageMessage(long id, long chatId, long senderId, Instant timestamp, String pathOrName) {
        super(id, chatId, senderId, timestamp);
        this.pathOrName = pathOrName;
    }

    public String getPathOrName() { return pathOrName; }
    public void setPathOrName(String pathOrName) { this.pathOrName = pathOrName; }

    @Override
    public String preview() {
        return "[image] " + pathOrName;
    }
}
