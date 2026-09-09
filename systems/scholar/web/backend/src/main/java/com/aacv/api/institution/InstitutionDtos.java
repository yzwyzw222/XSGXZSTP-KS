package com.aacv.api.institution;

/**
 * 机构检索接口 DTO 集合。
 */
public final class InstitutionDtos {

    private InstitutionDtos() {
    }

    /** 机构条目：id + 名称 + 参与论文数（按署名机构解析）。 */
    public record InstitutionItem(String id, String name, long paperCount) {
    }
}
