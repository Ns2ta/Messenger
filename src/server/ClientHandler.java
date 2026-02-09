package server;

import domain.Chat;
import domain.User;
import domain.message.Message;
import domain.message.VoiceLinkMessage;
import domain.message.ImageMessage;
import domain.message.TextMessage;
import domain.message.MediaLinkMessage;
import domain.message.FileLinkMessage;
import net.Protocol;
import service.ChatService;
import service.UserService;


import java.io.*;
import java.net.Socket;
import java.util.*;

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
                        case Protocol.SEND_MEDIA_LINK -> handleSendMediaLink(args);
                        case Protocol.SEND_FILE_LINK -> handleSendFileLink(args);
                        case Protocol.HISTORY -> handleHistory(args);

                        default -> sendLine(Protocol.ERROR + " Unknown command. Type HELP");
                    }
                } catch (Exception ex) {
                    sendLine(Protocol.ERROR + " " + ex.getMessage());
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (currentUserId != null) {
                server.unregisterOnline(currentUserId);
            }
        }
    }

    public void sendLine(String line) {
        if (out != null) out.println(line);
    }

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
        currentUserId = u.getId();
        server.registerOnline(currentUserId, this);
        server.ensureSubscribedForUser(currentUserId);

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
        server.ensureSubscribedForUser(currentUserId);

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
        participants.add(currentUserId);

        if (!ids.isBlank()) {
            for (String s : ids.split(",")) {
                String t = s.trim();
                if (t.isEmpty()) continue;
                participants.add(Long.parseLong(t));
            }
        }

        List<Long> participantList = new ArrayList<>(participants);
        for (Long uid : participantList) userService.getUser(uid);

        Chat chat = chatService.createChat(title, participantList);

        server.ensureChatSubscribed(chat.getId());

        sendLine(Protocol.OK + " CHAT_CREATED chatId=" + chat.getId()
                + " title=" + chat.getTitle()
                + " participants=" + participantList);
    }

    private void handleChats() {
        requireLogin();
        List<Chat> chats = chatService.listChats();

        List<Chat> mine = new ArrayList<>();
        for (Chat c : chats) {
            if (c.getParticipantIds().contains(currentUserId)) {
                mine.add(c);
            }
        }

        sendLine(Protocol.OK + " CHATS count=" + mine.size());
        for (Chat c : mine) {
            String participantsNames = c.getParticipantIds().stream()
                    .map(id -> userService.getUser(id).getUsername())
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            sendLine("  chatId=" + c.getId()
                    + "|title=" + c.getTitle()
                    + "|participants=" + participantsNames);
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

        Chat chat = chatService.getChat(chatId);
        if (!chat.getParticipantIds().contains(currentUserId)) {
            sendLine(Protocol.ERROR + " You are not a participant of chatId=" + chatId);
            return;
        }

        server.ensureChatSubscribed(chatId);
        Message msg = chatService.sendText(chatId, currentUserId, text);
        sendLine(Protocol.OK + " SENT messageId=" + msg.getId() + " status=" + msg.getStatus());
    }

    private void handleSendVoiceLink(String args) {
        requireLogin();
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
        server.ensureChatSubscribed(chatId);
        var msg = chatService.sendVoiceLink(chatId, currentUserId, title, url);
        sendLine(Protocol.OK + " SENT messageId=" + msg.getId() + " kind=VOICE");
    }

    private void handleSendMediaLink(String args) {
        requireLogin();
        if (!args.contains("|")) {
            sendLine(Protocol.ERROR + " Usage: SEND_MEDIA_LINK <chatId> <title> | <url>");
            return;
        }
        String[] lr = args.split("\\|", 2);
        String left = lr[0].trim();
        String url = lr[1].trim();

        String[] lp = left.split("\\s+", 2);
        if (lp.length < 2) { sendLine(Protocol.ERROR + " Usage: SEND_MEDIA_LINK <chatId> <title> | <url>"); return; }

        long chatId = Long.parseLong(lp[0]);
        String title = lp[1].trim();

        Chat chat = chatService.getChat(chatId);
        if (!chat.getParticipantIds().contains(currentUserId)) {
            sendLine(Protocol.ERROR + " You are not a participant of chatId=" + chatId);
            return;
        }
        server.ensureChatSubscribed(chatId);
        chatService.sendMediaLink(chatId, currentUserId, title, url);
        sendLine(Protocol.OK + " SENT kind=MEDIA");
    }

    private void handleSendFileLink(String args) {
        requireLogin();
        if (!args.contains("|")) {
            sendLine(Protocol.ERROR + " Usage: SEND_FILE_LINK <chatId> <fileName> | <url>");
            return;
        }
        String[] lr = args.split("\\|", 2);
        String left = lr[0].trim();
        String url = lr[1].trim();

        String[] lp = left.split("\\s+", 2);
        if (lp.length < 2) { sendLine(Protocol.ERROR + " Usage: SEND_FILE_LINK <chatId> <fileName> | <url>"); return; }

        long chatId = Long.parseLong(lp[0]);
        String fileName = lp[1].trim();

        Chat chat = chatService.getChat(chatId);
        if (!chat.getParticipantIds().contains(currentUserId)) {
            sendLine(Protocol.ERROR + " You are not a participant of chatId=" + chatId);
            return;
        }

        server.ensureChatSubscribed(chatId);
        chatService.sendFileLink(chatId, currentUserId, fileName, url);
        sendLine(Protocol.OK + " SENT kind=FILE");
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
            String senderName = userService.getUser(m.getSenderId()).getUsername();
            String ts = String.valueOf(m.getTimestamp());

            if (m instanceof VoiceLinkMessage vm) {
                sendLine("[" + ts + "] " + escape(senderName)
                        + " 🎙 Voice: " + escape(vm.getTitle())
                        + " (" + escape(vm.getUrl()) + ")");
            } else if (m instanceof domain.message.MediaLinkMessage mm) {
                sendLine("[" + ts + "] " + escape(senderName)
                        + " 🎞 Media: " + escape(mm.getTitle())
                        + " (" + escape(mm.getUrl()) + ")");
            } else if (m instanceof domain.message.FileLinkMessage fm) {
                sendLine("[" + ts + "] " + escape(senderName)
                        + " 📎 File: " + escape(fm.getFileName())
                        + " (" + escape(fm.getUrl()) + ")");
            } else if (m instanceof domain.message.ImageMessage im) {
                sendLine("[" + ts + "] " + escape(senderName)
                        + " 🖼 Image: " + escape(im.getPathOrName()));
            } else if (m instanceof domain.message.TextMessage tm) {
                sendLine("[" + ts + "] " + escape(senderName) + ": " + escape(tm.getText()));
            } else {
                sendLine("[" + ts + "] " + escape(senderName) + ": " + escape(m.preview()));
            }
        }
    }

    private String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }

    private String firstToken(String line) {
        int idx = line.indexOf(' ');
        return (idx < 0) ? line : line.substring(0, idx);
    }

    private String rest(String line) {
        int idx = line.indexOf(' ');
        return (idx < 0) ? "" : line.substring(idx + 1);
    }
}
