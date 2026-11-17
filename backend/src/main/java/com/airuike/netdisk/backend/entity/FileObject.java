package com.airuike.netdisk.backend.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("file_object")
public class FileObject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bucket;
    private String objectKey;
    private Long size;
    private String mimeType;
    private String hash;
    private Integer referenceCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime cleanupAfter;
    public LocalDateTime getCleanupAfter() { return cleanupAfter; }
    public void setCleanupAfter(LocalDateTime value) { cleanupAfter = value; }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getBucket() { return bucket; }
    public void setBucket(String value) { bucket = value; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String value) { objectKey = value; }
    public Long getSize() { return size; }
    public void setSize(Long value) { size = value; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String value) { mimeType = value; }
    public String getHash() { return hash; }
    public void setHash(String value) { hash = value; }
    public Integer getReferenceCount() { return referenceCount; }
    public void setReferenceCount(Integer value) { referenceCount = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
