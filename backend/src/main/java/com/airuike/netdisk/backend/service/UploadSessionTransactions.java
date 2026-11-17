package com.airuike.netdisk.backend.service;

import com.airuike.netdisk.backend.config.StorageProperties;
import com.airuike.netdisk.backend.config.UploadProperties;
import com.airuike.netdisk.backend.dto.FileDtos;
import com.airuike.netdisk.backend.dto.UploadDtos;
import com.airuike.netdisk.backend.mapper.FileNodeMapper;
import com.airuike.netdisk.backend.mapper.FileObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.airuike.netdisk.backend.service.FileRules.*;
import static com.airuike.netdisk.backend.service.UploadRecords.*;

@Service
public class UploadSessionTransactions {
    private final JdbcTemplate jdbc;
    private final FileAccess access;
    private final UploadTransactionService files;
    private final FileNodeMapper nodes;
    private final FileObjectMapper objects;
    private final UploadProperties props;
    private final StorageProperties storage;
    public UploadSessionTransactions(JdbcTemplate jdbc, FileAccess access, UploadTransactionService files,
            FileNodeMapper nodes, FileObjectMapper objects, UploadProperties props, StorageProperties storage) {
        this.jdbc=jdbc; this.access=access; this.files=files; this.nodes=nodes;
        this.objects=objects; this.props=props; this.storage=storage;
    }
    private Session owned(long userId, String id, boolean lock) {
        var rows=jdbc.query("SELECT * FROM file_upload_session WHERE user_id=? AND id=?"+
                (lock ? " FOR UPDATE" : ""), SESSION, userId, id);
        if (rows.isEmpty()) throw fail(HttpStatus.NOT_FOUND, "上传任务不存在");
        return rows.get(0);
    }
    private Session byClient(long userId, String id) {
        var rows=jdbc.query("SELECT * FROM file_upload_session WHERE user_id=? AND client_id=? FOR UPDATE",
                SESSION, userId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    private void validate(UploadDtos.Create body) {
        name(body.name()); HashSupport.hash(body.sha256());
        if (body.size()<0 || body.size()>props.maxFileSize())
            throw fail(HttpStatus.PAYLOAD_TOO_LARGE, "单文件超过上传大小限制");
    }
    private void sameRequest(Session s, UploadDtos.Create b) {
        if (s.parentId()!=b.parentId() || s.size()!=b.size() || !s.name().equals(name(b.name()))
                || !s.hash().equals(HashSupport.hash(b.sha256())))
            throw fail(HttpStatus.CONFLICT, "上传请求标识已用于其他文件，请新建上传任务");
    }
    private void notCancelled(long userId, String clientId) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM file_upload_cancel WHERE user_id=? AND client_id=?",
                Long.class, userId, clientId);
        if (count!=null && count>0) throw fail(HttpStatus.GONE, "此上传请求已取消，请重新创建任务");
    }
    private Session insert(long userId, UploadDtos.Create b, String status, Long nodeId, boolean instant) {
        String id=UUID.randomUUID().toString(); LocalDateTime now=LocalDateTime.now();
        int count=(int)((b.size()+props.chunkSize()-1)/props.chunkSize());
        jdbc.update("""
                INSERT INTO file_upload_session(id,user_id,client_id,parent_id,name,size,sha256,
                chunk_size,chunk_count,bucket,object_prefix,status,result_node_id,instant,
                created_at,updated_at,expires_at,closed_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id,userId,b.clientId(),b.parentId(),name(b.name()),b.size(),HashSupport.hash(b.sha256()),
                props.chunkSize(),count,storage.bucket(),"uploads/"+userId+"/"+id+"/",status,nodeId,
                instant,now,now,now.plusHours(props.sessionTtlHours()),status.equals("COMPLETED")?now:null);
        return owned(userId,id,false);
    }
    @Transactional
    public UploadDtos.Instant instant(long userId, UploadDtos.Create b) {
        validate(b); access.checkUser(userId,true);
        Session existing=byClient(userId,b.clientId());
        if (existing!=null) {
            sameRequest(existing,b);
            return existing.status().equals("COMPLETED")
                    ? new UploadDtos.Instant(true,view(existing)) : new UploadDtos.Instant(false,null);
        }
        notCancelled(userId,b.clientId());
        var n=files.instant(userId,b.parentId(),name(b.name()),b.size(),HashSupport.hash(b.sha256()));
        if (n==null) return new UploadDtos.Instant(false,null);
        return new UploadDtos.Instant(true,view(insert(userId,b,"COMPLETED",n.getId(),true)));
    }
    @Transactional
    public UploadDtos.Session create(long userId, UploadDtos.Create b) {
        validate(b); access.checkUser(userId,true);
        Session existing=byClient(userId,b.clientId());
        if (existing!=null) { sameRequest(existing,b); return view(recover(existing)); }
        notCancelled(userId,b.clientId());
        if (b.size()==0) throw fail(HttpStatus.BAD_REQUEST, "空文件请使用普通上传");
        access.parent(userId,b.parentId()); access.unique(userId,b.parentId(),name(b.name()),null);
        Long count=jdbc.queryForObject("""
                SELECT COUNT(*) FROM file_upload_session WHERE user_id=?
                AND status IN ('OPEN','MERGING') AND expires_at>?
                """,Long.class,userId,LocalDateTime.now());
        if (count!=null && count>=100) throw fail(HttpStatus.TOO_MANY_REQUESTS, "未完成任务较多，请先取消不需要的任务");
        return view(insert(userId,b,"OPEN",null,false));
    }
    @Transactional
    public UploadDtos.Session info(long userId,String id) {
        access.checkUser(userId,true); return view(recover(owned(userId,id,true)));
    }
    public List<UploadDtos.Session> list(long userId) {
        access.checkUser(userId,false);
        return jdbc.query("""
                SELECT * FROM file_upload_session WHERE user_id=? AND
                (status IN ('OPEN','MERGING') OR (status='COMPLETED' AND closed_at>?))
                ORDER BY CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END,updated_at DESC LIMIT 200
                """,SESSION,userId,LocalDateTime.now().minusDays(7)).stream().map(this::view).toList();
    }
    private Session recover(Session s) {
        LocalDateTime now=LocalDateTime.now();
        if (s.status().equals("MERGING") && s.mergeLeaseUntil()!=null && s.mergeLeaseUntil().isBefore(now)) {
            if (s.outputObjectId()!=null) files.abort(s.outputObjectId());
            jdbc.update("""
                    UPDATE file_upload_session SET status='OPEN',merge_token=NULL,merge_lease_until=NULL,
                    output_object_id=NULL,updated_at=? WHERE id=?
                    """,now,s.id());
            s=owned(s.userId(),s.id(),false);
        }
        if (s.status().equals("OPEN") && !s.expiresAt().isAfter(now)) {
            jdbc.update("UPDATE file_upload_session SET status='EXPIRED',closed_at=?,updated_at=? WHERE id=?",now,now,s.id());
            s=owned(s.userId(),s.id(),false);
        }
        return s;
    }
    private void open(Session s) {
        if (s.status().equals("MERGING")) throw fail(HttpStatus.CONFLICT, "服务器正在合并文件，请稍后继续");
        if (!s.status().equals("OPEN") || !s.expiresAt().isAfter(LocalDateTime.now()))
            throw fail(HttpStatus.GONE, "上传任务已取消或过期，请重新上传");
    }
    private void touch(Session s) {
        var now=LocalDateTime.now();
        jdbc.update("UPDATE file_upload_session SET updated_at=?,expires_at=? WHERE id=?",
                now,now.plusHours(props.sessionTtlHours()),s.id());
    }
    @Transactional
    public WritePlan beginPart(long userId,String id,int index,long size,String hash) {
        access.checkUser(userId,true); Session s=recover(owned(userId,id,true)); open(s);
        hash=HashSupport.hash(hash);
        long expected=index==s.chunkCount()-1?s.size()-(long)index*s.chunkSize():s.chunkSize();
        if (index<0 || index>=s.chunkCount() || size!=expected)
            throw fail(HttpStatus.BAD_REQUEST, "分片序号或大小无效");
        var parts=jdbc.query("SELECT * FROM file_upload_part WHERE session_id=? AND part_index=? FOR UPDATE",PART,id,index);
        if (!parts.isEmpty()) {
            Part p=parts.get(0);
            if (p.status().equals("READY")) {
                if (!p.hash().equals(hash)) throw fail(HttpStatus.CONFLICT, "此分片与已保存内容不同，请重新创建任务");
                touch(s); return new WritePlan(s,p,true);
            }
            if (p.status().equals("WRITING") && p.leaseUntil()!=null && p.leaseUntil().isAfter(LocalDateTime.now()))
                throw fail(HttpStatus.CONFLICT, "此分片正在保存，请稍后重试");
        }
        String attempt=UUID.randomUUID().toString();
        Part p=new Part(index,size,hash,s.prefix()+"parts/"+index+"-"+attempt,attempt,"WRITING",LocalDateTime.now().plusMinutes(10));
        if (parts.isEmpty()) jdbc.update("""
                INSERT INTO file_upload_part(session_id,part_index,size,sha256,object_key,attempt,status,lease_until)
                VALUES(?,?,?,?,?,?,?,?)
                """,id,index,size,hash,p.objectKey(),attempt,p.status(),p.leaseUntil());
        else jdbc.update("""
                UPDATE file_upload_part SET size=?,sha256=?,object_key=?,attempt=?,status='WRITING',lease_until=?
                WHERE session_id=? AND part_index=?
                """,size,hash,p.objectKey(),attempt,p.leaseUntil(),id,index);
        touch(s); return new WritePlan(s,p,false);
    }
    @Transactional
    public UploadDtos.Part finishPart(long userId,WritePlan plan,String actual) {
        access.checkUser(userId,true); Session s=owned(userId,plan.session().id(),true); open(s);
        if (!HashSupport.matches(actual,plan.part().hash())) throw fail(HttpStatus.BAD_REQUEST, "分片校验失败，请重试");
        int changed=jdbc.update("""
                UPDATE file_upload_part SET status='READY',lease_until=NULL WHERE session_id=?
                AND part_index=? AND attempt=? AND status='WRITING'
                """,s.id(),plan.part().index(),plan.part().attempt());
        if (changed!=1) throw fail(HttpStatus.CONFLICT, "分片请求已被替换，请同步任务后重试");
        touch(s); return new UploadDtos.Part(plan.part().index(),plan.part().size(),actual);
    }
    @Transactional
    public void failPart(WritePlan plan) {
        jdbc.update("""
                UPDATE file_upload_part SET status='FAILED',lease_until=NULL
                WHERE session_id=? AND part_index=? AND attempt=? AND status='WRITING'
                """,plan.session().id(),plan.part().index(),plan.part().attempt());
    }
    @Transactional
    public MergePlan beginMerge(long userId,String id) {
        access.checkUser(userId,true); Session s=recover(owned(userId,id,true));
        if (s.status().equals("COMPLETED")) return new MergePlan(s,List.of(),null,true);
        open(s); access.parent(userId,s.parentId()); access.unique(userId,s.parentId(),s.name(),null);
        List<Part> parts=jdbc.query("SELECT * FROM file_upload_part WHERE session_id=? ORDER BY part_index",PART,id);
        if (parts.size()!=s.chunkCount()) throw fail(HttpStatus.CONFLICT, "仍有分片未上传完成");
        for (int i=0;i<parts.size();i++) {
            Part p=parts.get(i);
            long size=i==s.chunkCount()-1?s.size()-(long)i*s.chunkSize():s.chunkSize();
            if (p.index()!=i || !p.status().equals("READY") || p.size()!=size)
                throw fail(HttpStatus.CONFLICT, "仍有分片未保存完成");
        }
        var object=files.stage(userId,s.name(),s.size()); String token=UUID.randomUUID().toString();
        var now=LocalDateTime.now();
        jdbc.update("""
                UPDATE file_upload_session SET status='MERGING',output_object_id=?,merge_token=?,
                merge_lease_until=?,updated_at=?,expires_at=? WHERE id=?
                """,object.getId(),token,now.plusMinutes(props.mergeLeaseMinutes()),now,
                now.plusHours(props.sessionTtlHours()),id);
        return new MergePlan(owned(userId,id,false),parts,object,false);
    }
    @Transactional
    public UploadDtos.Session finishMerge(long userId,MergePlan plan,String actual) {
        access.checkUser(userId,true); Session s=owned(userId,plan.session().id(),true);
        if (s.status().equals("COMPLETED")) return view(s);
        if (!s.status().equals("MERGING") || !java.util.Objects.equals(s.mergeToken(),plan.session().mergeToken()))
            throw fail(HttpStatus.CONFLICT, "合并请求已失效，请同步任务状态");
        if (!HashSupport.matches(s.hash(),actual)) throw fail(HttpStatus.BAD_REQUEST, "完整文件校验失败，请重新上传");
        plan.object().setHash(actual);
        var node=files.complete(userId,s.parentId(),s.name(),plan.object()); var now=LocalDateTime.now();
        jdbc.update("""
                UPDATE file_upload_session SET status='COMPLETED',result_node_id=?,merge_token=NULL,
                merge_lease_until=NULL,updated_at=?,closed_at=? WHERE id=?
                """,node.getId(),now,now,s.id());
        return view(owned(userId,s.id(),false));
    }
    @Transactional
    public void failMerge(MergePlan plan,boolean corrupt) {
        Session s=owned(plan.session().userId(),plan.session().id(),true);
        files.abort(plan.object().getId());
        if (!s.status().equals("MERGING") || !java.util.Objects.equals(s.mergeToken(),plan.session().mergeToken())) return;
        var now=LocalDateTime.now();
        jdbc.update("""
                UPDATE file_upload_session SET status=?,output_object_id=NULL,merge_token=NULL,
                merge_lease_until=NULL,updated_at=?,closed_at=? WHERE id=?
                """,corrupt?"CANCELLED":"OPEN",now,corrupt?now:null,s.id());
    }
    @Transactional
    public UploadDtos.Session cancel(long userId,String clientId) {
        if (!clientId.matches("[A-Za-z0-9_-]{16,64}")) throw fail(HttpStatus.BAD_REQUEST,"请求标识无效");
        access.checkUser(userId,true); Session s=byClient(userId,clientId);
        if (s!=null && s.status().equals("COMPLETED")) return view(s);
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM file_upload_cancel WHERE user_id=? AND client_id=?",
                Long.class,userId,clientId);
        if (count!=null && count==0) jdbc.update("INSERT INTO file_upload_cancel(user_id,client_id,created_at) VALUES(?,?,?)",
                userId,clientId,LocalDateTime.now());
        if (s==null) return null;
        if (s.outputObjectId()!=null) files.abort(s.outputObjectId());
        var now=LocalDateTime.now();
        jdbc.update("""
                UPDATE file_upload_session SET status='CANCELLED',merge_token=NULL,merge_lease_until=NULL,
                updated_at=?,closed_at=? WHERE id=?
                """,now,now,s.id());
        return view(owned(userId,s.id(),false));
    }
    @Transactional
    public void recoverForCleanup(String id,long userId) { recover(owned(userId,id,true)); }
    @Transactional
    public void markCleaned(String id) {
        int changed=jdbc.update("""
                UPDATE file_upload_session SET parts_cleaned=TRUE WHERE id=?
                AND status IN ('COMPLETED','CANCELLED','EXPIRED')
                """,id);
        if (changed==1) jdbc.update("DELETE FROM file_upload_part WHERE session_id=?",id);
    }
    public UploadDtos.Session view(Session s) {
        var parts=jdbc.query("""
                SELECT * FROM file_upload_part WHERE session_id=? AND status='READY' ORDER BY part_index
                """,PART,s.id()).stream().map(p->new UploadDtos.Part(p.index(),p.size(),p.hash())).toList();
        var n=s.resultNodeId()==null?null:nodes.selectById(s.resultNodeId());
        FileDtos.Node node=n==null || n.getDeleted() || n.getUserId()!=s.userId()?null:FileDtos.Node.of(n,null);
        return new UploadDtos.Session(s.id(),s.clientId(),s.name(),Long.toString(s.parentId()),
                access.originalPath(s.userId(),s.parentId()),s.size(),s.hash(),s.chunkSize(),s.chunkCount(),
                s.status(),parts,s.expiresAt(),s.mergeLeaseUntil(),s.instant(),node);
    }
}
