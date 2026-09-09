package com.example.academic_entity_extract_kg_construction.api.controller;

import com.example.academic_entity_extract_kg_construction.api.dto.response.PageResponse;
import com.example.academic_entity_extract_kg_construction.api.dto.response.PaperDetailDto;
import com.example.academic_entity_extract_kg_construction.api.dto.response.PaperSummaryDto;
import com.example.academic_entity_extract_kg_construction.application.service.PaperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/papers")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PaperSummaryDto>> searchPapers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean fetchRemote) {

        PageResponse<PaperSummaryDto> result;
        if (fetchRemote && keyword != null && !keyword.isBlank()) {
            result = paperService.searchAndFetchPapers(keyword, page, size);
        } else {
            result = paperService.searchPapers(keyword, year, page, size);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaperDetailDto> getPaperDetail(@PathVariable Long id) {
        return ResponseEntity.ok(paperService.getPaperDetail(id));
    }
}
