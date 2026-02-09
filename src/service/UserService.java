package service;

import domain.Contact;
import domain.User;
import exception.UserNotFoundException;
import repository.UserRepository;
import util.IdGenerator;

import java.util.List;

public class UserService implements UserLookup {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public User createUser(String username) {
        User u = new User(IdGenerator.nextId(), username);
        users.add(u);
        return u;
    }

    public User getUser(long id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public List<User> listUsers() {
        return users.findAll();
    }

    public void renameUser(long id, String newName) {
        User u = getUser(id);
        u.setUsername(newName);
        users.update(u);
    }

    public void deleteUser(long id) {
        users.deleteById(id);
    }

    public void addContact(long ownerId, long targetId, String alias) {
        User owner = getUser(ownerId);
        User target = getUser(targetId);

        owner.addOrUpdateContact(new Contact(IdGenerator.nextId(), target, alias));
        users.update(owner);
    }

    public List<Contact> listContacts(long ownerId) {
        User owner = getUser(ownerId);
        return owner.getContacts();
    }

    public void removeContact(long ownerId, long targetUserId) {
        User owner = getUser(ownerId);
        owner.removeContactByTargetId(targetUserId);
        users.update(owner);
    }
}
