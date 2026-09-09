package com.xsyu.academicgraph.domain.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 事件仓储。
 * findPending 按 id 升序取最早的待处理事件（FIFO，保证先变更先投影）；
 * markProcessed / markFailed 用 UPDATE 直接落状态，避免"查出来再改"的并发窗口。
 * 注意：@Modifying 的 UPDATE 必须包在事务里，这里在仓储方法上直接声明 @Transactional，
 * 让图同步服务的每条事件状态更新各自独立提交（不被 Neo4j 调用的事务边界影响）。
 */
public interface GraphSyncEventRepository extends JpaRepository<GraphSyncEvent, Long> {

    List<GraphSyncEvent> findTop20ByStatusOrderByIdAsc(String status);

    long countByStatus(String status);

    @Transactional
    @Modifying
    @Query("UPDATE GraphSyncEvent e SET e.status = :status, e.processedAt = :now WHERE e.id = :id")
    void markProcessed(@Param("id") Long id, @Param("status") String status, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("UPDATE GraphSyncEvent e SET e.status = :status, e.attempts = e.attempts + 1, e.lastError = :error WHERE e.id = :id")
    void markFailed(@Param("id") Long id, @Param("status") String status, @Param("error") String error);
}
