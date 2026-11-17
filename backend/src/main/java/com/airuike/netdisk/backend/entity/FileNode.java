package com.airuike.netdisk.backend.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("file_node")
public class FileNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long parentId;
    private Long objectId;
    private String name;
    private String type;
    private String category;
    private Long size;
    private Boolean deleted;
    private LocalDateTime deletedAt;
    private Long trashRootId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { parentId = value; }
    public Long getObjectId() { return objectId; }
    public void setObjectId(Long value) { objectId = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public Long getSize() { return size; }
    public void setSize(Long value) { size = value; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean value) { deleted = value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime value) { deletedAt = value; }
    public Long getTrashRootId() { return trashRootId; }
    public void setTrashRootId(Long value) { trashRootId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
