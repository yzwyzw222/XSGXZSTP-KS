package com.aacv.system.export.api;

import com.aacv.system.export.application.ExportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportService service;
    private final Clock clock;

    public ExportController(ExportService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExportTaskResponse create(@Valid @RequestBody ExportCreateRequest request) {
        return ExportTaskResponse.from(service.create(request.format(), request.filters()), clock.instant());
    }

    @GetMapping("/{exportId}")
    public ExportTaskResponse get(@PathVariable String exportId) {
        return ExportTaskResponse.from(service.get(exportId), clock.instant());
    }

    @GetMapping("/{exportId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String exportId,
            @RequestParam @Size(min = 32, max = 128) String token) {
        var download = service.download(exportId, token);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(download.path()));
    }
}
