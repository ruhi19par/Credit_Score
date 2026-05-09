package com.credbridge.backend.application;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    List<LoanApplication> findByUserEmailIgnoreCase(String email);
}
