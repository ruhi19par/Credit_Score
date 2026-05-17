package com.credbridge.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.credbridge.backend.auth.AuthTestSupport;
import com.credbridge.backend.auth.UserRole;
import com.credbridge.backend.scoring.CreditScoreRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private CreditScoreRepository creditScoreRepository;

    private String borrowerToken;
    private String otherBorrowerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        borrowerToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.BORROWER);
        otherBorrowerToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.BORROWER);
        adminToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.ADMIN);
    }

    @Test
    void createsBasicApplicationAndReport() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications/basic")
                        .header("Authorization", borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Asha Kumar",
                                  "employmentType": "SALARIED",
                                  "monthlyIncome": 80000,
                                  "monthlyExpenses": 25000,
                                  "existingDebtPayment": 8000,
                                  "repaymentHistory": "EXCELLENT",
                                  "incomeStability": "STABLE",
                                  "requestedAmount": 300000,
                                  "tenureMonths": 24
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").isNumber())
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andReturn();

        Long applicationId = JsonTestSupport.longValue(result, "applicationId");

        mockMvc.perform(get("/api/reports/{applicationId}", applicationId)
                        .header("Authorization", borrowerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.fullName").value("Asha Kumar"))
                .andExpect(jsonPath("$.status").value("SCORED"))
                .andExpect(jsonPath("$.score").value(100));

        assertThat(loanApplicationRepository.findById(applicationId))
                .hasValueSatisfying(application -> {
                    assertThat(application.getFinancialProfile()).isNotNull();
                    assertThat(application.getUser()).isNotNull();
                });
        assertThat(creditScoreRepository.findByApplicationId(applicationId)).isPresent();
    }

    @Test
    void downloadsReportPdf() throws Exception {
        Long applicationId = createApplication();

        MvcResult result = mockMvc.perform(get("/api/reports/{applicationId}/pdf", applicationId)
                        .header("Authorization", borrowerToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("credit-report-" + applicationId + ".pdf")
                ))
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(pdf).hasSizeGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void borrowerCannotDownloadAnotherBorrowersReportPdf() throws Exception {
        Long applicationId = createApplication();

        mockMvc.perform(get("/api/reports/{applicationId}/pdf", applicationId)
                        .header("Authorization", otherBorrowerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidBasicApplication() throws Exception {
        mockMvc.perform(post("/api/applications/basic")
                        .header("Authorization", borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "employmentType": "SALARIED",
                                  "monthlyIncome": 0,
                                  "monthlyExpenses": 25000,
                                  "existingDebtPayment": 8000,
                                  "repaymentHistory": "EXCELLENT",
                                  "incomeStability": "STABLE",
                                  "requestedAmount": 300000,
                                  "tenureMonths": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.*", hasSize(3)));
    }

    @Test
    void updatesApplicationStatus() throws Exception {
        Long applicationId = createApplication();

        mockMvc.perform(patch("/api/applications/{id}/status", applicationId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void borrowerCannotReadAnotherBorrowersApplication() throws Exception {
        Long applicationId = createApplication();

        mockMvc.perform(get("/api/applications/{id}", applicationId)
                        .header("Authorization", otherBorrowerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void borrowerCannotUpdateStatus() throws Exception {
        Long applicationId = createApplication();

        mockMvc.perform(patch("/api/applications/{id}/status", applicationId)
                        .header("Authorization", borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsApplicationCreateWithoutToken() throws Exception {
        mockMvc.perform(post("/api/applications/basic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationJson("Unauthenticated User")))
                .andExpect(status().isUnauthorized());
    }

    private Long createApplication() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications/basic")
                        .header("Authorization", borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationJson("Nikhil Shah")))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonTestSupport.longValue(result, "applicationId");
    }

    private String validApplicationJson(String fullName) {
        return """
                {
                  "fullName": "%s",
                  "employmentType": "BUSINESS",
                  "monthlyIncome": 60000,
                  "monthlyExpenses": 28000,
                  "existingDebtPayment": 5000,
                  "repaymentHistory": "GOOD",
                  "incomeStability": "MODERATE",
                  "requestedAmount": 200000,
                  "tenureMonths": 20
                }
                """.formatted(fullName);
    }
}
