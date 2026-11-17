package com.airuike.netdisk.backend.config;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="netdisk.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey,
        String bucket, long maxFileSize, long previewMaxSize, long textPreviewMaxSize) {}
