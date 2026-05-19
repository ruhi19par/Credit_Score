package com.credbridge.backend.privacy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    List<ConsentRecord> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<ConsentRecord> findByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    Optional<ConsentRecord> findFirstByApplicationIdAndUserIdAndPurposeOrderByCreatedAtDesc(
            Long applicationId,
            Long userId,
            String purpose
    );

    Optional<ConsentRecord> findFirstByApplicationIdAndPurposeOrderByCreatedAtDesc(Long applicationId, String purpose);
}
