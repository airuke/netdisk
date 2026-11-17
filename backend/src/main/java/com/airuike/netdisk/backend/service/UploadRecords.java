package com.airuike.netdisk.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.RowMapper;

public final class UploadRecords {
    private UploadRecords() {}
    public record Session(String id, long userId, String clientId, long parentId, String name,
            long size, String hash, int chunkSize, int chunkCount, String bucket, String prefix,
            String status, Long resultNodeId, Long outputObjectId, String mergeToken,
            LocalDateTime mergeLeaseUntil, boolean instant, LocalDateTime expiresAt) {}
    public record Part(int index, long size, String hash, String objectKey,
            String attempt, String status, LocalDateTime leaseUntil) {}
    public record WritePlan(Session session, Part part, boolean alreadyStored) {}
    public record MergePlan(Session session, java.util.List<Part> parts,
            com.airuike.netdisk.backend.entity.FileObject object, boolean completed) {}
    private static LocalDateTime time(ResultSet r, String column) throws SQLException {
        var v=r.getTimestamp(column); return v==null ? null : v.toLocalDateTime();
    }
    private static Long nullableId(ResultSet r, String column) throws SQLException {
        long v=r.getLong(column); return r.wasNull() ? null : v;
    }
    public static final RowMapper<Session> SESSION=(r,n)->new Session(r.getString("id"),
            r.getLong("user_id"), r.getString("client_id"), r.getLong("parent_id"),
            r.getString("name"), r.getLong("size"), r.getString("sha256"),
            r.getInt("chunk_size"), r.getInt("chunk_count"), r.getString("bucket"),
            r.getString("object_prefix"), r.getString("status"), nullableId(r,"result_node_id"),
            nullableId(r,"output_object_id"), r.getString("merge_token"), time(r,"merge_lease_until"),
            r.getBoolean("instant"), time(r,"expires_at"));
    public static final RowMapper<Part> PART=(r,n)->new Part(r.getInt("part_index"),
            r.getLong("size"), r.getString("sha256"), r.getString("object_key"),
            r.getString("attempt"), r.getString("status"), time(r,"lease_until"));
}
