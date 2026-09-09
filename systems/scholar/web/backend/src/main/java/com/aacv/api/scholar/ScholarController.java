package com.aacv.api.scholar;

import com.aacv.api.scholar.ScholarDtos.ScholarProfile;
import com.aacv.api.scholar.ScholarDtos.ScholarSummary;
import com.aacv.application.scholar.ScholarProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学者画像接口：按姓名检索学者，聚合该学者的全部科研信息。
 */
@RestController
@RequestMapping("/api/v1/scholars")
public class ScholarController {

    private final ScholarProfileService scholarProfileService;

    public ScholarController(ScholarProfileService scholarProfileService) {
        this.scholarProfileService = scholarProfileService;
    }

    /** 按姓名模糊搜索学者候选。 */
    @GetMapping
    public List<ScholarSummary> search(@RequestParam(required = false) String name) {
        return scholarProfileService.searchByName(name);
    }

    /** 学者完整画像。 */
    @GetMapping("/{id}/profile")
    public ResponseEntity<ScholarProfile> profile(@PathVariable String id) {
        return scholarProfileService.profile(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
