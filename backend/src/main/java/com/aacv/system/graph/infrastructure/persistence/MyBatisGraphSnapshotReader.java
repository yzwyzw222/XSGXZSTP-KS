package com.aacv.system.graph.infrastructure.persistence;

import com.aacv.system.graph.domain.GraphAchievementSnapshot;
import com.aacv.system.graph.domain.GraphAchievementSnapshot.Venue;
import com.aacv.system.shared.application.ResourceNotFoundException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MyBatisGraphSnapshotReader {

    private final GraphSnapshotMapper mapper;

    public MyBatisGraphSnapshotReader(GraphSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public GraphAchievementSnapshot load(long requestedAchievementId) {
        Long canonicalId = mapper.resolveCanonicalAchievementId(requestedAchievementId);
        if (canonicalId == null) {
            throw new ResourceNotFoundException("待投影成果不存在");
        }
        GraphSnapshotRow row = mapper.findAchievement(canonicalId);
        if (row == null) {
            throw new ResourceNotFoundException("规范成果不存在");
        }
        Venue venue = row.venueId() == null
                ? null : new Venue(row.venueId(), row.venueName(), row.venueType(), row.venueIssn());
        return new GraphAchievementSnapshot(
                row.achievementId(), row.title(), row.achievementType(), row.language(),
                row.publicationDate(), row.doi(), venue,
                mapper.findAuthors(canonicalId), mapper.findAffiliations(canonicalId),
                mapper.findTopics(canonicalId), mapper.findReferences(canonicalId));
    }
}
