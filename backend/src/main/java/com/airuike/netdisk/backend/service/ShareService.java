package com.airuike.netdisk.backend.service;

import com.airuike.netdisk.backend.dto.FileDtos;
import com.airuike.netdisk.backend.dto.ShareDtos;
import com.airuike.netdisk.backend.entity.FileNode;
import com.airuike.netdisk.backend.mapper.FileNodeMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.airuike.netdisk.backend.service.FileRules.fail;

@Service
public class ShareService {
    public record Link(long id,long userId,long nodeId,String code,String extractionHash,
            LocalDateTime expiresAt,boolean revoked,LocalDateTime createdAt) {}
    public record Grant(String code,long until) {}
    public record Download(String accessToken,long nodeId,long until) {}
    private static final RowMapper<Link> LINK=(r,n)->new Link(r.getLong("id"),r.getLong("user_id"),
            r.getLong("node_id"),r.getString("code"),r.getString("extraction_hash"),
            r.getTimestamp("expires_at")==null?null:r.getTimestamp("expires_at").toLocalDateTime(),
            r.getBoolean("revoked"),r.getTimestamp("created_at").toLocalDateTime());
    private final JdbcTemplate jdbc;
    private final FileAccess files;
    private final FileNodeMapper nodes;
    private final PasswordEncoder passwords;
    private final SecureRandom random=new SecureRandom();
    private final ConcurrentHashMap<String,Grant> grants=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Download> downloads=new ConcurrentHashMap<>();
    public ShareService(JdbcTemplate jdbc,FileAccess files,FileNodeMapper nodes,PasswordEncoder passwords) {
        this.jdbc=jdbc; this.files=files; this.nodes=nodes; this.passwords=passwords;
    }
    private String randomToken(int size) {
        byte[] bytes=new byte[size]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private Link require(String code) {
        if (code==null || !code.matches("[A-Za-z0-9_-]{24}")) throw fail(HttpStatus.NOT_FOUND,"分享链接无效");
        var rows=jdbc.query("SELECT * FROM share_link WHERE code=?",LINK,code);
        if (rows.isEmpty()) throw fail(HttpStatus.NOT_FOUND,"分享不存在或已失效");
        Link link=rows.get(0);
        if (link.revoked() || (link.expiresAt()!=null && !link.expiresAt().isAfter(LocalDateTime.now())))
            throw fail(HttpStatus.GONE,"分享已取消或过期");
        try { files.checkUser(link.userId(),false); files.require(link.userId(),link.nodeId(),false); }
        catch (com.airuike.netdisk.backend.exception.BusinessException e) {
            throw fail(HttpStatus.GONE,"分享内容已不可用");
        }
        return link;
    }
    private ShareDtos.Item item(Link link) {
        FileNode n=nodes.selectById(link.nodeId());
        boolean available=n!=null && n.getUserId()==link.userId() && !n.getDeleted();
        String status=link.revoked()?"REVOKED":link.expiresAt()!=null && !link.expiresAt().isAfter(LocalDateTime.now())
                ?"EXPIRED":available?"ACTIVE":"UNAVAILABLE";
        return new ShareDtos.Item(Long.toString(link.id()),link.code(),"/s/"+link.code(),
                n==null?"源文件已删除":n.getName(),n==null?"FILE":n.getType(),n==null?0:n.getSize(),
                link.extractionHash()!=null,status,link.expiresAt(),link.createdAt());
    }
    @Transactional
    public ShareDtos.Created create(long userId,ShareDtos.Create b) {
        files.checkUser(userId,true); files.require(userId,b.nodeId(),false);
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM share_link WHERE user_id=? AND revoked=FALSE",Long.class,userId);
        if (count!=null && count>=1000) throw fail(HttpStatus.TOO_MANY_REQUESTS,"分享记录较多，请先取消不需要的分享");
        String code=randomToken(18);
        String extraction=b.extractionCode()==null || b.extractionCode().isBlank()?null:b.extractionCode().toUpperCase(Locale.ROOT);
        LocalDateTime now=LocalDateTime.now(); LocalDateTime expires=b.expiresDays()==0?null:now.plusDays(b.expiresDays());
        jdbc.update("""
                INSERT INTO share_link(user_id,node_id,code,extraction_hash,expires_at,revoked,created_at)
                VALUES(?,?,?,?,?,FALSE,?)
                """,userId,b.nodeId(),code,extraction==null?null:passwords.encode(extraction),expires,now);
        Link link=jdbc.query("SELECT * FROM share_link WHERE code=?",LINK,code).get(0);
        return new ShareDtos.Created(item(link),extraction);
    }
    public ShareDtos.Page list(long userId,int page,int size) {
        files.checkUser(userId,false);
        if (page<1 || page>100000 || size<1 || size>100) throw fail(HttpStatus.BAD_REQUEST,"分页参数无效");
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM share_link WHERE user_id=?",Long.class,userId);
        var list=jdbc.query("SELECT * FROM share_link WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                LINK,userId,size,(long)(page-1)*size).stream().map(this::item).toList();
        return new ShareDtos.Page(list,total==null?0:total,page,size);
    }
    @Transactional
    public void revoke(long userId,long id) {
        files.checkUser(userId,true);
        if (jdbc.update("UPDATE share_link SET revoked=TRUE WHERE id=? AND user_id=?",id,userId)==0) {
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM share_link WHERE id=? AND user_id=?",Long.class,id,userId);
            if (count==null || count==0) throw fail(HttpStatus.NOT_FOUND,"分享记录不存在");
        }
    }
    public ShareDtos.Info info(String code) {
        Link link=require(code);
        String owner=jdbc.queryForObject("SELECT nickname FROM sys_user WHERE id=?",String.class,link.userId());
        return new ShareDtos.Info(link.extractionHash()!=null,
                link.extractionHash()==null?files.require(link.userId(),link.nodeId(),false).getName():"受提取码保护的分享",
                owner==null?"网盘用户":owner,link.expiresAt());
    }
    public synchronized ShareDtos.Access unlock(String code,String extraction) {
        Link link=require(code);
        if (link.extractionHash()!=null && (extraction==null
                || !passwords.matches(extraction.toUpperCase(Locale.ROOT),link.extractionHash())))
            throw fail(HttpStatus.FORBIDDEN,"提取码不正确，请重新输入");
        long now=System.currentTimeMillis();
        grants.entrySet().removeIf(e->e.getValue().until()<=now);
        if (grants.size()>=10000) throw fail(HttpStatus.TOO_MANY_REQUESTS,"分享访问较多，请稍后再试");
        long until=now+2*60*60*1000;
        if (link.expiresAt()!=null) until=Math.min(until,link.expiresAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        String token=randomToken(32); grants.put(token,new Grant(code,until));
        return new ShareDtos.Access(token,(int)((until-now)/1000),FileDtos.Node.of(files.require(link.userId(),link.nodeId(),false),null));
    }
    public Link authorize(String code,String token,long nodeId) {
        Grant grant=token==null?null:grants.get(token);
        if (grant==null || grant.until()<=System.currentTimeMillis() || !grant.code().equals(code)) {
            if (token!=null) grants.remove(token);
            throw fail(HttpStatus.FORBIDDEN,"分享访问凭证已失效，请重新验证");
        }
        Link link=require(code); long id=nodeId==0?link.nodeId():nodeId;
        FileNode current=files.require(link.userId(),id,false);
        for (int depth=0;depth<=FileAccess.MAX_DEPTH;depth++) {
            if (current.getId()==link.nodeId()) return link;
            if (current.getParentId()==0) break;
            current=files.require(link.userId(),current.getParentId(),false);
        }
        throw fail(HttpStatus.NOT_FOUND,"文件不在此分享中");
    }
    public FileDtos.Page contents(String code,String token,long folderId,int page,int size) {
        if (page<1 || page>100000 || size<1 || size>100) throw fail(HttpStatus.BAD_REQUEST,"分页参数无效");
        Link link=authorize(code,token,folderId); long id=folderId==0?link.nodeId():folderId;
        FileNode folder=files.require(link.userId(),id,false);
        List<FileDtos.Crumb> crumbs=new ArrayList<>(); FileNode cursor=folder;
        while (true) {
            crumbs.add(new FileDtos.Crumb(cursor.getId().toString(),cursor.getName()));
            if (cursor.getId()==link.nodeId()) break;
            if (crumbs.size()>FileAccess.MAX_DEPTH) throw fail(HttpStatus.CONFLICT,"目录结构异常");
            cursor=files.require(link.userId(),cursor.getParentId(),false);
        }
        Collections.reverse(crumbs);
        if (folder.getType().equals("FILE")) {
            if (id!=link.nodeId()) throw fail(HttpStatus.BAD_REQUEST,"此项目不是文件夹");
            return new FileDtos.Page(page==1?List.of(FileDtos.Node.of(folder,null)):List.of(),1,page,size,crumbs);
        }
        var q=files.owned(link.userId()).eq("deleted",false).eq("parent_id",id);
        long total=nodes.selectCount(q);
        q.orderByAsc("CASE WHEN type='FOLDER' THEN 0 ELSE 1 END","name","id")
                .last("LIMIT "+size+" OFFSET "+((long)(page-1)*size));
        return new FileDtos.Page(nodes.selectList(q).stream().map(n->FileDtos.Node.of(n,null)).toList(),total,page,size,crumbs);
    }
    public synchronized FileDtos.Ticket downloadTicket(String code,String token,long nodeId) {
        Link link=authorize(code,token,nodeId);
        if (!files.require(link.userId(),nodeId,false).getType().equals("FILE"))
            throw fail(HttpStatus.BAD_REQUEST,"请进入文件夹后下载文件");
        long now=System.currentTimeMillis(); downloads.entrySet().removeIf(e->e.getValue().until()<=now);
        if (downloads.size()>=1000) throw fail(HttpStatus.TOO_MANY_REQUESTS,"下载请求较多，请稍后再试");
        String ticket=randomToken(32); downloads.put(ticket,new Download(token,nodeId,now+60000));
        return new FileDtos.Ticket("/api/public/share-downloads/"+ticket,60);
    }
    public Download resolveDownload(String token) {
        Download download=downloads.get(token);
        if (download==null || download.until()<=System.currentTimeMillis()) {
            downloads.remove(token); throw fail(HttpStatus.GONE,"下载链接已过期，请重新点击下载");
        }
        Grant grant=grants.get(download.accessToken());
        if (grant==null) throw fail(HttpStatus.FORBIDDEN,"请重新验证分享");
        authorize(grant.code(),download.accessToken(),download.nodeId()); return download;
    }
    public Link linkForDownload(Download download) {
        Grant grant=grants.get(download.accessToken());
        if (grant==null) throw fail(HttpStatus.FORBIDDEN,"请重新验证分享");
        return authorize(grant.code(),download.accessToken(),download.nodeId());
    }
}
