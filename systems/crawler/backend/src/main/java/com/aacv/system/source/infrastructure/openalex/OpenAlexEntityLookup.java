package com.aacv.system.source.infrastructure.openalex;

import com.aacv.system.source.application.SourceEntityLookupException;
import com.aacv.system.source.application.SourceQuotaExhaustedException;
import com.aacv.system.source.application.port.SourceEntityLookup;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class OpenAlexEntityLookup implements SourceEntityLookup {
    private final OpenAlexHttpTransport transport;
    private final ObjectMapper mapper;

    public OpenAlexEntityLookup(OpenAlexHttpTransport transport, ObjectMapper mapper) {
        this.transport = transport;
        this.mapper = mapper;
    }

    @Override
    public List<SourceEntity> search(SourceConnectionSettings settings, SourceEntity.Kind kind, String query) {
        return read(() -> transport.searchEntities(interactive(settings), kind, query), kind, true);
    }

    @Override
    public List<SourceEntity> resolve(SourceConnectionSettings settings, SourceEntity.Kind kind, List<String> ids) {
        return read(() -> transport.resolveEntities(interactive(settings), kind, ids), kind, false)
                .stream().filter(entity -> ids.contains(entity.id())).toList();
    }

    // 名称查询保持来源限流配置，缩短交互请求时限并限制响应体。
    private SourceConnectionSettings interactive(SourceConnectionSettings settings) {
        return new SourceConnectionSettings(settings.requestsPerSecond(), settings.maxConcurrency(),
                settings.connectTimeout().compareTo(Duration.ofSeconds(3)) < 0
                        ? settings.connectTimeout() : Duration.ofSeconds(3),
                settings.responseTimeout().compareTo(Duration.ofSeconds(8)) < 0
                        ? settings.responseTimeout() : Duration.ofSeconds(8),
                0, Math.min(settings.maxResponseBytes(), 256 * 1024));
    }

    private List<SourceEntity> read(Supplier<OpenAlexHttpResponse> request, SourceEntity.Kind kind, boolean autocomplete) {
        try {
            var response = request.get();
            if (response.statusCode() == 429) {
                throw new SourceEntityLookupException("OpenAlex名称查询达到频率或额度限制，请稍后重试");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SourceEntityLookupException("OpenAlex名称服务暂不可用，请稍后重试或检查来源连接配置");
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode results = root == null ? null : root.get("results");
            if (results == null || !results.isArray()) throw invalidResponse();
            var entities = new LinkedHashMap<String, SourceEntity>();
            for (JsonNode node : results) {
                String id = kind.normalizeId(text(node, "id", 128));
                String name = text(node, "display_name", 1000);
                if (name.isBlank()) throw invalidResponse();
                String hint = autocomplete ? text(node, "hint", 2000) : hint(node, kind);
                JsonNode works = node.path("works_count");
                Long worksCount = works.isIntegralNumber() && works.canConvertToLong() && works.longValue() >= 0
                        ? works.longValue() : null;
                entities.putIfAbsent(id, new SourceEntity(id, name, hint, worksCount));
                if (entities.size() == (autocomplete ? 10 : 50)) break;
            }
            return List.copyOf(entities.values());
        } catch (SourceQuotaExhaustedException exception) {
            throw new SourceEntityLookupException("OpenAlex每日额度已耗尽，请在额度恢复后重试名称查询");
        } catch (OpenAlexClientException exception) {
            throw new SourceEntityLookupException("OpenAlex名称查询失败或超时，请稍后重试");
        } catch (JacksonException | IllegalArgumentException exception) {
            throw invalidResponse();
        }
    }

    private String hint(JsonNode node, SourceEntity.Kind kind) {
        if (kind == SourceEntity.Kind.INSTITUTIONS) {
            String city = text(node.path("geo"), "city", 500);
            String country = text(node.path("geo"), "country", 500);
            return String.join(" · ", java.util.stream.Stream.of(city, country).filter(s -> !s.isBlank()).toList());
        }
        JsonNode institutions = node.path("last_known_institutions");
        if (!institutions.isArray()) return "";
        var names = new java.util.ArrayList<String>();
        for (JsonNode institution : institutions) {
            String name = text(institution, "display_name", 1000);
            if (!name.isBlank()) names.add(name);
            if (names.size() == 3) break;
        }
        return String.join(" · ", names);
    }

    private String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isTextual() || value.asString().length() > maxLength) throw invalidResponse();
        return value.asString().trim();
    }

    private SourceEntityLookupException invalidResponse() {
        return new SourceEntityLookupException("OpenAlex名称响应格式无效，请稍后重试");
    }
}
