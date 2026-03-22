package com.architecture.upload.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    List<OutboxEventEntity> findTop20ByStatusOrderByCreatedAtAsc(String status);

    List<OutboxEventEntity> findTop20ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(String status, int retryCount);
}
