package com.aacv.application.keyword;

import com.aacv.api.common.PageResponse;
import com.aacv.api.common.ResourceNotFoundException;
import com.aacv.api.keyword.KeywordDtos.KeywordItem;
import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.application.common.PaperRowMapper;
import com.aacv.domain.scholar.PaperTopic;
import com.aacv.domain.scholar.PaperTopicRepository;
import com.aacv.domain.scholar.Topic;
import com.aacv.domain.scholar.TopicRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 关键词（研究主题）检索：关键词列表 + 关键词下的论文分页列表。
 */
@Service
public class KeywordService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TopicRepository topics;
    private final PaperTopicRepository paperTopics;
    private final PaperRowMapper paperRowMapper;

    public KeywordService(TopicRepository topics,
                          PaperTopicRepository paperTopics,
                          PaperRowMapper paperRowMapper) {
        this.topics = topics;
        this.paperTopics = paperTopics;
        this.paperRowMapper = paperRowMapper;
    }

    /** 关键词列表（仅含有论文关联的），支持按名称模糊搜索，按论文数降序。 */
    @Transactional(readOnly = true)
    public List<KeywordItem> keywords(String search) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : paperTopics.countByTopic()) {
            counts.put((String) row[0], (Long) row[1]);
        }

        String kw = search == null || search.isBlank() ? null : search.trim().toLowerCase();
        return topics.findAll().stream()
                .map(t -> new KeywordItem(t.getId(), t.getName(), counts.getOrDefault(t.getId(), 0L)))
                .filter(i -> i.paperCount() > 0)
                .filter(i -> kw == null || i.name().toLowerCase().contains(kw))
                .sorted(Comparator.comparingLong(KeywordItem::paperCount).reversed()
                        .thenComparing(KeywordItem::name))
                .collect(Collectors.toList());
    }

    /** 某个关键词（主题）下的论文分页列表，page 从 1 开始。 */
    @Transactional(readOnly = true)
    public PageResponse<PaperRow> papers(String topicId, int page, int size) {
        Topic topic = topics.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("关键词不存在: " + topicId));

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<PaperTopic> ptPage = paperTopics.findByTopicId(topic.getId(), PageRequest.of(safePage - 1, safeSize));

        List<String> paperIds = ptPage.getContent().stream()
                .map(PaperTopic::getPaperId)
                .collect(Collectors.toList());
        List<PaperRow> rows = paperRowMapper.build(paperIds);

        return new PageResponse<>(rows, safePage, safeSize, ptPage.getTotalElements());
    }
}
