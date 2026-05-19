package com.credbridge.backend.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.documents.storage-provider", havingValue = "local", matchIfMissing = true)
public class LocalEncryptedDocumentStorageService implements DocumentStorageService {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final Path uploadRoot;
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public LocalEncryptedDocumentStorageService(
            @Value("${app.documents.upload-dir:uploads/documents}") String uploadDir,
            @Value("${app.documents.encryption-key:credbridge-local-document-key-32b}") String encryptionKey
    ) {
        this.uploadRoot = Path.of(uploadDir).normalize();
        this.secretKey = new SecretKeySpec(normalizeKey(encryptionKey), "AES");
    }

    @Override
    public StoredDocument store(String key, InputStream inputStream, long size, String contentType) {
        Path storedPath = uploadRoot.resolve(key + ".enc").normalize();
        if (!storedPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid document storage key");
        }

        try {
            Files.createDirectories(storedPath.getParent());
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            try (OutputStream outputStream = Files.newOutputStream(storedPath)) {
                outputStream.write(iv);
                outputStream.write(cipher.doFinal(inputStream.readAllBytes()));
            }
            return new StoredDocument(storedPath.toString());
        } catch (IOException | GeneralSecurityException exception) {
            throw new DocumentStorageException("Failed to store encrypted document", exception);
        }
    }

    @Override
    public Path retrieveToTemp(Document document) {
        try {
            byte[] encryptedBytes = Files.readAllBytes(Path.of(document.getStoredFilePath()));
            ByteBuffer buffer = ByteBuffer.wrap(encryptedBytes);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            Path tempFile = Files.createTempFile("credbridge-ocr-", "-" + document.getOriginalFilename());
            Files.write(tempFile, cipher.doFinal(ciphertext));
            return tempFile;
        } catch (IOException | GeneralSecurityException exception) {
            throw new DocumentStorageException("Failed to retrieve encrypted document", exception);
        }
    }

    @Override
    public void delete(Document document) {
        try {
            Files.deleteIfExists(Path.of(document.getStoredFilePath()));
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to delete stored document", exception);
        }
    }

    private byte[] normalizeKey(String key) {
        byte[] bytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] normalized = new byte[32];
        System.arraycopy(bytes, 0, normalized, 0, Math.min(bytes.length, normalized.length));
        return normalized;
    }
}
