package com.aacv.system.governance.infrastructure.persistence;

import java.time.Instant;

class DecisionRow {
    private Long id;
    private long candidateId;
    private String decision;
    private Long canonicalEntityId;
    private long revisionId;
    private long actorUserId;
    private String reason;
    private long version;
    private Instant decidedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getCandidateId() { return candidateId; }
    public void setCandidateId(long candidateId) { this.candidateId = candidateId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public Long getCanonicalEntityId() { return canonicalEntityId; }
    public void setCanonicalEntityId(Long canonicalEntityId) { this.canonicalEntityId = canonicalEntityId; }
    public long getRevisionId() { return revisionId; }
    public void setRevisionId(long revisionId) { this.revisionId = revisionId; }
    public long getActorUserId() { return actorUserId; }
    public void setActorUserId(long actorUserId) { this.actorUserId = actorUserId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
