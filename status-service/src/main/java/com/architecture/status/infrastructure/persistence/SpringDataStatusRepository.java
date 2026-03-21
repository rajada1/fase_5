package com.architecture.status.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataStatusRepository extends JpaRepository<StatusEntity, String> {
}
