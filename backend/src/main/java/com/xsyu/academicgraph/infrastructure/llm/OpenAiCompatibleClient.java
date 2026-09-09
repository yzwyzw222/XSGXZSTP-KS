package com.xsyu.academicgraph.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xsyu.academicgraph.application.extraction.ExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * OpenAI 兼容接口客户端（从 feature/Du 移植，tools.jackson 改回 com.fasterxml）。
 * 只做一件事：把 system + user 两条消息 POST 到 /chat/completions，取回 choices[0].message.content。
 * 与 Du 原版的差异：
 *  - 显式 60 秒读超时——LLM 生成 JSON 很慢，RestClient 默认超时（约 30s 级别）容易误杀
 *  - 重试不在本类做：重试策略归 ExtractionService 统一编排，客户端保持"一次调用一个结果"的单一职责
 */
@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    /** 读超时 60 秒：LLM 逐 token 生成，比普通 HTTP API 慢一个数量级 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // 连接超时挂在 JDK HttpClient 上（与 OpenAlexHttpTransport 同款写法），读超时挂在工厂上
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 发起一次对话补全，返回助手回复的纯文本内容。
     * 失败统一抛 ExtractionException（由调用方决定重试或标记 FAILED）。
     */
    public String chat(String systemPrompt, String userPrompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", properties.getModel());
            requestBody.put("temperature", properties.getTemperature());
            requestBody.put("max_tokens", properties.getMaxTokens());

            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(messageNode("system", systemPrompt));
            messages.add(messageNode("user", userPrompt));
            requestBody.set("messages", messages);

            String response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .body(requestBody.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (!content.isMissingNode() && !content.isNull()) {
                    return content.asText();
                }
            }
            // 200 但结构不对：choices 为空或 content 缺失，视为抽取失败（上游可能做了安全过滤）
            throw new ExtractionException("LLM 响应缺少 choices[0].message.content");
        } catch (ExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM API 调用失败: {}", e.getMessage());
            throw new ExtractionException("调用 LLM API 失败: " + e.getMessage(), e);
        }
    }

    private ObjectNode messageNode(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }
}
