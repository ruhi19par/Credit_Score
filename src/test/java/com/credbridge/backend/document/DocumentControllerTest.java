package com.credbridge.backend.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.credbridge.backend.application.JsonTestSupport;
import com.credbridge.backend.application.LoanApplicationRepository;
import com.credbridge.backend.auth.AuthTestSupport;
import com.credbridge.backend.auth.UserRepository;
import com.credbridge.backend.auth.UserRole;
import com.credbridge.backend.scoring.CreditScoreRepository;
import java.util.Arrays;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Autowired
    private ExtractedFinancialFieldsRepository extractedFinancialFieldsRepository;

    @Autowired
    private CreditScoreRepository creditScoreRepository;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DocumentStorageService documentStorageService;

    private String borrowerToken;
    private String otherBorrowerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        deleteUploadRoot();
        borrowerToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.BORROWER);
        otherBorrowerToken = AuthTestSupport.registerAndLogin(mockMvc, UserRole.BORROWER);
        adminToken = AuthTestSupport.createUserAndLogin(mockMvc, userRepository, passwordEncoder, UserRole.ADMIN);
    }

    @Test
    void uploadsDocumentAndStoresMetadata() throws Exception {
        Long applicationId = createApplication("Asha Kumar", borrowerToken);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bank-statement.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfBytes("statement-content")
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
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.storedFilePath").doesNotExist())
                .andReturn();

        Long documentId = JsonTestSupport.longValue(result, "id");
        assertThat(documentId).isPositive();
        assertThat(documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId))
                .singleElement()
                .satisfies(document -> {
                    Path decrypted = documentStorageService.retrieveToTemp(document);
                    try {
                        assertThat(Files.readString(decrypted)).contains("statement-content");
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                });
        assertThat(extractedFinancialFieldsRepository.findByDocumentId(documentId))
                .hasValueSatisfying(fields -> {
                    assertThat(fields.getMonthlyIncome()).isEqualByComparingTo("0");
                    assertThat(fields.getMonthlyExpenses()).isEqualByComparingTo("0");
                    assertThat(fields.getExistingDebtPayment()).isEqualByComparingTo("0");
                });
        assertThat(creditScoreRepository.findByApplicationId(applicationId)).isPresent();
        assertThat(loanApplicationRepository.findById(applicationId))
                .hasValueSatisfying(application -> assertThat(application.getMode().name()).isEqualTo("VERIFIED"));
    }

    @Test
    void extractsFinancialValuesFromUploadedTextDocument() throws Exception {
        Long applicationId = createApplication("Verified Borrower", borrowerToken);

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile(
                                "file",
                                "income.pdf",
                                MediaType.APPLICATION_PDF_VALUE,
                                pdfBytes("income 90000 expenses 30000 debt 10000")
                        ))
                        .param("applicationId", applicationId.toString())
                        .param("documentType", "INCOME_PROOF")
                        .header("Authorization", borrowerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertThat(extractedFinancialFieldsRepository.findAll())
                .anySatisfy(fields -> {
                    assertThat(fields.getMonthlyIncome()).isEqualByComparingTo("90000");
                    assertThat(fields.getMonthlyExpenses()).isEqualByComparingTo("30000");
                    assertThat(fields.getExistingDebtPayment()).isEqualByComparingTo("10000");
                });
    }

    @Test
    void listsDocumentsForApplication() throws Exception {
        Long applicationId = createApplication("Nikhil Shah", borrowerToken);
        uploadDocument(applicationId, "id-proof.pdf", "ID_PROOF", borrowerToken);
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
                        .file(new MockMultipartFile("file", "id.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes("id")))
                        .param("applicationId", applicationId.toString())
                        .param("documentType", "ID_PROOF")
                        .header("Authorization", otherBorrowerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsupportedFileType() throws Exception {
        Long applicationId = createApplication("Borrower One", borrowerToken);

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "script.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE, "x".getBytes()))
                        .param("applicationId", applicationId.toString())
                        .param("documentType", "OTHER")
                        .header("Authorization", borrowerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Document file type is not supported"));
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        Long applicationId = createApplication("Borrower One", borrowerToken);
        byte[] oversizedContent = new byte[1025];
        Arrays.fill(oversizedContent, (byte) 'a');

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "large.pdf", MediaType.APPLICATION_PDF_VALUE, oversizedContent))
                        .param("applicationId", applicationId.toString())
                        .param("documentType", "BANK_STATEMENT")
                        .header("Authorization", borrowerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Document file exceeds maximum allowed size"));
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
    void deletesProcessedDocumentAndExtractedFields() throws Exception {
        Long applicationId = createApplication("Delete Borrower", borrowerToken);
        Long documentId = uploadDocument(applicationId, "income.pdf", "INCOME_PROOF", borrowerToken);

        assertThat(extractedFinancialFieldsRepository.findByDocumentId(documentId)).isPresent();

        mockMvc.perform(delete("/api/documents/{documentId}", documentId)
                        .header("Authorization", borrowerToken))
                .andExpect(status().isNoContent());

        assertThat(extractedFinancialFieldsRepository.findByDocumentId(documentId)).isEmpty();
        assertThat(documentRepository.findById(documentId)).isEmpty();

        Long replacementDocumentId = uploadDocument(applicationId, "income.pdf", "INCOME_PROOF", borrowerToken);

        assertThat(replacementDocumentId).isNotEqualTo(documentId);
        assertThat(extractedFinancialFieldsRepository.findByDocumentId(replacementDocumentId)).isPresent();
        assertThat(documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId))
                .singleElement()
                .satisfies(document -> assertThat(document.getId()).isEqualTo(replacementDocumentId));
    }

    @Test
    void rejectsUploadWithoutToken() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "id.pdf", MediaType.APPLICATION_PDF_VALUE, pdfBytes("id")))
                        .param("applicationId", "1")
                        .param("documentType", "ID_PROOF"))
                .andExpect(status().isUnauthorized());
    }

    private Long uploadDocument(
            Long applicationId,
            String filename,
            String documentType,
            String token
    ) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", filename, MediaType.APPLICATION_PDF_VALUE, pdfBytes("content")))
                        .param("applicationId", applicationId.toString())
                        .param("documentType", documentType)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonTestSupport.longValue(result, "id");
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

    private byte[] pdfBytes(String text) {
        return ("%PDF-1.4\n" + text).getBytes();
    }
}
