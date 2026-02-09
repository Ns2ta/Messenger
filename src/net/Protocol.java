package net;

public final class Protocol {
    private Protocol() {}

    // Client -> Server
    public static final String REGISTER = "REGISTER";      // REGISTER <username...>
    public static final String LOGIN = "LOGIN";            // LOGIN <userId>
    public static final String USERS = "USERS";            // USERS
    public static final String CREATE_CHAT = "CREATE_CHAT";// CREATE_CHAT <title> | <id1,id2,...>
    public static final String CHATS = "CHATS";            // CHATS
    public static final String SEND_TEXT = "SEND_TEXT";    // SEND_TEXT <chatId> <text...>
    public static final String SEND_VOICE_LINK = "SEND_VOICE_LINK"; // SEND_VOICE_LINK <chatId> <title> | <url>
    public static final String SEND_MEDIA_LINK = "SEND_MEDIA_LINK";
    public static final String SEND_FILE_LINK  = "SEND_FILE_LINK";
    public static final String HISTORY = "HISTORY";        // HISTORY <chatId>
    public static final String HELP = "HELP";              // HELP
    public static final String EXIT = "EXIT";              // EXIT

    // Server -> Client
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    // Push events
    public static final String EVENT = "EVENT";            // EVENT NEW_MESSAGE ...
    public static final String NEW_MESSAGE = "NEW_MESSAGE";
}
