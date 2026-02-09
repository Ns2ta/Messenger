package domain.message;

import java.time.Instant;

public class FileLinkMessage extends Message {
    private final String fileName;
    private final String url;

    public FileLinkMessage(long id, long chatId, long senderId, Instant timestamp, String fileName, String url) {
        super(id, chatId, senderId, timestamp);
        this.fileName = fileName;
        this.url = url;
    }

    public String getFileName() { return fileName; }
    public String getUrl() { return url; }

    @Override
    public String preview() {
        return "[FILE] " + fileName + " | " + url;
    }
}
