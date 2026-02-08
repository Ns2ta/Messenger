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
    public void ensureChatSubscribed(long chatId) {
        chatService.subscribeToChat(chatId, this);
    }

    // Observer event: chat got a new message -> broadcast to online participants
    @Override
    public void onNewMessage(Chat chat, Message message) {
        String line;

        if (message instanceof domain.message.VoiceLinkMessage vm) {
            line = Protocol.EVENT + " NEW_VOICE"
                    + " chatId=" + chat.getId()
                    + " senderId=" + vm.getSenderId()
                    + " title=" + safe(vm.getTitle())
                    + " url=" + safe(vm.getUrl());
        } else {
            line = Protocol.EVENT + " NEW_TEXT"
                    + " chatId=" + chat.getId()
                    + " senderId=" + message.getSenderId()
                    + " text=" + safe(message.preview());
        }

        for (Long uid : chat.getParticipantIds()) {
            ClientHandler h = online.get(uid);
            if (h != null) {
                h.sendLine(line);
            }
        }
    }

    private String safe(String s) {
        // We keep protocol line-based; replace newlines so they don't break reading.
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }

    // Helper for server-side formatting if needed
    public String formatUser(User u) {
        return "id=" + u.getId() + "|name=" + u.getUsername() + "|online=" + isOnline(u.getId());
    }
}
