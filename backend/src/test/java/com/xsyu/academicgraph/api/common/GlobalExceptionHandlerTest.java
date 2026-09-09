package com.xsyu.academicgraph.api.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据完整性报错翻译的单元测试。
 * 用真实的 MySQL 8 报错原文做断言，确保返回给前端的 detail 是中文人话，
 * 不再把表名/约束名/SQL 片段暴露给用户（前端会直接把 detail 显示出来）。
 * 纯静态方法测试，不启动 Spring 上下文，跑起来是毫秒级。
 */
class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("删除被论文署名引用的作者：提示谁在引用，不带 SQL 细节")
    void foreignKeyViolation_shouldNameReferencingRelation() {
        String raw = "Cannot delete or update a parent row: a foreign key constraint fails "
                + "(`academic_graph`.`paper_author`, CONSTRAINT `fk_paper_author_author` "
                + "FOREIGN KEY (`author_id`) REFERENCES `author` (`id`))";

        String detail = GlobalExceptionHandler.translateIntegrityViolation(raw);

        assertThat(detail).isEqualTo("该记录仍被「论文署名」引用，请先解除关联后再删除");
        assertThat(detail).doesNotContain("CONSTRAINT", "academic_graph", "FOREIGN KEY");
    }

    @Test
    @DisplayName("删除被论文引用的渠道：命中 paper 子表映射")
    void foreignKeyViolation_venueUsedByPaper() {
        String raw = "Cannot delete or update a parent row: a foreign key constraint fails "
                + "(`academic_graph`.`paper`, CONSTRAINT `fk_paper_venue` "
                + "FOREIGN KEY (`venue_id`) REFERENCES `venue` (`id`) ON DELETE SET NULL)";

        assertThat(GlobalExceptionHandler.translateIntegrityViolation(raw))
                .isEqualTo("该记录仍被「论文（发表渠道或创建者）」引用，请先解除关联后再删除");
    }

    @Test
    @DisplayName("关键词重名：提示重复的值和字段中文名")
    void duplicateKeywordName_shouldShowValueAndFieldLabel() {
        String raw = "Duplicate entry '知识图谱' for key 'keyword.uk_keyword_name'";

        assertThat(GlobalExceptionHandler.translateIntegrityViolation(raw))
                .isEqualTo("「知识图谱」已存在，关键词名称不允许重复");
    }

    @Test
    @DisplayName("唯一键不在映射表里时退化为通用字段说明，不抛异常")
    void duplicateUnknownKey_shouldFallBackToGenericLabel() {
        String raw = "Duplicate entry 'abc' for key 'some_table.uk_something_new'";

        assertThat(GlobalExceptionHandler.translateIntegrityViolation(raw))
                .isEqualTo("「abc」已存在，该字段不允许重复");
    }

    @Test
    @DisplayName("无法识别或为空的报错：统一回退到通用文案，绝不返回 null")
    void unknownOrNull_shouldUseFallback() {
        assertThat(GlobalExceptionHandler.translateIntegrityViolation(null))
                .isEqualTo("操作违反了数据完整性约束（记录重复或仍被其他数据引用）");
        assertThat(GlobalExceptionHandler.translateIntegrityViolation("some weird driver message"))
                .isEqualTo("操作违反了数据完整性约束（记录重复或仍被其他数据引用）");
    }
}
