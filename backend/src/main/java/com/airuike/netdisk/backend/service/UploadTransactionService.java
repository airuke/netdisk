package com.airuike.netdisk.backend.service;

import com.airuike.netdisk.backend.config.StorageProperties;
import com.airuike.netdisk.backend.entity.FileNode;
import com.airuike.netdisk.backend.entity.FileObject;
import com.airuike.netdisk.backend.mapper.FileNodeMapper;
import com.airuike.netdisk.backend.mapper.FileObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.airuike.netdisk.backend.service.FileRules.*;

@Service
public class UploadTransactionService {
    private final FileAccess access;
    private final FileNodeMapper nodes;
    private final FileObjectMapper objects;
    private final StorageProperties props;
    private final JdbcTemplate jdbc;
    public UploadTransactionService(FileAccess access, FileNodeMapper nodes, FileObjectMapper objects,
            StorageProperties props, JdbcTemplate jdbc) {
        this.access=access; this.nodes=nodes; this.objects=objects; this.props=props; this.jdbc=jdbc;
    }
    @Transactional
    public FileObject prepare(long userId, long parentId, String name, long size) {
        access.checkUser(userId, true); access.parent(userId, parentId);
        access.unique(userId, parentId, name, null);
        return stage(userId, name, size);
    }
    // 调用方的事务负责锁定用户及上传任务；MinIO 网络操作始终在事务之外。
    public FileObject stage(long userId, String name, long size) {
        FileObject object = new FileObject(); object.setBucket(props.bucket());
        object.setObjectKey("users/" + userId + "/" + UUID.randomUUID()); object.setSize(size);
        object.setMimeType(mime(name)); object.setReferenceCount(0); object.setStatus("UPLOADING");
        object.setCreatedAt(LocalDateTime.now()); objects.insert(object); return object;
    }
    @Transactional
    public FileNode complete(long userId, long parentId, String name, FileObject object) {
        access.checkUser(userId, true); access.parent(userId, parentId);
        access.unique(userId, parentId, name, null);
        var rows=jdbc.queryForList("SELECT status FROM file_object WHERE id=? FOR UPDATE", object.getId());
        if (rows.isEmpty() || !"UPLOADING".equals(rows.get(0).get("status")))
            throw fail(HttpStatus.CONFLICT, "上传任务已失效，请重新上传");
        FileNode node = attach(userId, parentId, name, object);
        jdbc.update("UPDATE file_object SET reference_count=1, status='ACTIVE', hash=? WHERE id=?",
                object.getHash(), object.getId());
        return node;
    }
    @Transactional
    public FileNode instant(long userId, long parentId, String name, long size, String hash) {
        access.checkUser(userId, true); access.parent(userId, parentId);
        access.unique(userId, parentId, name, null);
        // 只有本账号仍有有效节点的内容可复用，不开放全站哈希查询。
        var ids = jdbc.queryForList("""
                SELECT DISTINCT o.id FROM file_object o JOIN file_node n ON n.object_id=o.id
                WHERE n.user_id=? AND n.deleted=FALSE AND o.status='ACTIVE'
                AND o.hash=? AND o.size=? ORDER BY o.id LIMIT 1
                """, Long.class, userId, hash, size);
        if (ids.isEmpty()) return null;
        long id=ids.get(0);
        jdbc.queryForList("SELECT id FROM file_object WHERE id=? FOR UPDATE", id);
        FileObject object=objects.selectById(id);
        if (object==null || !"ACTIVE".equals(object.getStatus())) return null;
        FileNode node=attach(userId, parentId, name, object);
        jdbc.update("UPDATE file_object SET reference_count=reference_count+1 WHERE id=?", id);
        return node;
    }
    private FileNode attach(long userId, long parentId, String name, FileObject object) {
        FileNode node=new FileNode(); node.setUserId(userId); node.setParentId(parentId);
        node.setObjectId(object.getId()); node.setName(name); node.setType("FILE");
        node.setCategory(category(name)); node.setSize(object.getSize()); node.setDeleted(false);
        node.setCreatedAt(LocalDateTime.now()); node.setUpdatedAt(node.getCreatedAt());
        nodes.insert(node); return node;
    }
    @Transactional
    public void abort(long objectId) {
        objects.update(null, new UpdateWrapper<FileObject>().eq("id", objectId)
                .eq("status", "UPLOADING").eq("reference_count", 0).set("status", "DELETING")
                .set("cleanup_after", LocalDateTime.now().plusMinutes(10)));
    }
}
