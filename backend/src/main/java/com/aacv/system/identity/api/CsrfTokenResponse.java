package com.aacv.system.identity.api;

public record CsrfTokenResponse(String headerName, String parameterName, String token) {
}
