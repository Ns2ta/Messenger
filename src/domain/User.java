package domain;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class User {
    private final long id;
    private String username;
    private boolean online;
    private final List<Contact> contacts = new ArrayList<>();

    public User(long id, String username) {
        this.id = id;
        this.username = username;
        this.online = true;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public boolean isOnline() { return online; }
    public List<Contact> getContacts() {
        return Collections.unmodifiableList(contacts);
    }

    public void setUsername(String username) { this.username = username; }
    public void setOnline(boolean online) { this.online = online; }

    public void addContact(Contact contact) {
        this.contacts.add(contact);
    }
}
