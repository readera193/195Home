package com.family195home.app.family.application;

import com.family195home.app.family.domain.User;

import java.util.Optional;

public interface UserRepository {

    boolean existsByEmail(String email);

    User save(User user);

    Optional<User> findByEmail(String email);

    Optional<User> findById(Long id);
}
