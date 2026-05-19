package com.credbridge.backend.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFinancialFieldsRepository extends JpaRepository<ExtractedFinancialFields, Long> {

    Optional<ExtractedFinancialFields> findByDocumentId(Long documentId);

    List<ExtractedFinancialFields> findByDocumentApplicationId(Long applicationId);
}
