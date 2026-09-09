package com.xsyu.academicgraph.infrastructure.openalex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsyu.academicgraph.application.crawl.SourceWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAlexResponseParser 的纯 JUnit 单元测试（不依赖 Spring 容器与网络）。
 * fixture 取自 feature/Luo 的 work-page-sample.json（加回 cited_by_count 字段）。
 */
class OpenAlexResponseParserTest {

    private OpenAlexResponseParser parser;
    private byte[] fixture;

    @BeforeEach
    void setUp() throws Exception {
        parser = new OpenAlexResponseParser(new ObjectMapper());
        Path fixturePath = Path.of("src/test/resources/openalex/work-page-sample.json");
        fixture = Files.readString(fixturePath).getBytes(StandardCharsets.UTF_8);
    }

    /** 正常解析：一条成果的十二个字段全部映射正确 */
    @Test
    void parsesSingleWorkWithAllFields() {
        List<SourceWork> works = parser.parseWorks(fixture);

        assertThat(works).hasSize(1);
        SourceWork work = works.get(0);
        assertThat(work.externalId()).isEqualTo("https://openalex.org/W2741809807");
        assertThat(work.doi()).isEqualTo("https://doi.org/10.7717/peerj.4375");
        assertThat(work.title()).contains("state of OA");
        assertThat(work.type()).isEqualTo("article");
        assertThat(work.language()).isEqualTo("en");
        assertThat(work.publicationDate()).isEqualTo(LocalDate.of(2018, 2, 13));
        assertThat(work.citedByCount()).isEqualTo(481);

        assertThat(work.venue()).isNotNull();
        assertThat(work.venue().displayName()).isEqualTo("PeerJ");
        assertThat(work.venue().issnL()).isEqualTo("2167-8359");
        assertThat(work.venue().type()).isEqualTo("journal");

        assertThat(work.authorships()).hasSize(2);
        assertThat(work.authorships().get(0).position()).isEqualTo(1);
        assertThat(work.authorships().get(0).authorDisplayName()).isEqualTo("Heather Piwowar");
        assertThat(work.authorships().get(0).orcid()).isEqualTo("https://orcid.org/0000-0003-1613-5981");
        assertThat(work.authorships().get(0).organizations()).hasSize(1);
        assertThat(work.authorships().get(0).organizations().get(0).displayName()).isEqualTo("OpenAlex");
        assertThat(work.authorships().get(1).organizations()).isEmpty();

        assertThat(work.topics()).hasSize(1);
        assertThat(work.topics().get(0).displayName()).contains("scientometrics");
        assertThat(work.topics().get(0).fieldName()).isEqualTo("Decision Sciences");

        assertThat(work.referencedWorkIds()).containsExactly(
                "https://openalex.org/W1560783210", "https://openalex.org/W1724212071");

        // 倒排索引重建摘要：Despite growing interest in Open Access
        assertThat(work.abstractText()).isEqualTo("Despite growing interest in Open Access");
    }

    /** 响应缺少 results 数组 → 抛出不可重试的 PARSE 异常 */
    @Test
    void missingResultsArrayFailsWholePage() {
        assertThatThrownBy(() -> parser.parseWorks("{\"meta\":{}}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(OpenAlexClientException.class)
                .satisfies(e -> {
                    OpenAlexClientException ex = (OpenAlexClientException) e;
                    assertThat(ex.getCategory()).isEqualTo("PARSE");
                    assertThat(ex.isRetryable()).isFalse();
                });
    }

    /** 响应体不是合法 JSON → PARSE 异常 */
    @Test
    void malformedJsonFailsWholePage() {
        assertThatThrownBy(() -> parser.parseWorks("不是JSON".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(OpenAlexClientException.class);
    }

    /** 摘要倒排索引位置不连续 → 放弃摘要（null），但不影响论文其他字段 */
    @Test
    void brokenAbstractPositionGapYieldsNullAbstract() {
        String json = """
                {"results":[{"id":"https://openalex.org/W1","title":"t",
                  "abstract_inverted_index":{"a":[0],"b":[2]}}]}
                """;
        List<SourceWork> works = parser.parseWorks(json.getBytes(StandardCharsets.UTF_8));
        assertThat(works).hasSize(1);
        assertThat(works.get(0).abstractText()).isNull();
        assertThat(works.get(0).title()).isEqualTo("t");
    }

    /** 单条成果缺合法 id → 跳过该条（不拖垮整页），返回空列表 */
    @Test
    void invalidWorkIdSkipsThatWorkOnly() {
        String json = """
                {"results":[{"id":"https://evil.example.org/W1","title":"bad"}]}
                """;
        assertThat(parser.parseWorks(json.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    /** 畸形 id 混在正常成果里 → 只跳过坏的那条 */
    @Test
    void oneBadWorkDoesNotBreakOthers() {
        String json = """
                {"results":[
                  {"id":"https://openalex.org/W1","title":"good"},
                  {"id":"oops","title":"bad"}
                ]}
                """;
        List<SourceWork> works = parser.parseWorks(json.getBytes(StandardCharsets.UTF_8));
        assertThat(works).hasSize(1);
        assertThat(works.get(0).title()).isEqualTo("good");
    }

    /** 摘要词数超出上限 → 放弃摘要 */
    @Test
    void oversizedAbstractTokenCountYieldsNull() {
        StringBuilder index = new StringBuilder("{");
        for (int i = 0; i <= OpenAlexResponseParser.MAX_ABSTRACT_TOKENS; i++) {
            if (i > 0) {
                index.append(',');
            }
            index.append("\"w").append(i).append("\":[0]");
        }
        index.append('}');
        String json = "{\"results\":[{\"id\":\"https://openalex.org/W1\",\"title\":\"t\","
                + "\"abstract_inverted_index\":" + index + "}]}";
        List<SourceWork> works = parser.parseWorks(json.getBytes(StandardCharsets.UTF_8));
        assertThat(works.get(0).abstractText()).isNull();
    }
}
