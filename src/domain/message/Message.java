package domain.message;

import java.time.Instant;

public abstract class Message {
    public enum Status { SENT, DELIVERED, READ }

    private final long id;
    private final long chatId;
    private final long senderId;
    private final Instant timestamp;
    private Status status;


    protected Message(long id, long chatId, long senderId, Instant timestamp) {
        this.id = id;
        this.chatId = chatId;
        this.senderId = senderId;
        this.timestamp = timestamp;
        this.status = Status.SENT;
    }

    public long getId() { return id; }
    public long getChatId() { return chatId; }
    public long getSenderId() { return senderId; }
    public Instant getTimestamp() { return timestamp; }
    public Status getStatus() { return status; }

    public void setStatus(Status status) { this.status = status; }

    public abstract String preview();
}
