package com.credbridge.backend.financial;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialProfileRepository extends JpaRepository<FinancialProfile, Long> {

    Optional<FinancialProfile> findByApplicationId(Long applicationId);
}
