package com.airuike.netdisk.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix="netdisk.upload")
public record UploadProperties(
        @DefaultValue("8388608") int chunkSize,
        @DefaultValue("5368709120") long maxFileSize,
        @DefaultValue("72") int sessionTtlHours,
        @DefaultValue("30") int mergeLeaseMinutes) {
    public UploadProperties {
        if (chunkSize < 1024 * 1024 || chunkSize > 16 * 1024 * 1024 || maxFileSize < chunkSize
                || maxFileSize > 10L * 1024 * 1024 * 1024 || sessionTtlHours < 1
                || sessionTtlHours > 168 || mergeLeaseMinutes < 5 || mergeLeaseMinutes > 120) {
            throw new IllegalArgumentException("分片上传配置超出支持范围");
        }
    }
}
