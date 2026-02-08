package service;

import domain.Contact;
import domain.User;
import exception.UserNotFoundException;
import repository.UserRepository;
import util.IdGenerator;

import java.util.List;

public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    // CRUD: Create
    public User createUser(String username) {
        User u = new User(IdGenerator.nextId(), username);
        users.add(u);
        return u;
    }

    // CRUD: Read
    public User getUser(long id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public List<User> listUsers() {
        return users.findAll();
    }

    // CRUD: Update
    public void renameUser(long id, String newName) {
        User u = getUser(id);
        u.setUsername(newName);
        users.update(u);
    }

    // CRUD: Delete
    public void deleteUser(long id) {
        users.deleteById(id);
    }

    // Business operation
    public void addContact(long ownerId, long targetId, String alias) {
        User owner = getUser(ownerId);
        User target = getUser(targetId);

        owner.addContact(new Contact(IdGenerator.nextId(), target, alias));
        users.update(owner);
    }
}
