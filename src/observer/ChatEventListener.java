package observer;

import domain.Chat;
import domain.message.Message;

public interface ChatEventListener {
    void onNewMessage(Chat chat, Message message);
}
