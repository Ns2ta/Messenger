package repository.inmemory;

import domain.User;
import repository.UserRepository;

import java.util.*;

public class InMemoryUserRepository implements UserRepository {
    private final Map<Long, User> store = new HashMap<>();

    @Override
    public void add(User user) {
        store.put(user.getId(), user);
    }

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void update(User user) {
        store.put(user.getId(), user);
    }

    @Override
    public void deleteById(long id) {
        store.remove(id);
    }
}
