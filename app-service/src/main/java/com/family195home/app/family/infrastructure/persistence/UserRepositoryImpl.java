package com.family195home.app.family.infrastructure.persistence;

import com.family195home.app.family.application.UserRepository;
import com.family195home.app.family.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User save(User user) {
        userMapper.insert(user);
        return user;
    }

    @Override
    public void updateDisplayName(Long id, String displayName) {
        userMapper.updateDisplayName(id, displayName);
    }

    @Override
    public void renameDisplayName(Long id, String displayName) {
        userMapper.renameDisplayName(id, displayName);
    }

    @Override
    public Optional<User> findByLineUserId(String lineUserId) {
        return Optional.ofNullable(userMapper.selectByLineUserId(lineUserId));
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectAll();
    }
}
