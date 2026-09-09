package com.xsyu.academicgraph.application.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsyu.academicgraph.application.extraction.LlmResultParser.ParsedExtraction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LLM 回复解析器的纯单元测试（不依赖 Spring/数据库）。
 * 覆盖 LLM 输出的各种"不听话"形态：围栏包裹、非法类型、空名称、残缺关系、纯垃圾。
 */
class LlmResultParserTest {

    private LlmResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new LlmResultParser(new ObjectMapper());
    }

    /** 标准输出：围栏 + 两类实体 + 一条关系，全部正常解析 */
    @Test
    void parsesFencedJsonWithEntitiesAndRelationships() {
        String response = """
                ```json
                {
                  "entities": [
                    {"name": "GPT-4", "type": "TOOL", "properties": {"vendor": "OpenAI"}},
                    {"name": "knowledge graph", "type": "TOPIC", "properties": {}}
                  ],
                  "relationships": [
                    {"source": "GPT-4", "target": "knowledge graph", "type": "APPLIED_TO",
                     "evidence": "applied GPT-4 to build the graph"}
                  ]
                }
                ```
                """;

        ParsedExtraction parsed = parser.parse(response);

        assertThat(parsed.entities()).hasSize(2);
        assertThat(parsed.entities().get(0).name()).isEqualTo("GPT-4");
        assertThat(parsed.entities().get(0).type()).isEqualTo("TOOL");
        assertThat(parsed.entities().get(0).properties()).isEqualTo("{\"vendor\":\"OpenAI\"}");
        assertThat(parsed.relationships()).hasSize(1);
        assertThat(parsed.relationships().get(0).type()).isEqualTo("APPLIED_TO");
        assertThat(parsed.relationships().get(0).evidence()).contains("applied");
    }

    /** 无围栏的纯 JSON 也能解析（LLM 有时真的听话） */
    @Test
    void parsesPlainJson() {
        ParsedExtraction parsed = parser.parse("{\"entities\":[],\"relationships\":[]}");
        assertThat(parsed.entities()).isEmpty();
        assertThat(parsed.relationships()).isEmpty();
    }

    /** 非法实体类型兜底成 TOPIC（与 Du 原版行为一致，宁收勿丢） */
    @Test
    void invalidEntityTypeFallsBackToTopic() {
        ParsedExtraction parsed = parser.parse("""
                {"entities":[{"name":"x","type":"WIZARDRY"}],"relationships":[]}
                """);
        assertThat(parsed.entities()).hasSize(1);
        assertThat(parsed.entities().get(0).type()).isEqualTo("TOPIC");
    }

    /** 非法关系类型直接丢弃：关系没有"默认类型"可落 */
    @Test
    void invalidRelationshipTypeIsDropped() {
        ParsedExtraction parsed = parser.parse("""
                {"entities":[{"name":"a","type":"TOOL"},{"name":"b","type":"DATASET"}],
                 "relationships":[{"source":"a","target":"b","type":"FRIENDS_WITH"}]}
                """);
        assertThat(parsed.relationships()).isEmpty();
    }

    /** 空名称、缺字段的残缺条目跳过；端点悬空（名称不在实体里）的关系保留——由服务层过滤 */
    @Test
    void skipsMalformedEntries() {
        ParsedExtraction parsed = parser.parse("""
                {"entities":[
                    {"type":"TOOL"},
                    {"name":"   ","type":"TOOL"},
                    {"name":"ok","type":"METHOD"}
                 ],
                 "relationships":[
                    {"source":"ok","type":"USES"},
                    {"source":"ok","target":"ghost","type":"USES"}
                 ]}
                """);
        assertThat(parsed.entities()).hasSize(1);
        assertThat(parsed.entities().get(0).name()).isEqualTo("ok");
        // 缺 target 字段的跳过；target 名称悬空的保留（解析层只看结构，端点校验在落库时做）
        assertThat(parsed.relationships()).hasSize(1);
        assertThat(parsed.relationships().get(0).target()).isEqualTo("ghost");
    }

    /** properties 缺失时补 "{}"（台账 JSON 列不能存 null） */
    @Test
    void missingPropertiesBecomesEmptyObject() {
        ParsedExtraction parsed = parser.parse("{\"entities\":[{\"name\":\"x\",\"type\":\"TOOL\"}]}");
        assertThat(parsed.entities().get(0).properties()).isEqualTo("{}");
    }

    /** 纯垃圾输入 → ExtractionException（调用方据此标记 FAILED） */
    @Test
    void garbageInputThrowsExtractionException() {
        assertThatThrownBy(() -> parser.parse("这不是 JSON，模型在胡说八道"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("JSON");
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(ExtractionException.class);
    }
}
