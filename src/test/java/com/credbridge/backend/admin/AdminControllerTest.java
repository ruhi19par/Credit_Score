package com.credbridge.backend.admin;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.credbridge.backend.application.JsonTestSupport;
import com.credbridge.backend.auth.AuthTestSupport;
import com.credbridge.backend.auth.UserRepository;
import com.credbridge.backend.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String borrowerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        borrowerToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.BORROWER);
        adminToken = AuthTestSupport.createUserAndLogin(mockMvc, userRepository, passwordEncoder, UserRole.ADMIN);
    }

    @Test
    void adminCanListApplications() throws Exception {
        Long applicationId = createApplication("Admin Visible Borrower", borrowerToken);

        mockMvc.perform(get("/api/admin/applications")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(applicationId.intValue())))
                .andExpect(jsonPath("$[*].fullName", hasItem("Admin Visible Borrower")))
                .andExpect(jsonPath("$[*].status", hasItem("SCORED")))
                .andExpect(jsonPath("$[*].score", hasItem(87)))
                .andExpect(jsonPath("$[*].riskLevel", hasItem("LOW")));
    }

    @Test
    void adminCanReadOverview() throws Exception {
        createApplication("Overview Borrower", borrowerToken);

        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalScores", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalRequestedAmount").isNumber())
                .andExpect(jsonPath("$.averageScore").isNumber())
                .andExpect(jsonPath("$.applicationsByStatus.SCORED", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.scoresByRiskLevel.LOW", greaterThanOrEqualTo(1)));
    }

    @Test
    void borrowerCannotAccessAdminApplications() throws Exception {
        mockMvc.perform(get("/api/admin/applications")
                        .header("Authorization", borrowerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void borrowerCannotAccessAdminOverview() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", borrowerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAdminEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    private Long createApplication(String fullName, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications/basic")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
                                """.formatted(fullName)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonTestSupport.longValue(result, "applicationId");
    }
}
