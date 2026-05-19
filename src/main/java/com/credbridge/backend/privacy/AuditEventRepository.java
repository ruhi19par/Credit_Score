package com.credbridge.backend.privacy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<AuditEvent> findTop200ByOrderByCreatedAtDesc();
}
