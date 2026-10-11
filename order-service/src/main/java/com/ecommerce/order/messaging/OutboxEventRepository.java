package com.ecommerce.order.messaging;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Blocca il prossimo gruppo di eventi da pubblicare. SKIP LOCKED (timeout -2) permette di avviare
     * più istanze dello stesso servizio senza che due istanze pubblichino lo stesso evento.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.id")
    List<OutboxEvent> lockNextBatch(Pageable pageable);

    List<OutboxEvent> findByAggregateIdOrderByIdAsc(String aggregateId);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :threshold")
    int deletePublishedBefore(@Param("threshold") LocalDateTime threshold);
}
