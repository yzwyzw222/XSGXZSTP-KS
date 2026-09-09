package com.xsyu.academicgraph.application.academic;

import com.xsyu.academicgraph.api.authors.AuthorDtos.AuthorResponse;
import com.xsyu.academicgraph.api.authors.AuthorDtos.AuthorUpsertRequest;
import com.xsyu.academicgraph.api.common.GlobalExceptionHandler.EntityNotFoundException;
import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.domain.academic.Author;
import com.xsyu.academicgraph.domain.academic.AuthorRepository;
import com.xsyu.academicgraph.domain.sync.GraphSyncEvent;
import com.xsyu.academicgraph.domain.sync.GraphSyncEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 作者应用服务。
 * 写操作与论文模块同走事务性 Outbox：同一数据库事务里插入 graph_sync_event 事件，
 * 后台 GraphSyncService 轮询后把作者投影成 Neo4j 的 (:Author) 节点。
 */
@Service
@RequiredArgsConstructor
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;

    /** 分页列表：keyword 为空查全部，否则按姓名模糊搜索 */
    @Transactional(readOnly = true)
    public PageResponse<AuthorResponse> list(String keyword, Pageable pageable) {
        Page<Author> page = (keyword == null || keyword.isBlank())
                ? authorRepository.findAll(pageable)
                : authorRepository.findByDisplayNameContainingIgnoreCase(keyword.trim(), pageable);
        return new PageResponse<>(page.getContent().stream().map(AuthorResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AuthorResponse get(Long id) {
        return AuthorResponse.from(mustFind(id));
    }

    @Transactional
    public AuthorResponse create(AuthorUpsertRequest request) {
        Author author = new Author();
        applyFields(author, request);
        author = authorRepository.save(author); // save 后拿到自增 id，作为投影事件的 businessId
        emitSyncEvent(GraphSyncEvent.ENTITY_AUTHOR, author.getId(), GraphSyncEvent.EVENT_UPSERT);
        return AuthorResponse.from(author);
    }

    @Transactional
    public AuthorResponse update(Long id, AuthorUpsertRequest request) {
        Author author = mustFind(id);
        // 乐观锁：请求带的 version 与库中不一致 ⇒ 数据已被他人改过，拒绝覆盖
        if (request.version() == null || !Objects.equals(author.getVersion(), request.version())) {
            throw new OptimisticLockingFailureException(
                    "版本冲突: 当前 version=" + author.getVersion() + ", 请求携带=" + request.version());
        }
        applyFields(author, request);
        authorRepository.save(author);
        emitSyncEvent(GraphSyncEvent.ENTITY_AUTHOR, author.getId(), GraphSyncEvent.EVENT_UPSERT);
        return AuthorResponse.from(author);
    }

    /** 删除作者：若仍有论文署名引用（paper_author 外键无级联），数据库拒绝并翻译成 409 冲突 */
    @Transactional
    public void delete(Long id) {
        Author author = mustFind(id);
        authorRepository.delete(author);
        emitSyncEvent(GraphSyncEvent.ENTITY_AUTHOR, id, GraphSyncEvent.EVENT_DELETE);
    }

    private void applyFields(Author author, AuthorUpsertRequest request) {
        author.setDisplayName(request.displayName().trim());
        author.setOrcid(blankToNull(request.orcid()));
    }

    /** 发 Outbox 事件：与业务改动同事务提交，图同步器稍后轮询投影 */
    private void emitSyncEvent(String entityType, Long entityId, String eventType) {
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEventType(eventType);
        graphSyncEventRepository.save(event);
    }

    private Author mustFind(Long id) {
        return authorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("作者不存在: id=" + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
