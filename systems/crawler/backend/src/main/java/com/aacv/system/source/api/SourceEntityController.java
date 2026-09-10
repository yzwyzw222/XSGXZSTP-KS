package com.aacv.system.source.api;

import com.aacv.system.source.application.SourceEntityService;
import com.aacv.system.source.domain.SourceEntity;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/sources/{sourceId}/entities/{kind}")
public class SourceEntityController {
    private final SourceEntityService service;

    public SourceEntityController(SourceEntityService service) { this.service = service; }

    @GetMapping
    public List<SourceEntity> search(@PathVariable @Min(1) long sourceId,
            @PathVariable String kind, @RequestParam String query) {
        return service.search(sourceId, kind, query);
    }

    @GetMapping("/resolve")
    public List<SourceEntity> resolve(@PathVariable @Min(1) long sourceId,
            @PathVariable String kind, @RequestParam List<String> ids) {
        return service.resolve(sourceId, kind, ids);
    }
}
