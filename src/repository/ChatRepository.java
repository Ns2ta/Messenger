package repository;

import domain.Chat;

import java.util.List;
import java.util.Optional;

public interface ChatRepository {
    void add(Chat chat);
    Optional<Chat> findById(long id);
    List<Chat> findAll();
    void update(Chat chat);
    void deleteById(long id);
}
