package com.airuike.netdisk.backend.service;
import com.airuike.netdisk.backend.entity.FileNode;
import com.airuike.netdisk.backend.mapper.FileNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import static com.airuike.netdisk.backend.service.FileRules.fail;
import com.airuike.netdisk.backend.dto.FileDtos.Crumb;

@Component
public class FileAccess {
    public static final int MAX_DEPTH = 64;
    private final FileNodeMapper nodes;
    private final JdbcTemplate jdbc;
    public FileAccess(FileNodeMapper nodes, JdbcTemplate jdbc) { this.nodes=nodes; this.jdbc=jdbc; }
    public long userId(Jwt jwt) {
        try { long id=Long.parseLong(jwt.getSubject()); if (id>0) return id; }
        catch (NumberFormatException ignored) {}
        throw fail(HttpStatus.UNAUTHORIZED, "登录状态无效，请重新登录");
    }
    public void checkUser(long userId, boolean lock) {
        List<Map<String,Object>> rows=jdbc.queryForList(
            "SELECT id, status FROM sys_user WHERE id=?" + (lock ? " FOR UPDATE" : ""), userId);
        if (rows.isEmpty() || ((Number)rows.get(0).get("status")).intValue()!=1)
            throw fail(HttpStatus.UNAUTHORIZED, "账号已失效，请重新登录");
    }
    public QueryWrapper<FileNode> owned(long userId) { return new QueryWrapper<FileNode>().eq("user_id",userId); }
    public FileNode require(long userId, long id, boolean deleted) {
        FileNode n=nodes.selectOne(owned(userId).eq("id",id).eq("deleted",deleted));
        if(n==null) throw fail(HttpStatus.NOT_FOUND,"文件或文件夹不存在");
        return n;
    }
    public void parent(long userId, long id) {
        if(id==0) return;
        FileNode n=require(userId,id,false);
        if(!n.getType().equals("FOLDER")) throw fail(HttpStatus.BAD_REQUEST,"目标不是文件夹");
        breadcrumbs(userId,id);
    }
    public List<Crumb> breadcrumbs(long userId, long id) {
        List<Crumb> items=new ArrayList<>();
        while(id!=0) {
            if(items.size()>=MAX_DEPTH) throw fail(HttpStatus.CONFLICT,"目录层级超过 64 层或存在循环");
            FileNode n=require(userId,id,false);
            if(!n.getType().equals("FOLDER")) throw fail(HttpStatus.BAD_REQUEST,"目标不是文件夹");
            items.add(new Crumb(n.getId().toString(),n.getName())); id=n.getParentId();
        }
        items.add(new Crumb("0","全部文件")); Collections.reverse(items); return items;
    }
    public String originalPath(long userId, long parentId) {
        List<String> parts=new ArrayList<>(); int depth=0;
        while(parentId!=0 && depth++<MAX_DEPTH) {
            FileNode n=nodes.selectOne(owned(userId).eq("id",parentId));
            if(n==null) break;
            parts.add(n.getName()); parentId=n.getParentId();
        }
        Collections.reverse(parts);
        return "全部文件" + (parts.isEmpty()?"":" / "+String.join(" / ",parts));
    }
    public void unique(long userId,long parentId,String name,Long excludeId) {
        var query=owned(userId).eq("parent_id",parentId).eq("deleted",false).eq("name",name);
        if(excludeId!=null) query.ne("id",excludeId);
        if(nodes.selectCount(query)>0) throw fail(HttpStatus.CONFLICT,"此位置已有同名文件或文件夹");
    }
}
