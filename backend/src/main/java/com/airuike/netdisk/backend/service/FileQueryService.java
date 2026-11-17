package com.airuike.netdisk.backend.service;
import com.airuike.netdisk.backend.dto.FileDtos.*;
import com.airuike.netdisk.backend.entity.FileNode;
import com.airuike.netdisk.backend.mapper.FileNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.airuike.netdisk.backend.service.FileRules.fail;

@Service
public class FileQueryService {
    private final FileNodeMapper nodes;
    private final FileAccess access;
    private final JdbcTemplate jdbc;
    public FileQueryService(FileNodeMapper nodes, FileAccess access, JdbcTemplate jdbc) {
        this.nodes=nodes; this.access=access; this.jdbc=jdbc;
    }
    public Page list(long userId, long parentId, String view, String keyword,
            String sort, String direction, int page, int pageSize) {
        access.checkUser(userId,false);
        if(parentId<0 || page<1 || page>100000 || pageSize<1 || pageSize>100)
            throw fail(HttpStatus.BAD_REQUEST,"分页或目录参数无效");
        var q=access.owned(userId);
        List<Crumb> crumbs=List.of(new Crumb("0","全部文件"));
        boolean trash=view.equals("trash");
        if(trash) q.eq("deleted",true).apply("id = trash_root_id");
        else {
            q.eq("deleted",false);
            switch(view) {
                case "files" -> { access.parent(userId,parentId); crumbs=access.breadcrumbs(userId,parentId); }
                case "recent" -> q.eq("type","FILE");
                case "image","document","video","audio" -> q.eq("category",view.toUpperCase(java.util.Locale.ROOT)).eq("type","FILE");
                default -> throw fail(HttpStatus.BAD_REQUEST,"文件视图无效");
            }
        }
        String term=keyword==null?"":keyword.strip();
        if(term.length()>100) throw fail(HttpStatus.BAD_REQUEST,"搜索词不能超过 100 个字符");
        if(!term.isEmpty()) {
            String escaped=term.replace("!","!!").replace("%","!%").replace("_","!_");
            q.apply("name LIKE {0} ESCAPE '!'", "%"+escaped+"%");
        } else if(!trash && view.equals("files")) q.eq("parent_id",parentId);
        long total=nodes.selectCount(q);
        String column=switch(sort) {
            case "name" -> "name"; case "size" -> "size";
            case "updatedAt" -> trash?"deleted_at":"updated_at";
            default -> throw fail(HttpStatus.BAD_REQUEST,"排序字段无效");
        };
        if(!direction.equals("asc") && !direction.equals("desc")) throw fail(HttpStatus.BAD_REQUEST,"排序方向无效");
        q.orderByAsc("CASE WHEN type = 'FOLDER' THEN 0 ELSE 1 END")
            .orderBy(true,direction.equals("asc"),column).orderByAsc("id")
            .last("LIMIT "+pageSize+" OFFSET "+((long)(page-1)*pageSize));
        List<Node> items=nodes.selectList(q).stream().map(n->Node.of(n,
            trash || !term.isEmpty() || !view.equals("files") ? access.originalPath(userId,n.getParentId()) : null)).toList();
        return new Page(items,total,page,pageSize,crumbs);
    }
    public Node get(long userId,long id) {
        access.checkUser(userId,false); return Node.of(access.require(userId,id,false),null);
    }
    public Summary summary(long userId) {
        access.checkUser(userId,false);
        Map<String,Object> m=jdbc.queryForMap("""
            SELECT COALESCE(SUM(CASE WHEN deleted=FALSE AND type='FILE' THEN 1 ELSE 0 END),0) AS files,
            COALESCE(SUM(CASE WHEN deleted=FALSE AND type='FOLDER' THEN 1 ELSE 0 END),0) AS folders,
            COALESCE(SUM(CASE WHEN type='FILE' THEN size ELSE 0 END),0) AS bytes,
            COALESCE(SUM(CASE WHEN deleted=TRUE AND id=trash_root_id THEN 1 ELSE 0 END),0) AS trash
            FROM file_node WHERE user_id=?
            """, userId);
        return new Summary(number(m,"files"),number(m,"folders"),number(m,"bytes"),number(m,"trash"));
    }
    private long number(Map<String,Object> m,String key) { return ((Number)m.get(key)).longValue(); }
}
