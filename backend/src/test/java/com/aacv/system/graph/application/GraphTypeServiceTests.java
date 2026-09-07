package com.aacv.system.graph.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aacv.system.graph.domain.GraphTypeDefinition;
import com.aacv.system.graph.domain.GraphTypeDefinition.Kind;
import com.aacv.system.graph.domain.GraphTypeDefinition.ReviewStatus;
import com.aacv.system.graph.infrastructure.persistence.GraphTypeMapper;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class GraphTypeServiceTests {
    private final GraphTypeMapper mapper = mock(GraphTypeMapper.class);
    private final AuditService audit = mock(AuditService.class);
    private final GraphTypeService service = new GraphTypeService(mapper, audit);

    @Test
    void validatesNamesColorsSizesAndVersions() {
        assertDoesNotThrow(() -> GraphTypeService.validate(value(Kind.NODE, "作者", "#AAbb00", 16, 0)));
        assertDoesNotThrow(() -> GraphTypeService.validate(value(Kind.NODE, "作者", "#AAbb00", 96, 0)));
        assertDoesNotThrow(() -> GraphTypeService.validate(value(Kind.RELATIONSHIP, "合作", "#AAbb00", 8, 0)));
        assertThrows(IllegalArgumentException.class, () -> GraphTypeService.validate(null));
        for (String name : new String[]{" ", "x".repeat(65), "作者\n"}) {
            assertThrows(IllegalArgumentException.class, () -> GraphTypeService.validate(value(Kind.NODE, name, "#aabbcc", 30, 0)));
        }
        for (String color : new String[]{"red", "#fff", "url(test)", "#gggggg"}) {
            assertThrows(IllegalArgumentException.class, () -> GraphTypeService.validate(value(Kind.NODE, "作者", color, 30, 0)));
        }
        for (int size : new int[]{0, 15, 97}) {
            assertThrows(IllegalArgumentException.class, () -> GraphTypeService.validate(value(Kind.NODE, "作者", "#aabbcc", size, 0)));
        }
        assertThrows(IllegalArgumentException.class, () -> GraphTypeService.validate(value(Kind.RELATIONSHIP, "合作", "#aabbcc", 9, 0)));
        assertThrows(IllegalArgumentException.class, () -> GraphTypeService.validate(value(Kind.NODE, "作者", "#aabbcc", 30, -1)));
    }

    @Test
    void refusesUnknownTypesAndStaleVersionsWithoutAuditSuccess() {
        var input = value(Kind.NODE, "作者", "#aabbcc", 30, 0);
        assertThrows(ResourceNotFoundException.class, () -> service.update(input));
        when(mapper.find(Kind.NODE, "AUTHOR")).thenReturn(input);
        assertThrows(ResourceConflictException.class, () -> service.update(input));
        verifyNoInteractions(audit);
    }

    @Test
    void trimsNameAndRecordsSuccessfulUpdate() {
        var input = value(Kind.NODE, " 作者 ", "#aabbcc", 30, 0);
        when(mapper.find(Kind.NODE, "AUTHOR")).thenReturn(input);
        when(mapper.update(any())).thenReturn(1);
        service.update(input);
        verify(mapper).update(value(Kind.NODE, "作者", "#aabbcc", 30, 0));
        verify(audit).record(any(), eq("GRAPH_TYPE"), eq("NODE:AUTHOR"), any(), any());
    }

    private GraphTypeDefinition value(Kind kind, String name, String color, int size, long version) {
        return new GraphTypeDefinition(kind, "AUTHOR", name, color, size, ReviewStatus.PENDING, version);
    }
}
