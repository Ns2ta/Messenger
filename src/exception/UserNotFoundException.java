package exception;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(long id) {
        super("User not found: id=" + id);
    }
}
