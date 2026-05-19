package com.credbridge.backend.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileSecurityService {

    private static final Map<String, String> EXTENSIONS_BY_MIME = Map.of(
            "application/pdf", "pdf",
            "image/png", "png",
            "image/jpeg", "jpg"
    );
    private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");

    private final long maxFileSizeBytes;
    private final boolean virusScanEnabled;
    private final String virusScanCommand;
    private final Duration virusScanTimeout;

    public FileSecurityService(
            @Value("${app.documents.max-file-size-bytes:5242880}") long maxFileSizeBytes,
            @Value("${app.documents.virus-scan.enabled:false}") boolean virusScanEnabled,
            @Value("${app.documents.virus-scan.command:clamscan}") String virusScanCommand,
            @Value("${app.documents.virus-scan.timeout-seconds:30}") long virusScanTimeoutSeconds
    ) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.virusScanEnabled = virusScanEnabled;
        this.virusScanCommand = virusScanCommand;
        this.virusScanTimeout = Duration.ofSeconds(virusScanTimeoutSeconds);
    }

    public ValidatedDocumentFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("Document file exceeds maximum allowed size");
        }

        try {
            Path tempFile = Files.createTempFile("credbridge-upload-", ".scan");
            file.transferTo(tempFile);
            String sniffedContentType = sniffContentType(tempFile);
            validateExtension(file.getOriginalFilename(), sniffedContentType);
            scanForViruses(tempFile);
            return new ValidatedDocumentFile(tempFile, sniffedContentType, file.getSize());
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to validate document file", exception);
        }
    }

    private String sniffContentType(Path path) throws IOException {
        byte[] header;
        try (InputStream inputStream = Files.newInputStream(path)) {
            header = inputStream.readNBytes(8);
        }
        String hex = HexFormat.of().formatHex(header);
        if (hex.startsWith("25504446")) {
            return "application/pdf";
        }
        if (hex.startsWith("89504e470d0a1a0a")) {
            return "image/png";
        }
        if (hex.startsWith("ffd8ff")) {
            return "image/jpeg";
        }
        throw new IllegalArgumentException("Document file type is not supported");
    }

    private void validateExtension(String filename, String contentType) {
        String extension = org.springframework.util.StringUtils.getFilenameExtension(filename == null ? "" : filename);
        if (extension == null) {
            throw new IllegalArgumentException("Document file extension is not supported");
        }
        String normalizedExtension = extension.toLowerCase(java.util.Locale.ROOT);
        if ("image/jpeg".equals(contentType) && JPEG_EXTENSIONS.contains(normalizedExtension)) {
            return;
        }
        if (!EXTENSIONS_BY_MIME.get(contentType).equals(normalizedExtension)) {
            throw new IllegalArgumentException("Document file extension does not match file content");
        }
    }

    private void scanForViruses(Path path) {
        if (!virusScanEnabled) {
            return;
        }

        try {
            Process process = new ProcessBuilder(virusScanCommand, "--no-summary", path.toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(virusScanTimeout.toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalArgumentException("Virus scan timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException("Document failed virus scan: " + output.strip());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Virus scanner is enabled but could not be started", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Virus scan was interrupted", exception);
        }
    }
}
