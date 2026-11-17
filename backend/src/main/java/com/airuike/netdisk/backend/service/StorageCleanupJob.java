package com.airuike.netdisk.backend.service;
import com.airuike.netdisk.backend.entity.FileObject;
import com.airuike.netdisk.backend.mapper.FileObjectMapper;
import com.airuike.netdisk.backend.storage.ObjectStorage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StorageCleanupJob {
    private static final Logger log=LoggerFactory.getLogger(StorageCleanupJob.class);
    private final FileObjectMapper objects;
    private final ObjectStorage storage;
    public StorageCleanupJob(FileObjectMapper objects,ObjectStorage storage) { this.objects=objects; this.storage=storage; }
    @Scheduled(initialDelayString="${netdisk.storage.cleanup-delay-ms:60000}",
        fixedDelayString="${netdisk.storage.cleanup-delay-ms:60000}")
    public void cleanup() {
        objects.update(null,new UpdateWrapper<FileObject>().eq("status","UPLOADING").eq("reference_count",0)
            .lt("created_at",LocalDateTime.now().minusDays(1)).set("status","DELETING"));
        var waiting=objects.selectList(new QueryWrapper<FileObject>().eq("status","DELETING")
            .eq("reference_count",0).and(q->q.isNull("cleanup_after").or().le("cleanup_after",LocalDateTime.now()))
            .orderByAsc("id").last("LIMIT 50"));
        for(var object:waiting) {
            try {
                storage.remove(object.getBucket(),object.getObjectKey());
                objects.delete(new QueryWrapper<FileObject>().eq("id",object.getId())
                    .eq("status","DELETING").eq("reference_count",0));
            } catch(RuntimeException e) { log.warn("等待重试清理文件对象 {}",object.getId()); }
        }
    }
}
