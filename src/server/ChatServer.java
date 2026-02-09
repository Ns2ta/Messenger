package server;

import domain.Chat;
import domain.User;
import domain.message.Message;
import net.Protocol;
import observer.ChatEventListener;
import repository.inmemory.InMemoryChatRepository;
import repository.inmemory.InMemoryUserRepository;
import service.ChatService;
import service.UserService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Set;
import domain.Chat;

/**
 * Single JVM server:
 * - keeps all repositories/services in memory (per project requirements) :contentReference[oaicite:1]{index=1}
 * - accepts multiple clients (each in own console)
 * - broadcasts new messages to online participants (push events)
 */
public class ChatServer implements ChatEventListener {
    private final int port;

    private final UserService userService;
    private final ChatService chatService;

    // online sessions: userId -> handler
    private final Map<Long, ClientHandler> online = new ConcurrentHashMap<>();

    public ChatServer(int port) {
        this.port = port;

        // In-memory storage lives here (SERVER process)
        this.userService = new UserService(new InMemoryUserRepository());
        this.chatService = new ChatService(new InMemoryChatRepository(), userService);
    }

    public void start() throws IOException {
        System.out.println("=== ChatServer started on port " + port + " ===");
        System.out.println("Clients can connect to localhost:" + port);

        try (ServerSocket ss = new ServerSocket(port)) {
            while (true) {
                Socket socket = ss.accept();
                ClientHandler handler = new ClientHandler(socket, this, userService, chatService);
                Thread t = new Thread(handler, "client-" + socket.getRemoteSocketAddress());
                t.start();
            }
        }
    }

    // Called by ClientHandler after successful login
    public void registerOnline(long userId, ClientHandler handler) {
        online.put(userId, handler);
    }

    public void unregisterOnline(long userId) {
        online.remove(userId);
    }

    public boolean isOnline(long userId) {
        return online.containsKey(userId);
    }

    // Subscribe server to chat events so it can broadcast NEW_MESSAGE
    private final Set<Long> subscribedChats = new HashSet<>();

    public void ensureChatSubscribed(long chatId) {
        if (subscribedChats.contains(chatId)) return;

        Chat chat = chatService.getChat(chatId);
        chat.subscribe(this); // Observer: сервер подписывается на чат
        subscribedChats.add(chatId);
    }
    public void ensureSubscribedForUser(long userId) {
        for (Chat c : chatService.listChats()) {
            if (c.getParticipantIds().contains(userId)) {
                ensureChatSubscribed(c.getId());
            }
        }
    }

    // Observer event: chat got a new message -> broadcast to online participants
    @Override
    public void onNewMessage(Chat chat, Message message) {
        // IMPORTANT:
        // Push notifications MUST start with "EVENT".
        // Otherwise the client will treat them as normal command responses,
        // they will land in the responses queue and will break parsing
        // (exactly the crash you saw in HISTORY).

        long chatId = chat.getId();
        long senderId = message.getSenderId();
        String senderName = safe(userService.getUser(senderId).getUsername());

        String eventLine;

        if (message instanceof domain.message.TextMessage tm) {
            eventLine = Protocol.EVENT + " NEW_TEXT"
                    + " chatId=" + chatId
                    + " senderId=" + senderId
                    + " sender=" + senderName
                    + " text=" + safe(tm.getText());
        } else if (message instanceof domain.message.VoiceLinkMessage vm) {
            eventLine = Protocol.EVENT + " NEW_VOICE"
                    + " chatId=" + chatId
                    + " senderId=" + senderId
                    + " sender=" + senderName
                    + " title=" + safe(vm.getTitle())
                    + " url=" + safe(vm.getUrl());
        } else if (message instanceof domain.message.MediaLinkMessage mm) {
            eventLine = Protocol.EVENT + " NEW_MEDIA"
                    + " chatId=" + chatId
                    + " senderId=" + senderId
                    + " sender=" + senderName
                    + " title=" + safe(mm.getTitle())
                    + " url=" + safe(mm.getUrl());
        } else if (message instanceof domain.message.FileLinkMessage fm) {
            eventLine = Protocol.EVENT + " NEW_FILE"
                    + " chatId=" + chatId
                    + " senderId=" + senderId
                    + " sender=" + senderName
                    + " name=" + safe(fm.getFileName())
                    + " url=" + safe(fm.getUrl());
        } else if (message instanceof domain.message.ImageMessage im) {
            eventLine = Protocol.EVENT + " NEW_IMAGE"
                    + " chatId=" + chatId
                    + " senderId=" + senderId
                    + " sender=" + senderName
                    + " file=" + safe(im.getPathOrName());
        } else {
            eventLine = Protocol.EVENT + " NEW_MESSAGE"
                    + " chatId=" + chatId
                    + " senderId=" + senderId
                    + " sender=" + senderName
                    + " text=" + safe(message.preview());
        }

        for (Long uid : chat.getParticipantIds()) {
            ClientHandler h = online.get(uid);
            if (h != null) h.sendLine(eventLine);
        }
    }


    private String safe(String s) {
        if (s == null) return "";
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }

    // Helper for server-side formatting if needed
    public String formatUser(User u) {
        return "id=" + u.getId() + "|name=" + u.getUsername() + "|online=" + isOnline(u.getId());
    }
}
