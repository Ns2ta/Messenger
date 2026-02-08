package domain.message;

import java.time.Instant;

public class VoiceLinkMessage extends Message {

    private final String title;
    private final String url;

    public VoiceLinkMessage(long id,
                            long chatId,
                            long senderId,
                            Instant timestamp,
                            String title,
                            String url) {
        super(id, chatId, senderId, timestamp);
        this.title = title;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String preview() {
        return "[VOICE] " + title + " | " + url;
    }
}
