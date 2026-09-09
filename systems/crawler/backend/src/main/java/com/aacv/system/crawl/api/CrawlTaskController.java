package com.aacv.system.crawl.api;

import com.aacv.system.crawl.application.CrawlTaskService;
import com.aacv.system.crawl.application.CrawlRunService;
import com.aacv.system.crawl.domain.CrawlScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/crawl")
public class CrawlTaskController {

    private final CrawlTaskService service;
    private final CrawlRunService runService;

    public CrawlTaskController(CrawlTaskService service, CrawlRunService runService) {
        this.service = service;
        this.runService = runService;
    }

    @GetMapping("/tasks")
    public CrawlTaskPageResponse findTaskPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CrawlTaskPageResponse.from(service.findPage(page, size));
    }

    @GetMapping("/tasks/{taskId}")
    public CrawlTaskResponse findTask(@PathVariable @Min(1) long taskId) {
        return CrawlTaskPageResponse.toTaskResponse(service.requireTask(taskId));
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public CrawlTaskResponse create(@Valid @RequestBody CreateCrawlTaskRequest request) {
        return CrawlTaskPageResponse.toTaskResponse(service.create(
                request.sourceId(), request.name(), toScope(request.parameters())));
    }

    @PutMapping("/tasks/{taskId}")
    public CrawlTaskResponse update(
            @PathVariable @Min(1) long taskId,
            @Valid @RequestBody UpdateCrawlTaskRequest request) {
        return CrawlTaskPageResponse.toTaskResponse(service.update(
                taskId, request.name(), toScope(request.parameters()), request.version()));
    }

    @PostMapping("/tasks/{taskId}/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CrawlRunResponse trigger(@PathVariable @Min(1) long taskId) {
        return CrawlTaskPageResponse.toRunResponse(service.trigger(taskId));
    }

    @PutMapping("/tasks/{taskId}/schedule")
    public CrawlScheduleResponse configureSchedule(
            @PathVariable @Min(1) long taskId,
            @Valid @RequestBody DailyScheduleRequest request) {
        return CrawlScheduleResponse.from(service.configureDailySchedule(
                taskId, LocalTime.parse(request.localTime()), ZoneId.of(request.timeZone()), request.version()));
    }

    @GetMapping("/runs/{runId}")
    public CrawlRunResponse findRun(@PathVariable @Min(1) long runId) {
        return CrawlTaskPageResponse.toRunResponse(service.requireRun(runId));
    }

    @GetMapping("/runs/{runId}/failures")
    public CrawlFailurePageResponse findFailures(
            @PathVariable @Min(1) long runId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CrawlFailurePageResponse.from(runService.findFailures(runId, page, size));
    }

    @PostMapping("/runs/{runId}/pause")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CrawlRunResponse pause(@PathVariable @Min(1) long runId) {
        return CrawlTaskPageResponse.toRunResponse(runService.requestPause(runId));
    }

    @PostMapping("/runs/{runId}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CrawlRunResponse resume(@PathVariable @Min(1) long runId) {
        return CrawlTaskPageResponse.toRunResponse(runService.resume(runId));
    }

    @PostMapping("/runs/{runId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CrawlRunResponse cancel(@PathVariable @Min(1) long runId) {
        return CrawlTaskPageResponse.toRunResponse(runService.requestCancel(runId));
    }

    @PostMapping("/runs/{runId}/retry-failures")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CrawlRunResponse retryFailures(@PathVariable @Min(1) long runId) {
        return CrawlTaskPageResponse.toRunResponse(runService.retryFailures(runId));
    }

    private CrawlScope toScope(CrawlTaskParametersRequest parameters) {
        return new CrawlScope(
                parameters.publicationDateFrom(),
                parameters.publicationDateTo(),
                parameters.keyword(),
                parameters.authorIds(),
                parameters.institutionIds(),
                parameters.dois(),
                parameters.orcids(),
                parameters.rorIds(),
                parameters.updatedFrom(),
                parameters.updatedUntil(),
                parameters.maxPages(),
                parameters.maxRecords());
    }
}
