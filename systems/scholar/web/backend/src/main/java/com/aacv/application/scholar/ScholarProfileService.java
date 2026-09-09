package com.aacv.application.scholar;

import com.aacv.api.scholar.ScholarDtos.NameCount;
import com.aacv.api.scholar.ScholarDtos.PaperBrief;
import com.aacv.api.scholar.ScholarDtos.ScholarProfile;
import com.aacv.api.scholar.ScholarDtos.ScholarSummary;
import com.aacv.api.scholar.ScholarDtos.YearCount;
import com.aacv.domain.scholar.Author;
import com.aacv.domain.scholar.AuthorRepository;
import com.aacv.domain.scholar.Authorship;
import com.aacv.domain.scholar.AuthorshipRepository;
import com.aacv.domain.scholar.Paper;
import com.aacv.domain.scholar.PaperRepository;
import com.aacv.domain.scholar.PaperTopic;
import com.aacv.domain.scholar.PaperTopicRepository;
import com.aacv.domain.scholar.Topic;
import com.aacv.domain.scholar.TopicRepository;
import com.aacv.domain.scholar.Venue;
import com.aacv.domain.scholar.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 学者画像服务：按学者姓名检索，聚合该学者的全部科研信息（仅查询该学者关联的数据）。
 */
@Service
public class ScholarProfileService {

    private final AuthorRepository authorRepository;
    private final AuthorshipRepository authorshipRepository;
    private final PaperRepository paperRepository;
    private final PaperTopicRepository paperTopicRepository;
    private final TopicRepository topicRepository;
    private final VenueRepository venueRepository;

    public ScholarProfileService(AuthorRepository authorRepository,
                                 AuthorshipRepository authorshipRepository,
                                 PaperRepository paperRepository,
                                 PaperTopicRepository paperTopicRepository,
                                 TopicRepository topicRepository,
                                 VenueRepository venueRepository) {
        this.authorRepository = authorRepository;
        this.authorshipRepository = authorshipRepository;
        this.paperRepository = paperRepository;
        this.paperTopicRepository = paperTopicRepository;
        this.topicRepository = topicRepository;
        this.venueRepository = venueRepository;
    }

    /** 按姓名模糊搜索学者，返回候选列表（含论文数）。 */
    @Transactional(readOnly = true)
    public List<ScholarSummary> searchByName(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return List.of();
        }
        List<Author> matched = authorRepository.findByNameContaining(kw);
        List<ScholarSummary> result = new ArrayList<>();
        for (Author a : matched) {
            List<Authorship> auths = authorshipRepository.findByAuthorId(a.getId());
            result.add(new ScholarSummary(a.getId(), a.getName(), auths.size(), mainInstitution(auths)));
        }
        result.sort(Comparator.comparingLong(ScholarSummary::paperCount).reversed());
        return result;
    }

    /** 学者完整画像：趋势、方向、期刊、合作者、机构、论文清单（全部按该学者过滤）。 */
    @Transactional(readOnly = true)
    public Optional<ScholarProfile> profile(String authorId) {
        Optional<Author> authorOpt = authorRepository.findById(authorId);
        if (authorOpt.isEmpty()) {
            return Optional.empty();
        }
        Author author = authorOpt.get();
        List<Authorship> myAuthorships = authorshipRepository.findByAuthorId(authorId);
        List<String> paperIds = myAuthorships.stream().map(Authorship::getPaperId).distinct().toList();

        // 该学者的论文
        List<Paper> papers = paperRepository.findAllById(paperIds);
        Map<String, Paper> paperMap = papers.stream()
                .collect(Collectors.toMap(Paper::getId, p -> p, (a, b) -> a));

        // 论文名字映射（合作者用）
        Map<String, String> authorNames = new HashMap<>();
        for (Author a : authorRepository.findByIdIn(
                authorshipRepository.findByPaperIdIn(paperIds).stream()
                        .map(Authorship::getAuthorId).distinct().toList())) {
            authorNames.put(a.getId(), a.getName());
        }

        // 发表趋势
        Map<Integer, Long> trendMap = new TreeMap<>();
        for (Paper p : papers) {
            if (p.getYear() != null) {
                trendMap.merge(p.getYear(), 1L, Long::sum);
            }
        }
        List<YearCount> trend = trendMap.entrySet().stream()
                .map(e -> new YearCount(e.getKey(), e.getValue()))
                .toList();

        // 研究方向（主题）
        Map<String, Long> topicCount = new LinkedHashMap<>();
        if (!paperIds.isEmpty()) {
            for (PaperTopic pt : paperTopicRepository.findByPaperIdIn(paperIds)) {
                topicCount.merge(pt.getTopicId(), 1L, Long::sum);
            }
        }
        Map<String, String> topicNames = new HashMap<>();
        topicRepository.findAllById(topicCount.keySet())
                .forEach(t -> topicNames.put(t.getId(), t.getName()));
        List<NameCount> topics = toRanked(topicCount, topicNames);

        // 期刊分布
        Map<String, Long> venueCount = new LinkedHashMap<>();
        for (Paper p : papers) {
            if (p.getVenueId() != null) {
                venueCount.merge(p.getVenueId(), 1L, Long::sum);
            }
        }
        Map<String, String> venueNames = new HashMap<>();
        venueRepository.findAllById(venueCount.keySet())
                .forEach(v -> venueNames.put(v.getId(), v.getName()));
        List<NameCount> venues = toRanked(venueCount, venueNames);

        // 合作者：合作论文中的其他作者
        Map<String, Long> coauthorCount = new LinkedHashMap<>();
        for (Authorship a : authorshipRepository.findByPaperIdIn(paperIds)) {
            if (!a.getAuthorId().equals(authorId)) {
                coauthorCount.merge(a.getAuthorId(), 1L, Long::sum);
            }
        }
        List<NameCount> coauthors = toRanked(coauthorCount, authorNames);

        // 机构（按署名单位解析）
        Map<String, Long> instCount = new LinkedHashMap<>();
        for (Authorship a : myAuthorships) {
            for (String inst : splitList(a.getRawAffiliation())) {
                instCount.merge(inst, 1L, Long::sum);
            }
        }
        List<NameCount> institutions = instCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .toList();

        // 角色统计与论文清单
        long firstAuthorPapers = myAuthorships.stream()
                .filter(a -> a.getAuthorOrder() != null && a.getAuthorOrder() == 1).count();
        long correspondingPapers = myAuthorships.stream().filter(Authorship::isCorresponding).count();

        List<PaperBrief> briefs = papers.stream()
                .map(p -> {
                    Authorship mine = myAuthorships.stream()
                            .filter(a -> a.getPaperId().equals(p.getId()))
                            .findFirst()
                            .orElse(null);
                    boolean first = mine != null && mine.getAuthorOrder() != null && mine.getAuthorOrder() == 1;
                    boolean corr = mine != null && mine.isCorresponding();
                    String venue = p.getVenueId() != null
                            ? venueNames.getOrDefault(p.getVenueId(),
                              venueRepository.findById(p.getVenueId()).map(Venue::getName).orElse(null))
                            : null;
                    return new PaperBrief(p.getTitle(), p.getYear(), venue, p.getPaperType(), first, corr);
                })
                .sorted(Comparator.comparing(PaperBrief::year,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Integer firstYear = trend.isEmpty() ? null : trend.get(0).year();
        Integer lastYear = trend.isEmpty() ? null : trend.get(trend.size() - 1).year();

        return Optional.of(new ScholarProfile(
                author.getId(), author.getName(),
                papers.size(), firstAuthorPapers, correspondingPapers,
                firstYear, lastYear,
                trend, topics, venues, coauthors, institutions, briefs));
    }

    private List<NameCount> toRanked(Map<String, Long> countMap, Map<String, String> nameMap) {
        return countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new NameCount(
                        nameMap.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue()))
                .toList();
    }

    /** 取署名中出现最多的机构名作为主要机构展示。 */
    private String mainInstitution(List<Authorship> auths) {
        Map<String, Long> count = new HashMap<>();
        for (Authorship a : auths) {
            for (String inst : splitList(a.getRawAffiliation())) {
                count.merge(inst, 1L, Long::sum);
            }
        }
        return count.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<String> splitList(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : s.split("[;；]")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
