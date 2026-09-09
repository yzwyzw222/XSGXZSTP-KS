package com.xsyu.academicgraph.application.crawl;

import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportPaperItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAlexWorkMapper 的纯 JUnit 单元测试：类型映射、前缀剥离、嵌套对象取值。
 */
class OpenAlexWorkMapperTest {

    private final OpenAlexWorkMapper mapper = new OpenAlexWorkMapper();

    private SourceWork work(String type, String venueType) {
        return new SourceWork(
                "https://openalex.org/W1",
                "https://doi.org/10.1234/x",
                "测试论文",
                type,
                "en",
                LocalDate.of(2024, 5, 1),
                new SourceWork.SourceVenue("https://openalex.org/S1", "测试刊", "1234-5678", venueType),
                List.of(new SourceWork.SourceAuthorship(1, "https://openalex.org/A1", "张三",
                        "https://orcid.org/0000-0001-0000-0001",
                        List.of(new SourceWork.SourceOrganization("https://openalex.org/I1", "测试大学", "CN", "education")))),
                List.of(new SourceWork.SourceTopic("https://openalex.org/T1", "知识图谱", "计算机", "信息科学")),
                List.of("https://openalex.org/W9"),
                "摘要内容",
                42);
    }

    /** article + journal 渠道 → JOURNAL_ARTICLE */
    @Test
    void articleWithJournalVenueMapsToJournalArticle() {
        ImportPaperItem item = mapper.toImportItem(work("article", "journal"));
        assertThat(item.paperType()).isEqualTo("JOURNAL_ARTICLE");
    }

    /** article + conference 渠道 → CONFERENCE_PAPER */
    @Test
    void articleWithConferenceVenueMapsToConferencePaper() {
        ImportPaperItem item = mapper.toImportItem(work("article", "conference"));
        assertThat(item.paperType()).isEqualTo("CONFERENCE_PAPER");
    }

    /** 非 article 类型（preprint/book…）→ OTHER */
    @Test
    void nonArticleTypeMapsToOther() {
        ImportPaperItem item = mapper.toImportItem(work("preprint", "repository"));
        assertThat(item.paperType()).isEqualTo("OTHER");
    }

    /** DOI/ORCID 剥掉 https://doi.org/、https://orcid.org/ 前缀 */
    @Test
    void stripsDoiAndOrcidPrefixes() {
        ImportPaperItem item = mapper.toImportItem(work("article", "journal"));
        assertThat(item.doi()).isEqualTo("10.1234/x");
        assertThat(item.authors().get(0).orcid()).isEqualTo("0000-0001-0000-0001");
    }

    /** 作者只带第一个署名机构；主题映射成关键词（field_name 取大学科领域）；引用转外部线索 */
    @Test
    void mapsNestedEntities() {
        ImportPaperItem item = mapper.toImportItem(work("article", "journal"));
        assertThat(item.authors().get(0).name()).isEqualTo("张三");
        assertThat(item.authors().get(0).institution().name()).isEqualTo("测试大学");
        assertThat(item.authors().get(0).institution().countryCode()).isEqualTo("CN");

        assertThat(item.keywords()).hasSize(1);
        assertThat(item.keywords().get(0).name()).isEqualTo("知识图谱");
        assertThat(item.keywords().get(0).fieldName()).isEqualTo("信息科学");

        assertThat(item.references()).hasSize(1);
        assertThat(item.references().get(0).externalDoi()).isEqualTo("https://openalex.org/W9");

        assertThat(item.venue().name()).isEqualTo("测试刊");
        assertThat(item.venue().issn()).isEqualTo("1234-5678");
        assertThat(item.citationCount()).isEqualTo(42);
        assertThat(item.publicationDate()).isEqualTo(LocalDate.of(2024, 5, 1));
    }

    /** 空 DOI（缺失字段）→ null，不抛异常 */
    @Test
    void nullDoiStaysNull() {
        SourceWork noDoi = new SourceWork(
                "https://openalex.org/W2", null, "无DOI论文", "article", null, null, null,
                List.of(), List.of(), List.of(), null, null);
        assertThat(mapper.toImportItem(noDoi).doi()).isNull();
    }
}
