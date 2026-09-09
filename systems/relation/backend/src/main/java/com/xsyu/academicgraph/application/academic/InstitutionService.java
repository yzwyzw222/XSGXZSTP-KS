package com.xsyu.academicgraph.application.academic;

import com.xsyu.academicgraph.api.common.GlobalExceptionHandler.EntityNotFoundException;
import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.institutions.InstitutionDtos.InstitutionResponse;
import com.xsyu.academicgraph.api.institutions.InstitutionDtos.InstitutionUpsertRequest;
import com.xsyu.academicgraph.domain.academic.Institution;
import com.xsyu.academicgraph.domain.academic.InstitutionRepository;
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
 * 机构应用服务。
 * 写操作与论文模块同走事务性 Outbox：同一数据库事务里插入 graph_sync_event 事件，
 * 后台 GraphSyncService 轮询后把机构投影成 Neo4j 的 (:Institution) 节点。
 */
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;

    /** 分页列表：keyword 为空查全部，否则按机构名模糊搜索 */
    @Transactional(readOnly = true)
    public PageResponse<InstitutionResponse> list(String keyword, Pageable pageable) {
        Page<Institution> page = (keyword == null || keyword.isBlank())
                ? institutionRepository.findAll(pageable)
                : institutionRepository.findByDisplayNameContainingIgnoreCase(keyword.trim(), pageable);
        return new PageResponse<>(page.getContent().stream().map(InstitutionResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public InstitutionResponse get(Long id) {
        return InstitutionResponse.from(mustFind(id));
    }

    @Transactional
    public InstitutionResponse create(InstitutionUpsertRequest request) {
        Institution institution = new Institution();
        applyFields(institution, request);
        institution = institutionRepository.save(institution); // save 后拿到自增 id，作为投影事件的 businessId
        emitSyncEvent(GraphSyncEvent.ENTITY_INSTITUTION, institution.getId(), GraphSyncEvent.EVENT_UPSERT);
        return InstitutionResponse.from(institution);
    }

    @Transactional
    public InstitutionResponse update(Long id, InstitutionUpsertRequest request) {
        Institution institution = mustFind(id);
        // 乐观锁：请求带的 version 与库中不一致 ⇒ 数据已被他人改过，拒绝覆盖
        if (request.version() == null || !Objects.equals(institution.getVersion(), request.version())) {
            throw new OptimisticLockingFailureException(
                    "版本冲突: 当前 version=" + institution.getVersion() + ", 请求携带=" + request.version());
        }
        applyFields(institution, request);
        institutionRepository.save(institution);
        emitSyncEvent(GraphSyncEvent.ENTITY_INSTITUTION, institution.getId(), GraphSyncEvent.EVENT_UPSERT);
        return InstitutionResponse.from(institution);
    }

    /** 删除机构：若仍有署名引用（paper_author 外键无级联），数据库拒绝并翻译成 409 冲突 */
    @Transactional
    public void delete(Long id) {
        Institution institution = mustFind(id);
        institutionRepository.delete(institution);
        emitSyncEvent(GraphSyncEvent.ENTITY_INSTITUTION, id, GraphSyncEvent.EVENT_DELETE);
    }

    private void applyFields(Institution institution, InstitutionUpsertRequest request) {
        institution.setDisplayName(request.displayName().trim());
        institution.setCountryCode(blankToNull(request.countryCode()));
        institution.setInstitutionType(blankToNull(request.institutionType()));
    }

    /** 发 Outbox 事件：与业务改动同事务提交，图同步器稍后轮询投影 */
    private void emitSyncEvent(String entityType, Long entityId, String eventType) {
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEventType(eventType);
        graphSyncEventRepository.save(event);
    }

    private Institution mustFind(Long id) {
        return institutionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("机构不存在: id=" + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
