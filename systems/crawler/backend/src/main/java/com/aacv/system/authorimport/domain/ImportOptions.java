package com.aacv.system.authorimport.domain;

import java.util.Map;

public record ImportOptions(
        String scholarName, String scholarOrganization, Long authorId,
        int sheetIndex, int headerRow, String mode, Map<String, Integer> mapping) {

    public ImportOptions {
        scholarName = scholarName == null ? "" : scholarName.strip();
        scholarOrganization = scholarOrganization == null ? "" : scholarOrganization.strip();
        mode = mode == null ? "AUTHOR" : mode;
        mapping = mapping == null ? Map.of() : Map.copyOf(mapping);
        if (scholarName.isEmpty() || scholarName.length() > 200 || scholarOrganization.length() > 500
                || scholarName.codePoints().anyMatch(Character::isISOControl)
                || scholarOrganization.codePoints().anyMatch(Character::isISOControl)
                || authorId != null && authorId <= 0 || sheetIndex < 0 || sheetIndex >= 20
                || headerRow < 1 || headerRow > 20
                || !java.util.Set.of("AUTHOR", "MASTER_SUPERVISION", "DOCTOR_SUPERVISION").contains(mode)) {
            throw new IllegalArgumentException("请填写有效的学者姓名、机构、工作表、表头行和导入类型");
        }
    }

    public boolean supervision() { return !"AUTHOR".equals(mode); }
}
