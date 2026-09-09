package com.xsyu.academicgraph.application.academic;

import com.xsyu.academicgraph.api.common.GlobalExceptionHandler.EntityNotFoundException;
import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.keywords.KeywordDtos.KeywordResponse;
import com.xsyu.academicgraph.api.keywords.KeywordDtos.KeywordUpsertRequest;
import com.xsyu.academicgraph.domain.academic.Keyword;
import com.xsyu.academicgraph.domain.academic.KeywordRepository;
import com.xsyu.academicgraph.domain.sync.GraphSyncEvent;
import com.xsyu.academicgraph.domain.sync.GraphSyncEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 关键词应用服务。
 * name 在库内唯一（uk_keyword_name）：写入前先预检查，重复时抛出可读性好的业务错误；
 * 数据库唯一索引是最后防线（并发下仍会兜底成 409 冲突）。
 * 写操作同走事务性 Outbox，投影成 Neo4j 的 (:Keyword) 节点。
 */
@Service
@RequiredArgsConstructor
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;

    /** 分页列表：keyword 为空查全部，否则按关键词名模糊搜索 */
    @Transactional(readOnly = true)
    public PageResponse<KeywordResponse> list(String keyword, Pageable pageable) {
        Page<Keyword> page = (keyword == null || keyword.isBlank())
                ? keywordRepository.findAll(pageable)
                : keywordRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable);
        return new PageResponse<>(page.getContent().stream().map(KeywordResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public KeywordResponse get(Long id) {
        return KeywordResponse.from(mustFind(id));
    }

    @Transactional
    public KeywordResponse create(KeywordUpsertRequest request) {
        checkNameUnique(request.name(), null);
        Keyword keyword = new Keyword();
        applyFields(keyword, request);
        keyword = keywordRepository.save(keyword); // save 后拿到自增 id，作为投影事件的 businessId
        emitSyncEvent(GraphSyncEvent.ENTITY_KEYWORD, keyword.getId(), GraphSyncEvent.EVENT_UPSERT);
        return KeywordResponse.from(keyword);
    }

    @Transactional
    public KeywordResponse update(Long id, KeywordUpsertRequest request) {
        Keyword keyword = mustFind(id);
        checkNameUnique(request.name(), id);
        applyFields(keyword, request);
        keywordRepository.save(keyword);
        emitSyncEvent(GraphSyncEvent.ENTITY_KEYWORD, keyword.getId(), GraphSyncEvent.EVENT_UPSERT);
        return KeywordResponse.from(keyword);
    }

    /** 删除关键词：若仍被论文引用（paper_keyword 外键无级联），数据库拒绝并翻译成 409 冲突 */
    @Transactional
    public void delete(Long id) {
        Keyword keyword = mustFind(id);
        keywordRepository.delete(keyword);
        emitSyncEvent(GraphSyncEvent.ENTITY_KEYWORD, id, GraphSyncEvent.EVENT_DELETE);
    }

    private void applyFields(Keyword keyword, KeywordUpsertRequest request) {
        keyword.setName(request.name().trim());
        keyword.setFieldName(blankToNull(request.fieldName()));
    }

    /** 名称唯一性预检：排除自身后仍有同名记录即冲突 */
    private void checkNameUnique(String name, Long excludeId) {
        keywordRepository.findByName(name.trim()).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), excludeId)) {
                throw new IllegalArgumentException("关键词已存在: " + name.trim());
            }
        });
    }

    /** 发 Outbox 事件：与业务改动同事务提交，图同步器稍后轮询投影 */
    private void emitSyncEvent(String entityType, Long entityId, String eventType) {
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEventType(eventType);
        graphSyncEventRepository.save(event);
    }

    private Keyword mustFind(Long id) {
        return keywordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("关键词不存在: id=" + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
