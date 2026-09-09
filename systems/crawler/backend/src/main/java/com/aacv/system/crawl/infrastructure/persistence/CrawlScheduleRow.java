package com.aacv.system.crawl.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalTime;

public class CrawlScheduleRow {
    private Long id;
    private long taskId;
    private String scheduleKey;
    private LocalTime localTime;
    private String timeZone;
    private String incrementalMode;
    private Instant nextFireAt;
    private boolean enabled;
    private long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }
    public String getScheduleKey() { return scheduleKey; }
    public void setScheduleKey(String scheduleKey) { this.scheduleKey = scheduleKey; }
    public LocalTime getLocalTime() { return localTime; }
    public void setLocalTime(LocalTime localTime) { this.localTime = localTime; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public String getIncrementalMode() { return incrementalMode; }
    public void setIncrementalMode(String incrementalMode) { this.incrementalMode = incrementalMode; }
    public Instant getNextFireAt() { return nextFireAt; }
    public void setNextFireAt(Instant nextFireAt) { this.nextFireAt = nextFireAt; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
