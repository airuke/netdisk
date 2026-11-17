package com.airuike.netdisk.backend.storage;
import java.io.InputStream;

public interface ObjectStorage {
    void put(String bucket, String key, InputStream data, long size, String contentType);
    InputStream open(String bucket, String key, long offset, long length);
    void remove(String bucket, String key);
    default void removePrefix(String bucket, String prefix) {
        throw new UnsupportedOperationException("此存储适配器未实现分片清理");
    }
}
