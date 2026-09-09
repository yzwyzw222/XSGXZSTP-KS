package com.aacv.system.operations.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public record AuditRequestMetadata(String clientIp, String userAgent) {
    public static AuditRequestMetadata current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? from(attributes.getRequest()) : new AuditRequestMetadata(null, null);
    }

    public static AuditRequestMetadata from(HttpServletRequest request) {
        // 仅记录连接地址，不信任未经代理边界验证的转发头。
        return new AuditRequestMetadata(clean(request.getRemoteAddr(), 64), clean(request.getHeader("User-Agent"), 512));
    }

    private static String clean(String value, int limit) {
        if (value == null) return null;
        StringBuilder safe = new StringBuilder();
        value.codePoints().filter(c -> !Character.isISOControl(c)).limit(limit).forEach(safe::appendCodePoint);
        String result = safe.toString().strip();
        if (result.length() > limit) result = result.substring(0, limit);
        if (!result.isEmpty() && Character.isHighSurrogate(result.charAt(result.length() - 1))) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? null : result;
    }
}
