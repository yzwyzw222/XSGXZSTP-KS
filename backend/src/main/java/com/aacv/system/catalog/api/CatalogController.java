package com.aacv.system.catalog.api;

import com.aacv.system.catalog.application.CatalogService;
import com.aacv.system.catalog.domain.CatalogEntityKind;
import com.aacv.system.catalog.domain.CatalogQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Locale;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/achievements")
    public AchievementPageResponse findAchievements(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) Integer publicationYear,
            @RequestParam(required = false) String achievementType,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return AchievementPageResponse.from(service.findAchievements(query(
                title, author, organization, publicationYear, achievementType,
                sourceCode, venue, topic, page, size)));
    }

    @GetMapping("/achievements/{achievementId}")
    public AchievementDetailResponse findAchievement(@PathVariable @Min(1) long achievementId) {
        return AchievementDetailResponse.from(service.requireAchievement(achievementId));
    }

    @GetMapping("/{collection:authors|organizations|venues|topics}")
    public CatalogEntityPageResponse findEntities(
            @PathVariable String collection,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CatalogEntityPageResponse.from(service.findEntities(kind(collection), name, page, size));
    }

    @GetMapping("/{collection:authors|organizations|venues|topics}/{entityId}/achievements")
    public AchievementPageResponse findRelatedAchievements(
            @PathVariable String collection,
            @PathVariable @Min(1) long entityId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        CatalogQuery query = new CatalogQuery(
                null, null, null, null, null, null, null, null, page, size);
        return AchievementPageResponse.from(
                service.findRelatedAchievements(kind(collection), entityId, query));
    }

    private CatalogQuery query(
            String title,
            String author,
            String organization,
            Integer publicationYear,
            String achievementType,
            String sourceCode,
            String venue,
            String topic,
            int page,
            int size) {
        return new CatalogQuery(
                title, author, organization, publicationYear, achievementType,
                sourceCode, venue, topic, page, size);
    }

    private CatalogEntityKind kind(String collection) {
        return switch (collection.toLowerCase(Locale.ROOT)) {
            case "authors" -> CatalogEntityKind.AUTHOR;
            case "organizations" -> CatalogEntityKind.ORGANIZATION;
            case "venues" -> CatalogEntityKind.VENUE;
            case "topics" -> CatalogEntityKind.TOPIC;
            default -> throw new IllegalArgumentException("目录实体类型无效");
        };
    }
}
