package domain;

import domain.message.Message;
import observer.ChatEventListener;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class Chat {
    private final long id;
    private String title;
    private final List<Long> participantIds = new ArrayList<>();
    private final List<Message> messages = new ArrayList<>();

    private final List<ChatEventListener> listeners = new ArrayList<>();

    public Chat(long id, String title) {
        this.id = id;
        this.title = title;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public List<Long> getParticipantIds() {
        return Collections.unmodifiableList(participantIds);
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void setTitle(String title) { this.title = title; }

    public void addParticipant(long userId) {
        if (!participantIds.contains(userId)) participantIds.add(userId);
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyNewMessage(message);
    }

    public void subscribe(ChatEventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(ChatEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyNewMessage(Message message) {
        for (ChatEventListener l : listeners) {
            l.onNewMessage(this, message);
        }
    }
}
