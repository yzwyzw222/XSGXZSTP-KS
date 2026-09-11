package com.aacv.system.authorimport.infrastructure;

import com.aacv.system.authorimport.domain.ImportOptions;
import com.aacv.system.authorimport.domain.ImportRow;
import com.aacv.system.authorimport.domain.ImportSummary;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthorImportMapper {
    int lockImport();
    ImportSummary findBatch(String requestKey);
    List<ImportSummary> recentBatches();
    String findAuthorName(long id);
    Long findPerson(String key);
    void insertAuthor(@Param("row") ImportId row, @Param("name") String name);
    void insertPerson(@Param("key") String key, @Param("authorId") long authorId);
    List<Long> findOrganizations(String name);
    void insertOrganization(@Param("row") ImportId row, @Param("name") String name);
    List<Long> findVenues(String name);
    void insertVenue(@Param("row") ImportId row, @Param("name") String name, @Param("issn") String issn);
    Long findKeyword(String key);
    void insertKeyword(@Param("row") ImportId row, @Param("key") String key, @Param("name") String name);
    Long findWork(String key);
    Long findDoi(String doi);
    String findWorkType(long id);
    List<Long> findWorkAuthors(@Param("workId") long workId, @Param("name") String name);
    int advisorExists(@Param("workId") long workId, @Param("authorId") long authorId);
    int affiliationExists(@Param("workId") long workId, @Param("authorId") long authorId, @Param("orgId") long orgId);
    void insertWorkKey(@Param("key") String key, @Param("achievementId") long achievementId);
    void insertAchievement(@Param("id") ImportId id, @Param("row") ImportRow row, @Param("key") String key,
            @Param("normalizedTitle") String normalizedTitle, @Param("venueId") Long venueId);
    void insertAbstract(@Param("id") long id, @Param("text") String text, @Param("incomplete") boolean incomplete);
    void insertAuthorLink(@Param("workId") long workId, @Param("authorId") long authorId, @Param("position") int position);
    int insertAdvisor(@Param("workId") long workId, @Param("authorId") long authorId);
    void insertInstitution(@Param("workId") long workId, @Param("orgId") long orgId);
    void insertKeywordLink(@Param("workId") long workId, @Param("subjectId") long subjectId, @Param("position") int position);
    int insertAffiliation(@Param("workId") long workId, @Param("authorId") long authorId, @Param("orgId") long orgId);
    void insertBatch(@Param("id") ImportId id, @Param("key") String key, @Param("authorId") long authorId,
            @Param("fileName") String fileName, @Param("sheetName") String sheetName,
            @Param("options") ImportOptions options, @Param("total") int total);
    void finishBatch(@Param("id") long id, @Param("imported") int imported, @Param("linked") int linked, @Param("skipped") int skipped);
    void insertRecord(@Param("batchId") long batchId, @Param("rowNumber") int rowNumber,
            @Param("workId") long workId, @Param("original") String original);
    List<Map<String, Object>> evidence(long achievementId);
}
