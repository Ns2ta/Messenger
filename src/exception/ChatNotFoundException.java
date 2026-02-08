package exception;

public class ChatNotFoundException extends NotFoundException {
    public ChatNotFoundException(long id) {
        super("Chat not found: id=" + id);
    }
}
