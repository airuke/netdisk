package com.airuike.netdisk.backend.storage;
import com.airuike.netdisk.backend.exception.BusinessException;
import io.minio.*;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorage implements ObjectStorage {
    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);
    private final MinioClient client;
    private final Set<String> readyBuckets = new HashSet<>();
    private final MinioClient uploader;
    public MinioObjectStorage(@Qualifier("minioClient") MinioClient client,
            @Qualifier("uploadMinioClient") MinioClient uploader) { this.client=client; this.uploader=uploader; }

    private synchronized void ensureBucket(String bucket) throws Exception {
        if (readyBuckets.contains(bucket)) return;
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        readyBuckets.add(bucket);
    }
    @Override
    public void put(String bucket, String key, InputStream data, long size, String contentType) {
        try {
            ensureBucket(bucket);
            uploader.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .stream(data, size, -1L).contentType(contentType).build());
        } catch (Exception e) { throw unavailable("上传失败，请检查 MinIO 是否启动", e); }
    }
    @Override
    public InputStream open(String bucket, String key, long offset, long length) {
        try {
            var args = GetObjectArgs.builder().bucket(bucket).object(key).offset(offset);
            if (length > 0) args.length(length);
            return client.getObject(args.build());
        } catch (Exception e) { throw unavailable("文件暂时无法读取，请稍后重试", e); }
    }
    @Override
    public void remove(String bucket, String key) {
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception e) { throw unavailable("存储清理暂时失败", e); }
    }
    @Override
    public void removePrefix(String bucket, String prefix) {
        if (!prefix.startsWith("uploads/") || !prefix.endsWith("/"))
            throw new IllegalArgumentException("分片清理前缀无效");
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) return;
            for (var result : client.listObjects(ListObjectsArgs.builder().bucket(bucket)
                    .prefix(prefix).recursive(true).build())) {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket)
                        .object(result.get().objectName()).build());
            }
        } catch (Exception e) { throw unavailable("分片清理暂时失败", e); }
    }
    private BusinessException unavailable(String message, Exception e) {
        log.warn("MinIO 操作失败: {}", e.getMessage());
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
