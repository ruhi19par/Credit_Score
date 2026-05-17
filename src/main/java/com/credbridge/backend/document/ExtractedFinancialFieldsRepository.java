package com.credbridge.backend.document;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFinancialFieldsRepository extends JpaRepository<ExtractedFinancialFields, Long> {

    Optional<ExtractedFinancialFields> findByDocumentId(Long documentId);
}
