package com.aacv.system.graph.infrastructure.persistence;

import com.aacv.system.graph.domain.GraphAchievementSnapshot.Affiliation;
import com.aacv.system.graph.domain.GraphAchievementSnapshot.Author;
import com.aacv.system.graph.domain.GraphAchievementSnapshot.ReferencedAchievement;
import com.aacv.system.graph.domain.GraphAchievementSnapshot.Topic;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
interface GraphSnapshotMapper {

    Long resolveCanonicalAchievementId(long achievementId);

    GraphSnapshotRow findAchievement(long achievementId);

    List<Author> findAuthors(long achievementId);

    List<Affiliation> findAffiliations(long achievementId);

    List<Topic> findTopics(long achievementId);

    List<ReferencedAchievement> findReferences(long achievementId);
}
