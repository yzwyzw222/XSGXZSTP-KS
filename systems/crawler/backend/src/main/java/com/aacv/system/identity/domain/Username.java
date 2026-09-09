package com.aacv.system.identity.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public record Username(String value) {

    private static final Pattern FORMAT = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}._-]{2,63}");

    public Username {
        if (value == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        value = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("用户名长度必须为3至64位，且只能包含字母、数字、点、下划线或连字符");
        }
    }
}
