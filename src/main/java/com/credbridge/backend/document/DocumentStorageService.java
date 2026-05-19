package com.credbridge.backend.document;

import java.io.InputStream;
import java.nio.file.Path;

public interface DocumentStorageService {

    StoredDocument store(String key, InputStream inputStream, long size, String contentType);

    Path retrieveToTemp(Document document);

    void delete(Document document);
}
