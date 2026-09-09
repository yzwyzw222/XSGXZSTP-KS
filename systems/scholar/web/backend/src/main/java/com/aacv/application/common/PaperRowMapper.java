package com.aacv.application.common;

import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.domain.scholar.Author;
import com.aacv.domain.scholar.AuthorRepository;
import com.aacv.domain.scholar.Authorship;
import com.aacv.domain.scholar.AuthorshipRepository;
import com.aacv.domain.scholar.Paper;
import com.aacv.domain.scholar.PaperRepository;
import com.aacv.domain.scholar.Venue;
import com.aacv.domain.scholar.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 把一批论文 ID 组装成展示行（标题/来源/年份/类型/按署名顺序的作者名），供关键词检索、作者检索等复用。
 */
@Service
public class PaperRowMapper {

    private final PaperRepository papers;
    private final VenueRepository venues;
    private final AuthorshipRepository authorships;
    private final AuthorRepository authors;

    public PaperRowMapper(PaperRepository papers,
                          VenueRepository venues,
                          AuthorshipRepository authorships,
                          AuthorRepository authors) {
        this.papers = papers;
        this.venues = venues;
        this.authorships = authorships;
        this.authors = authors;
    }

    /** 按传入顺序组装论文行（忽略库中已不存在的 ID）。 */
    @Transactional(readOnly = true)
    public List<PaperRow> build(List<String> paperIds) {
        if (paperIds.isEmpty()) {
            return List.of();
        }

        Map<String, Paper> paperMap = papers.findAllById(paperIds).stream()
                .collect(Collectors.toMap(Paper::getId, p -> p, (a, b) -> a));
        Map<String, String> venueNames = venues.findByIdIn(
                        paperMap.values().stream().map(Paper::getVenueId).filter(Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName, (a, b) -> a));

        List<Authorship> shipList = authorships.findByPaperIdIn(paperIds);
        Map<String, String> authorNames = authors.findByIdIn(
                        shipList.stream().map(Authorship::getAuthorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Author::getId, Author::getName, (a, b) -> a));

        // paperId -> 按署名顺序排列的作者名列表
        Map<String, List<Authorship>> grouped = shipList.stream()
                .filter(a -> authorNames.containsKey(a.getAuthorId()))
                .collect(Collectors.groupingBy(
                        Authorship::getPaperId,
                        LinkedHashMap::new,
                        Collectors.<Authorship>toList()));
        grouped.forEach((pid, list) -> list.sort(Comparator.comparing(
                Authorship::getAuthorOrder, Comparator.nullsLast(Comparator.naturalOrder()))));
        Map<String, List<String>> authorsByPaper = new HashMap<>();
        grouped.forEach((pid, list) -> authorsByPaper.put(pid, list.stream()
                .map(a -> authorNames.get(a.getAuthorId()))
                .collect(Collectors.toList())));

        return paperIds.stream()
                .map(paperMap::get)
                .filter(Objects::nonNull)
                .map(p -> new PaperRow(
                        p.getId(),
                        p.getTitle(),
                        p.getVenueId() == null ? null : venueNames.get(p.getVenueId()),
                        p.getYear(),
                        p.getPaperType(),
                        authorsByPaper.getOrDefault(p.getId(), List.of())))
                .collect(Collectors.toList());
    }
}
