package com.aacv.application.aiselection;

import com.aacv.api.aiselection.AiSelectionDtos.SourceItem;
import com.aacv.api.common.PageResponse;
import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.application.common.PaperRowMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 选文分析：数据来源列表 + 在指定来源内按作者/关键词/标题搜索论文（供人工勾选后做 AI 分析）。
 */
@Service
public class AiSelectionService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PaperRepository papers;
    private final AuthorRepository authors;
    private final AuthorshipRepository authorships;
    private final TopicRepository topics;
    private final PaperTopicRepository paperTopics;
    private final PaperRowMapper paperRowMapper;

    public AiSelectionService(PaperRepository papers,
                              AuthorRepository authors,
                              AuthorshipRepository authorships,
                              TopicRepository topics,
                              PaperTopicRepository paperTopics,
                              PaperRowMapper paperRowMapper) {
        this.papers = papers;
        this.authors = authors;
        this.authorships = authorships;
        this.topics = topics;
        this.paperTopics = paperTopics;
        this.paperRowMapper = paperRowMapper;
    }

    /** 数据来源列表（来源库 + 论文数），按论文数降序。 */
    @Transactional(readOnly = true)
    public List<SourceItem> sources() {
        return papers.countByType().stream()
                .map(row -> new SourceItem((String) row[0], (Long) row[1]))
                .sorted(Comparator.comparingLong(SourceItem::paperCount).reversed()
                        .thenComparing(SourceItem::name, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    /** 在指定数据来源内搜索论文：匹配作者名、关键词（主题）名或标题，page 从 1 开始。 */
    @Transactional(readOnly = true)
    public PageResponse<PaperRow> search(String source, String search, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        String src = source == null || source.isBlank() ? null : source.trim();
        String kw = search == null || search.isBlank() ? null : search.trim().toLowerCase();

        // 命中作者名或关键词的论文 ID 集合（标题匹配在下方直接判断）
        Set<String> matchedPaperIds = Collections.emptySet();
        if (kw != null) {
            Set<String> authorIds = authors.findAll().stream()
                    .filter(a -> a.getName() != null && a.getName().toLowerCase().contains(kw))
                    .map(Author::getId)
                    .collect(Collectors.toSet());
            Set<String> topicIds = topics.findAll().stream()
                    .filter(t -> t.getName() != null && t.getName().toLowerCase().contains(kw))
                    .map(Topic::getId)
                    .collect(Collectors.toSet());

            Set<String> ids = new HashSet<>();
            if (!authorIds.isEmpty()) {
                for (Authorship ship : authorships.findAll()) {
                    if (authorIds.contains(ship.getAuthorId())) {
                        ids.add(ship.getPaperId());
                    }
                }
            }
            if (!topicIds.isEmpty()) {
                for (PaperTopic pt : paperTopics.findAll()) {
                    if (topicIds.contains(pt.getTopicId())) {
                        ids.add(pt.getPaperId());
                    }
                }
            }
            matchedPaperIds = ids;
        }
        final Set<String> matched = matchedPaperIds;

        List<Paper> filtered = papers.findAll().stream()
                .filter(p -> src == null || src.equals(p.getPaperType()))
                .filter(p -> kw == null
                        || (p.getTitle() != null && p.getTitle().toLowerCase().contains(kw))
                        || matched.contains(p.getId()))
                .sorted(Comparator.comparing(Paper::getYear, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Paper::getTitle, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<PaperRow> rows = paperRowMapper.build(
                filtered.subList(from, to).stream().map(Paper::getId).collect(Collectors.toList()));

        return new PageResponse<>(rows, safePage, safeSize, total);
    }
}
