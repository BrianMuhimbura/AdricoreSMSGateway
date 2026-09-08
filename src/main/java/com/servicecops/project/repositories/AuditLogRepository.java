package com.servicecops.project.repositories;

import com.servicecops.project.models.database.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    long countBySuccessFalse();
    long countByCreatedAtGreaterThanEqual(Instant start);
}

