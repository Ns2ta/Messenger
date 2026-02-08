package server;

import domain.Chat;
import domain.User;
import domain.message.Message;
import domain.message.VoiceLinkMessage;
import net.Protocol;
import service.ChatService;
import service.UserService;


import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

/**
 * One handler per client connection.
 * Reads commands and executes via services.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private final UserService userService;
    private final ChatService chatService;

    private BufferedReader in;
    private PrintWriter out;

    private Long currentUserId = null;

    public ClientHandler(Socket socket, ChatServer server, UserService userService, ChatService chatService) {
        this.socket = socket;
        this.server = server;
        this.userService = userService;
        this.chatService = chatService;
    }

    @Override
    public void run() {
        try (socket) {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            sendLine(Protocol.OK + " Connected. Type HELP for commands.");

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String cmd = firstToken(line).toUpperCase();
                String args = rest(line);

                try {
                    switch (cmd) {
                        case Protocol.HELP -> handleHelp();
                        case Protocol.EXIT -> { handleExit(); return; }

                        case Protocol.REGISTER -> handleRegister(args);
                        case Protocol.LOGIN -> handleLogin(args);

                        case Protocol.USERS -> handleUsers();
                        case Protocol.CREATE_CHAT -> handleCreateChat(args);
                        case Protocol.CHATS -> handleChats();

                        case Protocol.SEND_TEXT -> handleSendText(args);
                        case Protocol.SEND_VOICE_LINK -> handleSendVoiceLink(args);
                        case Protocol.HISTORY -> handleHistory(args);

                        default -> sendLine(Protocol.ERROR + " Unknown command. Type HELP");
                    }
                } catch (Exception ex) {
                    sendLine(Protocol.ERROR + " " + ex.getMessage());
                }
            }
        } catch (IOException ignored) {
            // client disconnected abruptly
        } finally {
            if (currentUserId != null) {
                server.unregisterOnline(currentUserId);
            }
        }
    }

    public void sendLine(String line) {
        if (out != null) out.println(line);
    }

    // ---------------- handlers ----------------

    private void handleHelp() {
        sendLine(Protocol.OK + " Commands:");
        sendLine("  REGISTER <username>");
        sendLine("  LOGIN <userId>");
        sendLine("  USERS");
        sendLine("  CREATE_CHAT <title> | <id1,id2,...>");
        sendLine("  CHATS");
        sendLine("  SEND_TEXT <chatId> <text...>");
        sendLine("  HISTORY <chatId>");
        sendLine("  EXIT");
    }

    private void handleExit() {
        sendLine(Protocol.OK + " Bye.");
    }

    private void handleRegister(String args) {
        if (args.isBlank()) {
            sendLine(Protocol.ERROR + " Usage: REGISTER <username>");
            return;
        }
        User u = userService.createUser(args.trim());
        // auto login
        currentUserId = u.getId();
        server.registerOnline(currentUserId, this);

        sendLine(Protocol.OK + " REGISTERED userId=" + u.getId() + " username=" + u.getUsername());
    }

    private void handleLogin(String args) {
        if (args.isBlank()) {
            sendLine(Protocol.ERROR + " Usage: LOGIN <userId>");
            return;
        }
        long id = Long.parseLong(args.trim());
        User u = userService.getUser(id);

        currentUserId = u.getId();
        server.registerOnline(currentUserId, this);

        sendLine(Protocol.OK + " LOGGED_IN userId=" + u.getId() + " username=" + u.getUsername());
    }

    private void requireLogin() {
        if (currentUserId == null) throw new IllegalStateException("Login first: REGISTER or LOGIN");
    }

    private void handleUsers() {
        List<User> users = userService.listUsers();
        if (users.isEmpty()) {
            sendLine(Protocol.OK + " USERS (empty)");
            return;
        }
        sendLine(Protocol.OK + " USERS count=" + users.size());
        for (User u : users) {
            sendLine("  " + server.formatUser(u));
        }
    }

    /**
     * CREATE_CHAT <title> | <id1,id2,...>
     * Example: CREATE_CHAT My group | 1,2,3
     */
    private void handleCreateChat(String args) {
        requireLogin();
        if (!args.contains("|")) {
            sendLine(Protocol.ERROR + " Usage: CREATE_CHAT <title> | <id1,id2,...>");
            return;
        }

        String[] parts = args.split("\\|", 2);
        String title = parts[0].trim();
        String ids = parts[1].trim();

        if (title.isBlank()) {
            sendLine(Protocol.ERROR + " Title is empty");
            return;
        }

        Set<Long> participants = new LinkedHashSet<>();
        participants.add(currentUserId); // always include creator

        if (!ids.isBlank()) {
            for (String s : ids.split(",")) {
                String t = s.trim();
                if (t.isEmpty()) continue;
                participants.add(Long.parseLong(t));
            }
        }

        // validate users exist
        List<Long> participantList = new ArrayList<>(participants);
        for (Long uid : participantList) userService.getUser(uid);

        Chat chat = chatService.createChat(title, participantList);

        // server subscribes to chat events once, so NEW_MESSAGE broadcasts happen
        server.ensureChatSubscribed(chat.getId());

        sendLine(Protocol.OK + " CHAT_CREATED chatId=" + chat.getId()
                + " title=" + chat.getTitle()
                + " participants=" + participantList);
    }

    private void handleChats() {
        requireLogin();
        List<Chat> chats = chatService.listChats();

        // фильтруем только те чаты, где состоит текущий пользователь
        List<Chat> mine = new ArrayList<>();
        for (Chat c : chats) {
            if (c.getParticipantIds().contains(currentUserId)) {
                mine.add(c);
            }
        }

        sendLine(Protocol.OK + " CHATS count=" + mine.size());
        for (Chat c : mine) {
            sendLine("  chatId=" + c.getId() + "|title=" + c.getTitle()
                    + "|participants=" + c.getParticipantIds());
        }
    }


    private void handleSendText(String args) {
        requireLogin();
        String[] p = args.split("\\s+", 2);
        if (p.length < 2) {
            sendLine(Protocol.ERROR + " Usage: SEND_TEXT <chatId> <text...>");
            return;
        }
        long chatId = Long.parseLong(p[0]);
        String text = p[1];

        // must be participant
        Chat chat = chatService.getChat(chatId);
        if (!chat.getParticipantIds().contains(currentUserId)) {
            sendLine(Protocol.ERROR + " You are not a participant of chatId=" + chatId);
            return;
        }

        Message msg = chatService.sendText(chatId, currentUserId, text);
        sendLine(Protocol.OK + " SENT messageId=" + msg.getId() + " status=" + msg.getStatus());
        // Broadcasting happens via server's ChatEventListener onNewMessage()
    }

    private void handleSendVoiceLink(String args) {
        requireLogin();
        // Формат: SEND_VOICE_LINK <chatId> <title> | <url>
        // Пример: SEND_VOICE_LINK 3 Голосовое #1 | https://drive.google.com/file/d/...
        if (!args.contains("|")) {
            sendLine(Protocol.ERROR + " Usage: SEND_VOICE_LINK <chatId> <title> | <url>");
            return;
        }

        String[] leftRight = args.split("\\|", 2);
        String left = leftRight[0].trim();
        String url = leftRight[1].trim();

        String[] leftParts = left.split("\\s+", 2);
        if (leftParts.length < 2) {
            sendLine(Protocol.ERROR + " Usage: SEND_VOICE_LINK <chatId> <title> | <url>");
            return;
        }

        long chatId = Long.parseLong(leftParts[0]);
        String title = leftParts[1].trim();

        Chat chat = chatService.getChat(chatId);
        if (!chat.getParticipantIds().contains(currentUserId)) {
            sendLine(Protocol.ERROR + " You are not a participant of chatId=" + chatId);
            return;
        }

        var msg = chatService.sendVoiceLink(chatId, currentUserId, title, url);
        sendLine(Protocol.OK + " SENT messageId=" + msg.getId() + " kind=VOICE");
    }

    private void handleHistory(String args) {
        requireLogin();
        if (args.isBlank()) {
            sendLine(Protocol.ERROR + " Usage: HISTORY <chatId>");
            return;
        }
        long chatId = Long.parseLong(args.trim());

        Chat chat = chatService.getChat(chatId);
        if (!chat.getParticipantIds().contains(currentUserId)) {
            sendLine(Protocol.ERROR + " You are not a participant of chatId=" + chatId);
            return;
        }

        List<Message> history = chatService.getHistory(chatId);
        sendLine(Protocol.OK + " HISTORY chat=" + chat.getTitle() + " count=" + history.size());

        for (Message m : history) {
            String kind = kindOf(m);

            if ("VOICE".equals(kind) && m instanceof VoiceLinkMessage vm) {
                sendLine("  kind=VOICE"
                        + "|ts=" + vm.getTimestamp()
                        + "|sender=" + userService.getUser(vm.getSenderId()).getUsername()
                        + "|status=" + vm.getStatus()
                        + "|title=" + escape(vm.getTitle())
                        + "|url=" + escape(vm.getUrl()));
            } else {
                sendLine("  kind=TEXT"
                        + "|ts=" + m.getTimestamp()
                        + "|sender=" + userService.getUser(m.getSenderId()).getUsername()
                        + "|status=" + m.getStatus()
                        + "|text=" + escape(m.preview()));
            }
        }
    }

    private String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }

    private String kindOf(Message m) {
        return (m instanceof VoiceLinkMessage) ? "VOICE" : "TEXT";
    }

    // ---------------- parsing helpers ----------------

    private String firstToken(String line) {
        int idx = line.indexOf(' ');
        return (idx < 0) ? line : line.substring(0, idx);
    }

    private String rest(String line) {
        int idx = line.indexOf(' ');
        return (idx < 0) ? "" : line.substring(idx + 1);
    }
}
