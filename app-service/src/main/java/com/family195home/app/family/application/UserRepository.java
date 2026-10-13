package com.family195home.app.family.application;

import com.family195home.app.family.domain.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    void updateDisplayName(Long id, String displayName);

    Optional<User> findByLineUserId(String lineUserId);

    Optional<User> findById(Long id);

    List<User> findAll();
}
