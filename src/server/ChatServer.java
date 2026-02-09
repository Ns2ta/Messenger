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
        String chatTitle = safe(chat.getTitle());
        String senderName = safe(userService.getUser(message.getSenderId()).getUsername());

        String line;

        if (message instanceof domain.message.VoiceLinkMessage vm) {
            line = "🔔 [" + chatTitle + "] " + senderName
                    + " 🎙 Voice: " + safe(vm.getTitle())
                    + " (" + safe(vm.getUrl()) + ")";
        } else if (message instanceof domain.message.MediaLinkMessage mm) {
            line = "🔔 [" + chatTitle + "] " + senderName
                    + " 🎞 Media: " + safe(mm.getTitle())
                    + " (" + safe(mm.getUrl()) + ")";
        } else if (message instanceof domain.message.FileLinkMessage fm) {
            line = "🔔 [" + chatTitle + "] " + senderName
                    + " 📎 File: " + safe(fm.getFileName())
                    + " (" + safe(fm.getUrl()) + ")";
        } else if (message instanceof domain.message.ImageMessage im) {
            line = "🔔 [" + chatTitle + "] " + senderName
                    + " 🖼 Image: " + safe(im.getPathOrName());
        } else if (message instanceof domain.message.TextMessage tm) {
            line = "🔔 [" + chatTitle + "] " + senderName + ": " + safe(tm.getText());
        } else {
            line = "🔔 [" + chatTitle + "] " + senderName + ": " + safe(message.preview());
        }

        for (Long uid : chat.getParticipantIds()) {
            ClientHandler h = online.get(uid);
            if (h != null) h.sendLine(line);
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
