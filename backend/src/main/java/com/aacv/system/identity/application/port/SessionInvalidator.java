package com.aacv.system.identity.application.port;

public interface SessionInvalidator {

    void invalidateByPrincipalName(String principalName);
}
