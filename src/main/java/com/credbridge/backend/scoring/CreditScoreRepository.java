package com.credbridge.backend.scoring;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditScoreRepository extends JpaRepository<CreditScore, Long> {

    Optional<CreditScore> findByApplicationId(Long applicationId);
}
