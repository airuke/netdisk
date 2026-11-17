package com.airuike.netdisk.backend.service;

import com.airuike.netdisk.backend.storage.ObjectStorage;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import static com.airuike.netdisk.backend.service.UploadRecords.SESSION;

@Component
public class UploadCleanupJob {
    private static final Logger log=LoggerFactory.getLogger(UploadCleanupJob.class);
    private final JdbcTemplate jdbc;
    private final UploadSessionTransactions transactions;
    private final ObjectStorage storage;
    public UploadCleanupJob(JdbcTemplate jdbc,UploadSessionTransactions transactions,ObjectStorage storage) {
        this.jdbc=jdbc; this.transactions=transactions; this.storage=storage;
    }
    @Scheduled(initialDelayString="${netdisk.upload.cleanup-delay-ms:60000}",
            fixedDelayString="${netdisk.upload.cleanup-delay-ms:60000}")
    public void cleanup() {
        var now=LocalDateTime.now();
        var stale=jdbc.query("""
                SELECT * FROM file_upload_session WHERE
                (status='OPEN' AND expires_at<=?) OR (status='MERGING' AND merge_lease_until<=?)
                ORDER BY updated_at LIMIT 30
                """,SESSION,now,now);
        for (var s:stale) {
            try { transactions.recoverForCleanup(s.id(),s.userId()); }
            catch (RuntimeException e) { log.warn("等待恢复上传任务 {}",s.id()); }
        }
        // 一小时宽限期避免清理仍在结束的分片请求；最终文件对象使用另外的 key。
        var waiting=jdbc.query("""
                SELECT * FROM file_upload_session WHERE status IN ('COMPLETED','CANCELLED','EXPIRED')
                AND parts_cleaned=FALSE AND closed_at<? ORDER BY closed_at LIMIT 20
                """,SESSION,now.minusHours(1));
        for (var s:waiting) {
            try { storage.removePrefix(s.bucket(),s.prefix()); transactions.markCleaned(s.id()); }
            catch (RuntimeException e) { log.warn("等待重试清理分片任务 {}",s.id()); }
        }
        jdbc.update("DELETE FROM file_upload_cancel WHERE created_at<?",now.minusDays(30));
        jdbc.update("""
                DELETE FROM file_upload_session WHERE parts_cleaned=TRUE
                AND status IN ('COMPLETED','CANCELLED','EXPIRED') AND closed_at<?
                """,now.minusDays(30));
    }
}
