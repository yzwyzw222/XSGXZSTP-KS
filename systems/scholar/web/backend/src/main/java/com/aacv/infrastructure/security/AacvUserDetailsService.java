package com.aacv.infrastructure.security;

import com.aacv.domain.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AacvUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AacvUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(SessionUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}