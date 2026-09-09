package com.example.academic_entity_extract_kg_construction.api.controller;

import com.example.academic_entity_extract_kg_construction.api.dto.response.*;
import com.example.academic_entity_extract_kg_construction.application.service.AuthorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/authors")
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuthorSummaryDto>> searchAuthors(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean fetchRemote) {

        PageResponse<AuthorSummaryDto> result;
        if (fetchRemote) {
            result = authorService.searchAndFetchAuthors(name, page, size);
        } else {
            result = authorService.searchAuthors(name, page, size);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthorDetailDto> getAuthorDetail(@PathVariable Long id) {
        return ResponseEntity.ok(authorService.getAuthorDetail(id));
    }

    @GetMapping("/{id}/graph")
    public ResponseEntity<GraphDataDto> getAuthorGraph(@PathVariable Long id) {
        return ResponseEntity.ok(authorService.getAuthorGraph(id));
    }
}
