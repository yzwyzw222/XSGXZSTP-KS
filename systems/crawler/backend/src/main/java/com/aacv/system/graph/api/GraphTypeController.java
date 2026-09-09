package com.aacv.system.graph.api;

import com.aacv.system.graph.application.GraphTypeService;
import com.aacv.system.graph.domain.GraphTypeDefinition;
import com.aacv.system.graph.domain.GraphTypeDefinition.Kind;
import com.aacv.system.graph.domain.GraphTypeDefinition.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/graph/types")
public class GraphTypeController {
    private final GraphTypeService service;

    public GraphTypeController(GraphTypeService service) { this.service = service; }

    @GetMapping
    public List<GraphTypeDefinition> list() { return service.list(); }

    @PutMapping("/{kind}/{code}")
    public GraphTypeDefinition update(@PathVariable Kind kind, @PathVariable String code,
                                     @Valid @RequestBody UpdateRequest request) {
        return service.update(new GraphTypeDefinition(kind, code, request.displayName(), request.color(),
                request.size(), request.reviewStatus(), request.version()));
    }

    public record UpdateRequest(String displayName, String color, @NotNull Integer size,
                                @NotNull ReviewStatus reviewStatus, @NotNull Long version) { }
}
