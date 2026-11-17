package com.airuike.netdisk.backend.service;
import com.airuike.netdisk.backend.dto.FileDtos.Node;
import com.airuike.netdisk.backend.entity.FileNode;
import com.airuike.netdisk.backend.mapper.FileNodeMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.airuike.netdisk.backend.service.FileRules.*;

@Service
public class FileTreeService {
    private final FileNodeMapper nodes;
    private final FileAccess access;
    private final JdbcTemplate jdbc;
    public FileTreeService(FileNodeMapper nodes,FileAccess access,JdbcTemplate jdbc) {
        this.nodes=nodes; this.access=access; this.jdbc=jdbc;
    }
    @Transactional
    public Node createFolder(long userId,long parentId,String raw) {
        access.checkUser(userId,true); access.parent(userId,parentId);
        if(access.breadcrumbs(userId,parentId).size()>FileAccess.MAX_DEPTH)
            throw fail(HttpStatus.CONFLICT,"最多支持 64 层文件夹");
        String name=name(raw); access.unique(userId,parentId,name,null);
        FileNode n=new FileNode(); n.setUserId(userId); n.setParentId(parentId); n.setName(name);
        n.setType("FOLDER"); n.setCategory("FOLDER"); n.setSize(0L); n.setDeleted(false);
        n.setCreatedAt(LocalDateTime.now()); n.setUpdatedAt(n.getCreatedAt()); nodes.insert(n);
        return Node.of(n,null);
    }
    @Transactional
    public Node rename(long userId,long id,String raw) {
        access.checkUser(userId,true); FileNode n=access.require(userId,id,false);
        String value=name(raw); access.unique(userId,n.getParentId(),value,id);
        // 只允许修改基本名，保留文件扩展名，避免伪造预览类型。
        if(n.getType().equals("FILE") && !extension(value).equals(extension(n.getName())))
            throw fail(HttpStatus.BAD_REQUEST,"重命名时请保留原文件扩展名");
        n.setName(value); n.setUpdatedAt(LocalDateTime.now()); nodes.updateById(n); return Node.of(n,null);
    }
    @Transactional
    public Node move(long userId,long id,long parentId) {
        access.checkUser(userId,true); FileNode n=access.require(userId,id,false);
        access.parent(userId,parentId); if(n.getParentId()==parentId) return Node.of(n,null);
        List<FileNode> subtree=descendants(userId,n,false);
        if(subtree.stream().anyMatch(item->item.getId()==parentId))
            throw fail(HttpStatus.CONFLICT,"不能移动到自身或子文件夹");
        int parentDepth=access.breadcrumbs(userId,parentId).size()-1;
        Map<Long,Integer> depths=new HashMap<>(); depths.put(id,n.getType().equals("FOLDER")?1:0);
        int maxDepth=depths.get(id);
        for(FileNode item:subtree) {
            if(item.getId()==id) continue;
            int depth=depths.getOrDefault(item.getParentId(),0)+(item.getType().equals("FOLDER")?1:0);
            depths.put(item.getId(),depth); maxDepth=Math.max(maxDepth,depth);
        }
        if(parentDepth+maxDepth>FileAccess.MAX_DEPTH) throw fail(HttpStatus.CONFLICT,"移动后目录超过 64 层");
        access.unique(userId,parentId,n.getName(),id);
        n.setParentId(parentId); n.setUpdatedAt(LocalDateTime.now()); nodes.updateById(n); return Node.of(n,null);
    }
    @Transactional
    public void trash(long userId,long id) {
        access.checkUser(userId,true); FileNode root=access.require(userId,id,false);
        LocalDateTime now=LocalDateTime.now();
        for(FileNode n:descendants(userId,root,false)) {
            n.setDeleted(true); n.setTrashRootId(id); n.setDeletedAt(now); n.setUpdatedAt(now); nodes.updateById(n);
            jdbc.update("UPDATE share_link SET revoked=TRUE WHERE user_id=? AND node_id=? AND revoked=FALSE",userId,n.getId());
        }
    }
    @Transactional
    public Node restore(long userId,long id) {
        access.checkUser(userId,true); FileNode root=trashRoot(userId,id);
        long parentId=root.getParentId();
        if(parentId!=0 && nodes.selectCount(access.owned(userId).eq("id",parentId)
            .eq("type","FOLDER").eq("deleted",false))==0) parentId=0;
        List<FileNode> group=nodes.selectList(access.owned(userId).eq("deleted",true).eq("trash_root_id",id));
        if(group.size()>10000) throw fail(HttpStatus.CONFLICT,"单次最多操作 10000 个目录项");
        access.parent(userId,parentId);
        if(access.breadcrumbs(userId,parentId).size()-1+restoreDepth(group,id)>FileAccess.MAX_DEPTH) parentId=0;
        access.unique(userId,parentId,root.getName(),null);
        LocalDateTime now=LocalDateTime.now();
        for(FileNode n:group) {
            if(n.getId()==id) n.setParentId(parentId);
            n.setDeleted(false); n.setDeletedAt(null); n.setTrashRootId(null); n.setUpdatedAt(now);
            // updateById 默认不更新 null，使用 JDBC 明确清空回收站字段。
            jdbc.update("UPDATE file_node SET parent_id=?, deleted=FALSE, deleted_at=NULL, trash_root_id=NULL, updated_at=? WHERE id=? AND user_id=?",
                n.getParentId(),now,n.getId(),userId);
        }
        return Node.of(access.require(userId,id,false),null);
    }
    @Transactional
    public void purge(long userId,long id) {
        access.checkUser(userId,true); FileNode root=trashRoot(userId,id);
        List<FileNode> all=descendants(userId,root,true);
        if(all.stream().anyMatch(n->!n.getDeleted())) throw fail(HttpStatus.CONFLICT,"目录中存在有效文件，请刷新重试");
        Map<Long,Integer> refs=new TreeMap<>();
        for(FileNode n:all) { if(n.getObjectId()!=null) refs.merge(n.getObjectId(),1,Integer::sum); nodes.deleteById(n.getId()); }
        for(var e:refs.entrySet()) {
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT reference_count FROM file_object WHERE id=? FOR UPDATE",e.getKey());
            if(rows.isEmpty()) throw fail(HttpStatus.CONFLICT,"文件对象元数据缺失");
            int count=((Number)rows.get(0).get("reference_count")).intValue()-e.getValue();
            if(count<0) throw fail(HttpStatus.CONFLICT,"文件引用计数异常");
            jdbc.update("UPDATE file_object SET reference_count=?, status=? WHERE id=?",count,count==0?"DELETING":"ACTIVE",e.getKey());
        }
    }
    private FileNode trashRoot(long userId,long id) {
        FileNode n=access.require(userId,id,true);
        if(!Objects.equals(n.getTrashRootId(),id)) throw fail(HttpStatus.NOT_FOUND,"此项已包含在被删除的文件夹内");
        return n;
    }
    private int restoreDepth(List<FileNode> group,long rootId) {
        Map<Long,FileNode> byId=new HashMap<>();
        group.forEach(n->byId.put(n.getId(),n));
        int max=0;
        for(FileNode item:group) {
            int depth=0, hops=0;
            FileNode current=item;
            while(current!=null) {
                if(current.getType().equals("FOLDER")) depth++;
                if(current.getId()==rootId) break;
                if(++hops>FileAccess.MAX_DEPTH) throw fail(HttpStatus.CONFLICT,"回收站目录结构异常");
                current=byId.get(current.getParentId());
            }
            max=Math.max(max,depth);
        }
        return max;
    }
    private List<FileNode> descendants(long userId,FileNode root,boolean includeDeleted) {
        List<FileNode> all=new ArrayList<>(); all.add(root);
        List<Long> frontier=List.of(root.getId()); Set<Long> seen=new HashSet<>(frontier);
        for(int depth=0;!frontier.isEmpty();depth++) {
            if(depth>FileAccess.MAX_DEPTH) throw fail(HttpStatus.CONFLICT,"目录结构异常");
            List<Long> next=new ArrayList<>();
            for(int start=0; start<frontier.size(); start+=200) {
                var q=access.owned(userId).in("parent_id",frontier.subList(start,Math.min(start+200,frontier.size())));
                if(!includeDeleted) q.eq("deleted",false);
                for(FileNode n:nodes.selectList(q)) {
                    if(!seen.add(n.getId())) throw fail(HttpStatus.CONFLICT,"目录结构存在循环");
                    all.add(n); if(all.size()>10000) throw fail(HttpStatus.CONFLICT,"单次最多操作 10000 个目录项");
                    if(n.getType().equals("FOLDER")) next.add(n.getId());
                }
            }
            frontier=next;
        }
        return all;
    }
}
