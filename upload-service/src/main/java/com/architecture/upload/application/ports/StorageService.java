package com.architecture.upload.application.ports;

import java.io.InputStream;

public interface StorageService {
    String uploadFile(String key, InputStream content, long contentLength, String contentType);

    void deleteFile(String key);
}
