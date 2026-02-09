package app;

import domain.Chat;
import domain.User;
import observer.ConsoleNotificationListener;
import service.ChatService;
import service.UserService;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class ConsoleApp {
    private final UserService userService;
    private final ChatService chatService;
    private final Scanner scanner;

    private User currentUser;

    public ConsoleApp(UserService userService, ChatService chatService, Scanner scanner) {
        this.userService = userService;
        this.chatService = chatService;
        this.scanner = scanner;
    }

    public void run() {
        printBanner();
        printHelp();

        while (true) {
            System.out.print(prompt());
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            String args = (parts.length > 1) ? parts[1] : "";

            try {
                switch (cmd) {
                    case "help" -> printHelp();
                    case "exit" -> { System.out.println("Bye."); return; }

                    case "register" -> cmdRegister(args);
                    case "login" -> cmdLogin(args);
                    case "whoami" -> cmdWhoAmI();

                    case "users" -> cmdUsers();
                    case "add_contact" -> cmdAddContact(args);

                    case "create_chat" -> cmdCreateChat(args);
                    case "chats" -> cmdChats();
                    case "history" -> cmdHistory(args);

                    case "send_text" -> cmdSendText(args);
                    case "send_image" -> cmdSendImage(args);

                    case "rename_chat" -> cmdRenameChat(args);
                    case "delete_chat" -> cmdDeleteChat(args);

                    case "rename_user" -> cmdRenameUser(args);

                    case "ack_delivered" -> cmdAckDelivered(args);
                    case "mark_read" -> cmdMarkRead(args);

                    default -> System.out.println("Unknown command. Type: help");
                }
            } catch (Exception ex) {
                System.out.println("[ERROR] " + ex.getMessage());
            }
        }
    }

    private String prompt() {
        String me = (currentUser == null) ? "guest" : currentUser.getUsername() + "#" + currentUser.getId();
        return me + "> ";
    }

    private void requireLogin() {
        if (currentUser == null) {
            throw new IllegalStateException("You must login first. Use: register <name> OR login <userId>");
        }
    }

    private void cmdRegister(String args) {
        if (args.isBlank()) {
            System.out.println("Usage: register <username>");
            return;
        }
        User u = userService.createUser(args.trim());
        currentUser = u;
        System.out.println("Registered & logged in as: " + u.getUsername() + " (id=" + u.getId() + ")");
    }

    private void cmdLogin(String args) {
        if (args.isBlank()) {
            System.out.println("Usage: login <userId>");
            return;
        }
        long id = Long.parseLong(args.trim());
        currentUser = userService.getUser(id);
        System.out.println("Logged in as: " + currentUser.getUsername() + " (id=" + currentUser.getId() + ")");
    }

    private void cmdWhoAmI() {
        if (currentUser == null) {
            System.out.println("Not logged in.");
        } else {
            System.out.println("You are: " + currentUser.getUsername() + " (id=" + currentUser.getId() + ")");
        }
    }

    private void cmdUsers() {
        List<User> users = userService.listUsers();
        if (users.isEmpty()) {
            System.out.println("(no users)");
            return;
        }
        users.forEach(u -> System.out.println("id=" + u.getId() + " | " + u.getUsername() + " | contacts=" + u.getContacts().size()));
    }

    private void cmdAddContact(String args) {
        requireLogin();
        if (args.isBlank()) {
            System.out.println("Usage: add_contact <targetUserId> <alias>");
            return;
        }
        String[] p = args.trim().split("\s+", 2);
        long targetId = Long.parseLong(p[0]);
        String alias = (p.length >= 2) ? p[1].trim() : "";
        if (alias.isBlank()) {
            alias = userService.getUser(targetId).getUsername();
        }
        userService.addContact(currentUser.getId(), targetId, alias);
        System.out.println("Added contact: userId=" + targetId + " alias=\"" + alias + "\"" );
    }

    private void cmdCreateChat(String args) {
        requireLogin();
        if (args.isBlank()) {
            System.out.println("Usage: create_chat <title>");
            return;
        }
        Chat chat = chatService.createChat(args.trim(), List.of(currentUser.getId()));
        chatService.subscribeToChat(chat.getId(), new ConsoleNotificationListener());

        System.out.println("Chat created: id=" + chat.getId() + " | title=" + chat.getTitle()
                + " | participants=" + chat.getParticipantIds());
    }

    private void cmdChats() {
        requireLogin();
        List<Chat> chats = chatService.listChats();
        if (chats.isEmpty()) {
            System.out.println("(no chats)");
            return;
        }
        chats.forEach(c -> System.out.println("id=" + c.getId() + " | title=" + c.getTitle()
                + " | participants=" + c.getParticipantIds()));
    }

    private void cmdHistory(String args) {
        requireLogin();
        if (args.isBlank()) {
            System.out.println("Usage: history <chatId>");
            return;
        }
        long chatId = Long.parseLong(args.trim());
        var history = chatService.getHistory(chatId);
        if (history.isEmpty()) {
            System.out.println("(empty)");
            return;
        }
        history.forEach(m -> System.out.println(
                m.getTimestamp() + " | sender=" + m.getSenderId() + " | " + m.getStatus() + " | " + m.preview()
        ));
    }

    private void cmdSendText(String args) {
        requireLogin();
        String[] p = args.split("\\s+", 2);
        if (p.length < 2) {
            System.out.println("Usage: send_text <chatId> <text>");
            return;
        }
        long chatId = Long.parseLong(p[0]);
        String text = p[1];
        chatService.sendText(chatId, currentUser.getId(), text);
        System.out.println("Sent.");
    }

    private void cmdSendImage(String args) {
        requireLogin();
        String[] p = args.split("\\s+", 2);
        if (p.length < 2) {
            System.out.println("Usage: send_image <chatId> <filenameOrPath>");
            return;
        }
        long chatId = Long.parseLong(p[0]);
        String name = p[1];
        chatService.sendImage(chatId, currentUser.getId(), name);
        System.out.println("Sent.");
    }

    private void cmdRenameChat(String args) {
        requireLogin();
        String[] p = args.split("\\s+", 2);
        if (p.length < 2) {
            System.out.println("Usage: rename_chat <chatId> <newTitle>");
            return;
        }
        long chatId = Long.parseLong(p[0]);
        String newTitle = p[1];
        chatService.renameChat(chatId, newTitle);
        System.out.println("Chat renamed.");
    }

    private void cmdRenameUser(String args) {
        requireLogin();
        if (args.isBlank()) {
            System.out.println("Usage: rename_user <newName>");
            return;
        }
        userService.renameUser(currentUser.getId(), args.trim());
        currentUser = userService.getUser(currentUser.getId());
        System.out.println("User renamed. Now: " + currentUser.getUsername());
    }

    private void printBanner() {
        System.out.println("=== Messenger CLI (in-memory) ===");
        System.out.println("Type: help");
    }

    private void printHelp() {
        System.out.println("""
Commands:
  help
  exit

Auth:
  register <username>
  login <userId>
  whoami

Users/Contacts:
  users
  rename_user <newName>
  delete_user <userId>
  add_contact <targetUserId> <alias>
  contacts
  remove_contact <targetUserId>

Chats:
  create_chat <title>
  chats
  rename_chat <chatId> <newTitle>
  delete_chat <chatId>

Messaging:
  send_text <chatId> <text>
  send_image <chatId> <filenameOrPath>
  history <chatId>

Message status demo:
  ack_delivered <chatId> <messageId>
  mark_read <chatId>
""");
    }


    private void cmdContacts() {
        requireLogin();
        var contacts = userService.listContacts(currentUser.getId());
        if (contacts.isEmpty()) {
            System.out.println("(no contacts)");
            return;
        }
        contacts.forEach(c -> System.out.println(
                "target=" + c.getTargetUser().getUsername() + "#" + c.getTargetUser().getId()
                        + " | alias=" + c.getAlias()
        ));
    }


    private void cmdRemoveContact(String args) {
        requireLogin();
        if (args.isBlank()) {
            System.out.println("Usage: remove_contact <targetUserId>");
            return;
        }
        long targetId = Long.parseLong(args.trim());
        userService.removeContact(currentUser.getId(), targetId);
        System.out.println("Removed contact: userId=" + targetId);
    }


    private void cmdDeleteUser(String args) {
        requireLogin();
        if (args.isBlank()) {
            System.out.println("Usage: delete_user <userId>");
            return;
        }
        long userId = Long.parseLong(args.trim());
        userService.deleteUser(userId);
        if (currentUser != null && currentUser.getId() == userId) {
            currentUser = null;
        }
        System.out.println("Deleted user id=" + userId);
    }


    private void cmdDeleteChat(String args) {
        requireLogin();
        if (args.isBlank()) {
            System.out.println("Usage: delete_chat <chatId>");
            return;
        }
        long chatId = Long.parseLong(args.trim());
        chatService.deleteChat(chatId);
        System.out.println("Deleted chat id=" + chatId);
    }


    private void cmdAckDelivered(String args) {
        requireLogin();
        String[] p = args.trim().split("\\s+");
        if (p.length < 2) {
            System.out.println("Usage: ack_delivered <chatId> <messageId>");
            return;
        }
        long chatId = Long.parseLong(p[0]);
        long messageId = Long.parseLong(p[1]);
        chatService.markDelivered(chatId, currentUser.getId(), messageId);
        System.out.println("Marked DELIVERED: chatId=" + chatId + " messageId=" + messageId);
    }


    private void cmdMarkRead(String args) {
        requireLogin();
        if (args.isBlank()) {
            System.out.println("Usage: mark_read <chatId>");
            return;
        }
        long chatId = Long.parseLong(args.trim());
        chatService.markChatRead(chatId, currentUser.getId());
        System.out.println("Marked READ for chatId=" + chatId);
    }
}
