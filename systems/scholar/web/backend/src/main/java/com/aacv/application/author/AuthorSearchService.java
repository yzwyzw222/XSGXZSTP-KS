package com.aacv.application.author;

import com.aacv.api.author.AuthorDtos.AuthorItem;
import com.aacv.api.common.PageResponse;
import com.aacv.api.common.ResourceNotFoundException;
import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.application.common.PaperRowMapper;
import com.aacv.domain.scholar.Author;
import com.aacv.domain.scholar.AuthorRepository;
import com.aacv.domain.scholar.Authorship;
import com.aacv.domain.scholar.AuthorshipRepository;
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
 * 作者检索：作者列表 + 某作者名下的论文分页列表。
 */
@Service
public class AuthorSearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AuthorRepository authors;
    private final AuthorshipRepository authorships;
    private final PaperRowMapper paperRowMapper;

    public AuthorSearchService(AuthorRepository authors,
                               AuthorshipRepository authorships,
                               PaperRowMapper paperRowMapper) {
        this.authors = authors;
        this.authorships = authorships;
        this.paperRowMapper = paperRowMapper;
    }

    /** 作者列表（仅含有论文的），支持按姓名模糊搜索，按论文数降序。 */
    @Transactional(readOnly = true)
    public List<AuthorItem> authorList(String search) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : authorships.countByAuthor()) {
            counts.put((String) row[0], (Long) row[1]);
        }

        String kw = search == null || search.isBlank() ? null : search.trim().toLowerCase();
        return authors.findAll().stream()
                .map(a -> new AuthorItem(a.getId(), a.getName(), counts.getOrDefault(a.getId(), 0L)))
                .filter(i -> i.paperCount() > 0)
                .filter(i -> kw == null || i.name().toLowerCase().contains(kw))
                .sorted(Comparator.comparingLong(AuthorItem::paperCount).reversed()
                        .thenComparing(AuthorItem::name))
                .collect(Collectors.toList());
    }

    /** 某作者名下的论文分页列表（按署名记录分页），page 从 1 开始。 */
    @Transactional(readOnly = true)
    public PageResponse<PaperRow> papers(String authorId, int page, int size) {
        Author author = authors.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("作者不存在: " + authorId));

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Authorship> shipPage = authorships.findByAuthorId(author.getId(), PageRequest.of(safePage - 1, safeSize));

        List<String> paperIds = shipPage.getContent().stream()
                .map(Authorship::getPaperId)
                .collect(Collectors.toList());
        List<PaperRow> rows = paperRowMapper.build(paperIds);

        return new PageResponse<>(rows, safePage, safeSize, shipPage.getTotalElements());
    }
}
