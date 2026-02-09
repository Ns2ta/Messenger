package domain.message;

import java.time.Instant;

public class TextMessage extends Message {
    private final String text;

    public TextMessage(long id, long chatId, long senderId, Instant timestamp, String text) {
        super(id, chatId, senderId, timestamp);
        this.text = text;
    }

    public String getText() { return text; }

    @Override
    public String preview() {
        return text;
    }
}
