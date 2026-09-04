package com.aacv.system.ingestion.infrastructure.persistence;

import com.aacv.system.ingestion.domain.NormalizedWork;
import java.time.Instant;
import java.time.LocalDate;

class AchievementPersistenceRow {

    private Long id;
    private NormalizedWork work;
    private Long venueId;
    private Instant now;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NormalizedWork getWork() {
        return work;
    }

    public void setWork(NormalizedWork work) {
        this.work = work;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public Instant getNow() {
        return now;
    }

    public void setNow(Instant now) {
        this.now = now;
    }
}
