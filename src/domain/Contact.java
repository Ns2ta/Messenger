package domain;

public class Contact {
    private long id;
    private User targetUser;
    private String alias;

    public Contact(long id, User targetUser, String alias) {
        this.id = id;
        this.targetUser = targetUser;
        this.alias = alias;
    }

    public long getId() { return id; }
    public User getTargetUser() { return targetUser; }
    public String getAlias() { return alias; }

    public void setAlias(String alias) { this.alias = alias; }
    public void setTargetUser(User targetUser) { this.targetUser = targetUser; }
}
