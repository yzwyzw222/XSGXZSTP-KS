package com.aacv.system.source.domain;

import java.util.List;

public record SourceValidationResult(boolean valid, List<String> errors) {

    public SourceValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        if (valid && !errors.isEmpty()) {
            throw new IllegalArgumentException("有效的来源配置不能包含校验错误");
        }
        if (!valid && errors.isEmpty()) {
            throw new IllegalArgumentException("无效的来源配置必须包含校验错误");
        }
    }

    public static SourceValidationResult success() {
        return new SourceValidationResult(true, List.of());
    }

    public static SourceValidationResult invalid(List<String> errors) {
        return new SourceValidationResult(false, errors);
    }
}
