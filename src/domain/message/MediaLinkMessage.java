package domain.message;

import java.time.Instant;

public class MediaLinkMessage extends Message {
    private final String title;
    private final String url;

    public MediaLinkMessage(long id, long chatId, long senderId, Instant timestamp, String title, String url) {
        super(id, chatId, senderId, timestamp);
        this.title = title;
        this.url = url;
    }

    public String getTitle() { return title; }
    public String getUrl() { return url; }

    @Override
    public String preview() {
        return "[MEDIA] " + title + " | " + url;
    }
}
