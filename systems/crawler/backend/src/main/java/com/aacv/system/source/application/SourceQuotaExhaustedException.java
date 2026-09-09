package com.aacv.system.source.application;

import java.time.Instant;
import java.util.Objects;

public final class SourceQuotaExhaustedException extends SourceClientException {

    private final Instant resumeAt;

    public SourceQuotaExhaustedException(Instant resumeAt) {
        super("DAILY_QUOTA_EXHAUSTED", false, 429, "来源每日额度已耗尽，等待额度恢复");
        this.resumeAt = Objects.requireNonNull(resumeAt, "额度恢复时间不能为空");
    }

    public Instant resumeAt() {
        return resumeAt;
    }
}
