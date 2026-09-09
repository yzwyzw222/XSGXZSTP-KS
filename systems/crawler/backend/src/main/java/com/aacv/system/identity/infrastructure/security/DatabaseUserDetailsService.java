package com.aacv.system.identity.infrastructure.security;

import com.aacv.system.identity.application.UserAccountService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAccountService userAccountService;

    public DatabaseUserDetailsService(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return userAccountService.findByUsername(username)
                    .filter(account -> account.canAuthenticate())
                    .map(UserPrincipal::from)
                    .orElseThrow(() -> new UsernameNotFoundException("用户不可用于认证"));
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException("用户不可用于认证");
        }
    }
}
