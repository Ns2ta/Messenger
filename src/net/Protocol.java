package net;

public final class Protocol {
    private Protocol() {}

    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String USERS = "USERS";
    public static final String CREATE_CHAT = "CREATE_CHAT";
    public static final String CHATS = "CHATS";
    public static final String SEND_TEXT = "SEND_TEXT";
    public static final String SEND_VOICE_LINK = "SEND_VOICE_LINK";
    public static final String SEND_MEDIA_LINK = "SEND_MEDIA_LINK";
    public static final String SEND_FILE_LINK  = "SEND_FILE_LINK";
    public static final String HISTORY = "HISTORY";
    public static final String HELP = "HELP";
    public static final String EXIT = "EXIT";

    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    public static final String EVENT = "EVENT";
    public static final String NEW_MESSAGE = "NEW_MESSAGE";
}
