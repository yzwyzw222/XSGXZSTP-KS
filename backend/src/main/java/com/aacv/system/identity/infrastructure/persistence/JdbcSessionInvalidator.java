package com.aacv.system.identity.infrastructure.persistence;

import com.aacv.system.identity.application.port.SessionInvalidator;
import org.springframework.stereotype.Component;

@Component
public class JdbcSessionInvalidator implements SessionInvalidator {

    private final SessionMapper sessionMapper;

    public JdbcSessionInvalidator(SessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Override
    public void invalidateByPrincipalName(String principalName) {
        sessionMapper.deleteByPrincipalName(principalName);
    }
}
