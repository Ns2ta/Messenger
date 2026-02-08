package service;

import domain.Chat;
import domain.User;
import domain.message.ImageMessage;
import domain.message.Message;
import domain.message.TextMessage;
import exception.ChatNotFoundException;
import observer.ChatEventListener;
import repository.ChatRepository;
import util.IdGenerator;
import util.TimeProvider;
import domain.message.VoiceLinkMessage;
import exception.InvalidLinkException;

import java.util.List;

public class ChatService {
    private final ChatRepository chats;
    private final UserService userService;

    public ChatService(ChatRepository chats, UserService userService) {
        this.chats = chats;
        this.userService = userService;
    }

    // CRUD: Create
    public Chat createChat(String title, List<Long> participantIds) {
        Chat chat = new Chat(IdGenerator.nextId(), title);
        for (long uid : participantIds) {
            // проверим, что пользователь существует
            User u = userService.getUser(uid);
            chat.addParticipant(u.getId());
        }
        chats.add(chat);
        return chat;
    }

    // CRUD: Read
    public Chat getChat(long chatId) {
        return chats.findById(chatId).orElseThrow(() -> new ChatNotFoundException(chatId));
    }

    public List<Chat> listChats() {
        return chats.findAll();
    }

    // CRUD: Update
    public void renameChat(long chatId, String newTitle) {
        Chat chat = getChat(chatId);
        chat.setTitle(newTitle);
        chats.update(chat);
    }

    // CRUD: Delete
    public void deleteChat(long chatId) {
        chats.deleteById(chatId);
    }

    // Observer wiring
    public void subscribeToChat(long chatId, ChatEventListener listener) {
        Chat chat = getChat(chatId);
        chat.subscribe(listener);
        chats.update(chat);
    }

    // Messaging operations
    public Message sendText(long chatId, long senderId, String text) {
        Chat chat = getChat(chatId);
        userService.getUser(senderId); // validate

        Message msg = new TextMessage(IdGenerator.nextId(), chatId, senderId, TimeProvider.now(), text);
        chat.addMessage(msg);
        chats.update(chat);
        return msg;
    }

    public Message sendImage(long chatId, long senderId, String pathOrName) {
        Chat chat = getChat(chatId);
        userService.getUser(senderId); // validate

        Message msg = new ImageMessage(IdGenerator.nextId(), chatId, senderId, TimeProvider.now(), pathOrName);
        chat.addMessage(msg);
        chats.update(chat);
        return msg;
    }
    public VoiceLinkMessage sendVoiceLink(long chatId, long senderId, String title, String url) {
        Chat chat = getChat(chatId);
        userService.getUser(senderId);

        if (!isGoogleDriveHttps(url)) {
            throw new InvalidLinkException("Voice link must be Google Drive https link (drive.google.com or docs.google.com)");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new InvalidLinkException("Title must not be empty");
        }

        VoiceLinkMessage msg = new VoiceLinkMessage(
                IdGenerator.nextId(), chatId, senderId, TimeProvider.now(),
                title.trim(), url.trim()
        );
        chat.addMessage(msg);
        chats.update(chat);
        return msg;
    }

    private boolean isGoogleDriveHttps(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (!u.startsWith("https://")) return false;
        // Google Drive share links commonly use these hosts
        return u.contains("://drive.google.com/") || u.contains("://docs.google.com/");
    }

    public List<Message> getHistory(long chatId) {
        return getChat(chatId).getMessages();
    }

    public void markAllDelivered(long chatId) {
        Chat chat = getChat(chatId);
        for (Message m : chat.getMessages()) {
            m.setStatus(Message.Status.DELIVERED);
        }
        chats.update(chat);
    }
}
