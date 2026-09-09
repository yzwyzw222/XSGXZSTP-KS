package com.aacv.infrastructure.persistence.user;

import com.aacv.domain.user.User;
import com.aacv.domain.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SpringDataUserRepository implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public SpringDataUserRepository(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }
}