package com.aacv.domain.user;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    Optional<User> findById(Long id);
    User save(User user);
    boolean existsByUsername(String username);
}