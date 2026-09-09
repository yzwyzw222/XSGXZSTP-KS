package com.aacv.application.paper;

import com.aacv.api.common.ResourceNotFoundException;
import com.aacv.api.paper.PaperDtos.PaperItem;
import com.aacv.api.paper.PaperDtos.TopicItem;
import com.aacv.domain.scholar.Paper;
import com.aacv.domain.scholar.PaperRepository;
import com.aacv.domain.scholar.PaperTopic;
import com.aacv.domain.scholar.PaperTopicRepository;
import com.aacv.domain.scholar.Topic;
import com.aacv.domain.scholar.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 论文检索：按论文名搜索论文 + 查看某篇论文包含的研究主题。
 */
@Service
public class PaperSearchService {

    private final PaperRepository papers;
    private final PaperTopicRepository paperTopics;
    private final TopicRepository topics;

    public PaperSearchService(PaperRepository papers,
                              PaperTopicRepository paperTopics,
                              TopicRepository topics) {
        this.papers = papers;
        this.paperTopics = paperTopics;
        this.topics = topics;
    }

    /** 论文列表（支持标题模糊搜索），有主题的排前面，其次按年份降序。 */
    @Transactional(readOnly = true)
    public List<PaperItem> list(String search) {
        Map<String, Long> topicCount = paperTopics.findAll().stream()
                .collect(Collectors.groupingBy(PaperTopic::getPaperId, Collectors.counting()));

        String kw = search == null || search.isBlank() ? null : search.trim().toLowerCase();
        return papers.findAll().stream()
                .filter(p -> kw == null || (p.getTitle() != null && p.getTitle().toLowerCase().contains(kw)))
                .map(p -> new PaperItem(p.getId(), p.getTitle(), p.getYear(),
                        topicCount.getOrDefault(p.getId(), 0L)))
                .sorted(Comparator.comparingLong(PaperItem::topicCount).reversed()
                        .thenComparing(PaperItem::year, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PaperItem::title, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    /** 某篇论文包含的研究主题。 */
    @Transactional(readOnly = true)
    public List<TopicItem> topics(String paperId) {
        Paper paper = papers.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("论文不存在: " + paperId));

        List<String> topicIds = paperTopics.findByPaperId(paper.getId()).stream()
                .map(PaperTopic::getTopicId)
                .collect(Collectors.toList());
        if (topicIds.isEmpty()) {
            return List.of();
        }

        Map<String, Topic> topicMap = topics.findAllById(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, Function.identity()));
        return topicIds.stream()
                .distinct()
                .map(topicMap::get)
                .filter(t -> t != null)
                .map(t -> new TopicItem(t.getId(), t.getName()))
                .collect(Collectors.toList());
    }
}
