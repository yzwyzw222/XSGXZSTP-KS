package com.aacv.application.institution;

import com.aacv.api.common.PageResponse;
import com.aacv.api.common.ResourceNotFoundException;
import com.aacv.api.institution.InstitutionDtos.InstitutionItem;
import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.application.common.PaperRowMapper;
import com.aacv.domain.scholar.Authorship;
import com.aacv.domain.scholar.AuthorshipRepository;
import com.aacv.domain.scholar.Institution;
import com.aacv.domain.scholar.InstitutionRepository;
import com.aacv.domain.scholar.Paper;
import com.aacv.domain.scholar.PaperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 机构检索：机构列表 + 机构参与的论文分页列表。
 * 与机构合作网络保持同一口径：从署名 raw_affiliation 按分号解析机构名。
 */
@Service
public class InstitutionSearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final InstitutionRepository institutions;
    private final AuthorshipRepository authorships;
    private final PaperRepository papers;
    private final PaperRowMapper paperRowMapper;

    public InstitutionSearchService(InstitutionRepository institutions,
                                    AuthorshipRepository authorships,
                                    PaperRepository papers,
                                    PaperRowMapper paperRowMapper) {
        this.institutions = institutions;
        this.authorships = authorships;
        this.papers = papers;
        this.paperRowMapper = paperRowMapper;
    }

    /** 机构名 -> 参与的论文 ID 集合（按 raw_affiliation 分号解析，与机构合作网络同口径）。 */
    private Map<String, Set<String>> paperIdsByInstName() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Authorship a : authorships.findAll()) {
            String raw = a.getRawAffiliation();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String n : raw.split("[;；]")) {
                String trimmed = n.trim();
                if (!trimmed.isEmpty()) {
                    map.computeIfAbsent(trimmed, k -> new HashSet<>()).add(a.getPaperId());
                }
            }
        }
        return map;
    }

    /** 机构列表（仅有论文关联的），支持按名称模糊搜索，按论文数降序。 */
    @Transactional(readOnly = true)
    public List<InstitutionItem> list(String search) {
        Map<String, Set<String>> byName = paperIdsByInstName();

        String kw = search == null || search.isBlank() ? null : search.trim().toLowerCase();
        return institutions.findAll().stream()
                .map(i -> new InstitutionItem(i.getId(), i.getName(),
                        byName.getOrDefault(i.getName(), Set.of()).size()))
                .filter(i -> i.paperCount() > 0)
                .filter(i -> kw == null || i.name().toLowerCase().contains(kw))
                .sorted(Comparator.comparingLong(InstitutionItem::paperCount).reversed()
                        .thenComparing(InstitutionItem::name))
                .collect(Collectors.toList());
    }

    /** 某机构参与的论文分页列表（按年份降序），page 从 1 开始。 */
    @Transactional(readOnly = true)
    public PageResponse<PaperRow> papers(String instId, int page, int size) {
        Institution inst = institutions.findById(instId)
                .orElseThrow(() -> new ResourceNotFoundException("机构不存在: " + instId));

        Set<String> idSet = paperIdsByInstName().getOrDefault(inst.getName(), Set.of());
        // 按年份降序（无年份排最后），同年按标题，保证分页顺序稳定
        List<Paper> sorted = papers.findAllById(idSet).stream()
                .sorted(Comparator
                        .comparing(Paper::getYear, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(p -> p.getTitle() == null ? "" : p.getTitle()))
                .collect(Collectors.toList());

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int from = (safePage - 1) * safeSize;
        List<String> pageIds = from >= sorted.size()
                ? List.of()
                : sorted.subList(from, Math.min(from + safeSize, sorted.size()))
                        .stream().map(Paper::getId).collect(Collectors.toCollection(ArrayList::new));

        List<PaperRow> rows = paperRowMapper.build(pageIds);
        return new PageResponse<>(rows, safePage, safeSize, sorted.size());
    }
}
