package repository.inmemory;

import domain.Chat;
import repository.ChatRepository;

import java.util.*;

public class InMemoryChatRepository implements ChatRepository {
    private final Map<Long, Chat> store = new HashMap<>();

    @Override
    public void add(Chat chat) {
        store.put(chat.getId(), chat);
    }

    @Override
    public Optional<Chat> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Chat> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void update(Chat chat) {
        store.put(chat.getId(), chat);
    }

    @Override
    public void deleteById(long id) {
        store.remove(id);
    }
}
