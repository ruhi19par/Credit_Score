package com.credbridge.backend.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.credbridge.backend.application.JsonTestSupport;
import com.credbridge.backend.auth.AuthTestSupport;
import com.credbridge.backend.auth.UserRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentControllerTest {

    private static final Path TEST_UPLOAD_ROOT = Path.of("target/test-uploads/documents");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    private String borrowerToken;
    private String otherBorrowerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        deleteUploadRoot();
        borrowerToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.BORROWER);
        otherBorrowerToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.BORROWER);
        adminToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.ADMIN);
    }

    @Test
    void uploadsDocumentAndStoresMetadata() throws Exception {
        Long applicationId = createApplication("Asha Kumar", borrowerToken);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bank-statement.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "statement-content".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("applicationId", applicationId.toString())
                        .param("documentType", "BANK_STATEMENT")
                        .header("Authorization", borrowerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.documentType").value("BANK_STATEMENT"))
                .andExpect(jsonPath("$.originalFilename").value("bank-statement.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.storedFilePath").isString())
                .andReturn();

        String storedFilePath = JsonTestSupport.stringValue(result, "storedFilePath");
        assertThat(Files.readString(Path.of(storedFilePath))).isEqualTo("statement-content");
        assertThat(documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)).hasSize(1);
    }

    @Test
    void listsDocumentsForApplication() throws Exception {
        Long applicationId = createApplication("Nikhil Shah", borrowerToken);
        uploadDocument(applicationId, "id-proof.png", "ID_PROOF", borrowerToken);
        uploadDocument(applicationId, "income.pdf", "INCOME_PROOF", borrowerToken);

        mockMvc.perform(get("/api/documents/application/{applicationId}", applicationId)
                        .header("Authorization", borrowerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$[1].applicationId").value(applicationId));
    }

    @Test
    void rejectsUploadForAnotherBorrowersApplication() throws Exception {
        Long applicationId = createApplication("Borrower One", borrowerToken);

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "id.pdf", MediaType.APPLICATION_PDF_VALUE, "id".getBytes()))
                        .param("applicationId", applicationId.toString())
                        .param("documentType", "ID_PROOF")
                        .header("Authorization", otherBorrowerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanListBorrowerDocuments() throws Exception {
        Long applicationId = createApplication("Borrower One", borrowerToken);
        uploadDocument(applicationId, "id.pdf", "ID_PROOF", borrowerToken);

        mockMvc.perform(get("/api/documents/application/{applicationId}", applicationId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationId").value(applicationId));
    }

    @Test
    void rejectsUploadWithoutToken() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "id.pdf", MediaType.APPLICATION_PDF_VALUE, "id".getBytes()))
                        .param("applicationId", "1")
                        .param("documentType", "ID_PROOF"))
                .andExpect(status().isUnauthorized());
    }

    private void uploadDocument(
            Long applicationId,
            String filename,
            String documentType,
            String token
    ) throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", filename, MediaType.APPLICATION_PDF_VALUE, "content".getBytes()))
                        .param("applicationId", applicationId.toString())
                        .param("documentType", documentType)
                        .header("Authorization", token))
                .andExpect(status().isCreated());
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

    private void deleteUploadRoot() throws Exception {
        if (!Files.exists(TEST_UPLOAD_ROOT)) {
            return;
        }

        try (var paths = Files.walk(TEST_UPLOAD_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
