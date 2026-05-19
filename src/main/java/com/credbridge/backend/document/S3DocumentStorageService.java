package com.credbridge.backend.document;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Service
@ConditionalOnProperty(name = "app.documents.storage-provider", havingValue = "s3")
public class S3DocumentStorageService implements DocumentStorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3DocumentStorageService(
            @Value("${app.documents.s3.bucket}") String bucket,
            @Value("${app.documents.s3.region:us-east-1}") String region,
            @Value("${app.documents.s3.endpoint:}") String endpoint,
            @Value("${app.documents.s3.access-key}") String accessKey,
            @Value("${app.documents.s3.secret-key}") String secretKey,
            @Value("${app.documents.s3.path-style-access:true}") boolean pathStyleAccess
    ) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyleAccess);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.s3Client = builder.build();
        this.bucket = bucket;
    }

    @Override
    public StoredDocument store(String key, InputStream inputStream, long size, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(inputStream.readAllBytes()));
            return new StoredDocument("s3://" + bucket + "/" + key);
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to read document for S3 storage", exception);
        }
    }

    @Override
    public Path retrieveToTemp(Document document) {
        String key = document.getStoredFilePath().replace("s3://" + bucket + "/", "");
        try {
            Path tempFile = Files.createTempFile("credbridge-ocr-", "-" + document.getOriginalFilename());
            s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toFile(tempFile)
            );
            return tempFile;
        } catch (IOException exception) {
            throw new DocumentStorageException("Failed to create temporary document file", exception);
        }
    }

    @Override
    public void delete(Document document) {
        String key = document.getStoredFilePath().replace("s3://" + bucket + "/", "");
        s3Client.deleteObject(builder -> builder.bucket(bucket).key(key));
    }
}
