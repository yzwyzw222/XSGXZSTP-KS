package com.aacv.system.governance.api;

import com.aacv.system.governance.domain.MergeDecision;
import java.time.Instant;

public record MergeDecisionResponse(
        long id,
        long candidateId,
        String decision,
        Long canonicalEntityId,
        long revisionId,
        long actorUserId,
        String reason,
        long version,
        Instant decidedAt) {

    static MergeDecisionResponse from(MergeDecision decision) {
        return new MergeDecisionResponse(
                decision.id(), decision.candidateId(), decision.decision(),
                decision.canonicalEntityId(), decision.revisionId(), decision.actorUserId(),
                decision.reason(), decision.version(), decision.decidedAt());
    }
}
