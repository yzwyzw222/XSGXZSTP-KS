package com.aacv.system.export.api;

import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.export.domain.ExportFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ExportCreateRequest(
        @NotNull ExportFormat format,
        @NotNull @Valid ExportFilter filters) {
}
