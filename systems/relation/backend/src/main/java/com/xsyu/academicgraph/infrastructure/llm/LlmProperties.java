package com.xsyu.academicgraph.infrastructure.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 调用配置（app.llm 段，从 feature/Du 移植）。
 * baseUrl / apiKey 从环境变量占位（application.yaml 里的 ${LLM_BASE_URL:}/${LLM_API_KEY:}）：
 *  - 不配 key 也能启动，只是抽取会走 FAILED 路径（课程演示可接受，测试用 @MockitoBean 覆盖正确性）
 *  - 任何 OpenAI 兼容服务（官方 API / DeepSeek / 本地 ollama 等）只要换 baseUrl 就能接入
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** OpenAI 兼容接口的根地址，例如 https://api.openai.com/v1 */
    private String baseUrl = "https://api.openai.com/v1";

    /** API Key：官方接口必填；本地兼容服务可留空 */
    private String apiKey = "";

    /** 模型名：默认 gpt-4o-mini，便宜且对 JSON 抽取任务足够 */
    private String model = "gpt-4o-mini";

    /** 采样温度：抽取是确定性任务，调低减少幻觉 */
    private double temperature = 0.1;

    /** 单次回复 token 上限：一页摘要抽取远用不满，留足余量防截断 */
    private int maxTokens = 4096;
}
