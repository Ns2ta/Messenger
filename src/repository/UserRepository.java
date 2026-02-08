package repository;

import domain.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    void add(User user);
    Optional<User> findById(long id);
    List<User> findAll();
    void update(User user);
    void deleteById(long id);
}
