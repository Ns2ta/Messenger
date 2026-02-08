package observer;

import domain.Chat;
import domain.message.Message;

public class ConsoleNotificationListener implements ChatEventListener {
    @Override
    public void onNewMessage(Chat chat, Message message) {
        System.out.println("[NOTIFY] Chat #" + chat.getId() + " (" + chat.getTitle() + "): " + message.preview());
    }
}
