package service;

import domain.User;

public interface UserLookup {
    User getUser(long id);
}