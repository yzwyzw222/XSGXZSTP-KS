package com.xsyu.academicgraph.application.academic;

import com.xsyu.academicgraph.api.common.GlobalExceptionHandler.EntityNotFoundException;
import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.venues.VenueDtos.VenueResponse;
import com.xsyu.academicgraph.api.venues.VenueDtos.VenueUpsertRequest;
import com.xsyu.academicgraph.domain.academic.Venue;
import com.xsyu.academicgraph.domain.academic.VenueRepository;
import com.xsyu.academicgraph.domain.sync.GraphSyncEvent;
import com.xsyu.academicgraph.domain.sync.GraphSyncEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发表渠道应用服务。
 * 写操作同走事务性 Outbox，投影成 Neo4j 的 (:Venue) 节点。
 * 注意：删除渠道会成功（paper.venue_id 外键是 ON DELETE SET NULL，论文只是失去渠道归属），
 * 图里的 (:Venue) 节点及 PUBLISHED_IN 边由 DELETE 事件摘除。
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;

    /** 分页列表：keyword 为空查全部，否则按渠道名模糊搜索 */
    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> list(String keyword, Pageable pageable) {
        Page<Venue> page = (keyword == null || keyword.isBlank())
                ? venueRepository.findAll(pageable)
                : venueRepository.findByDisplayNameContainingIgnoreCase(keyword.trim(), pageable);
        return new PageResponse<>(page.getContent().stream().map(VenueResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public VenueResponse get(Long id) {
        return VenueResponse.from(mustFind(id));
    }

    @Transactional
    public VenueResponse create(VenueUpsertRequest request) {
        Venue venue = new Venue();
        applyFields(venue, request);
        venue = venueRepository.save(venue); // save 后拿到自增 id，作为投影事件的 businessId
        emitSyncEvent(GraphSyncEvent.ENTITY_VENUE, venue.getId(), GraphSyncEvent.EVENT_UPSERT);
        return VenueResponse.from(venue);
    }

    @Transactional
    public VenueResponse update(Long id, VenueUpsertRequest request) {
        Venue venue = mustFind(id);
        applyFields(venue, request);
        venueRepository.save(venue);
        emitSyncEvent(GraphSyncEvent.ENTITY_VENUE, venue.getId(), GraphSyncEvent.EVENT_UPSERT);
        return VenueResponse.from(venue);
    }

    @Transactional
    public void delete(Long id) {
        Venue venue = mustFind(id);
        venueRepository.delete(venue);
        emitSyncEvent(GraphSyncEvent.ENTITY_VENUE, id, GraphSyncEvent.EVENT_DELETE);
    }

    private void applyFields(Venue venue, VenueUpsertRequest request) {
        venue.setDisplayName(request.displayName().trim());
        venue.setIssn(blankToNull(request.issn()));
        venue.setVenueType(blankToNull(request.venueType()));
    }

    /** 发 Outbox 事件：与业务改动同事务提交，图同步器稍后轮询投影 */
    private void emitSyncEvent(String entityType, Long entityId, String eventType) {
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEventType(eventType);
        graphSyncEventRepository.save(event);
    }

    private Venue mustFind(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("发表渠道不存在: id=" + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
