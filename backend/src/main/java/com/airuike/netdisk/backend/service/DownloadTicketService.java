package com.airuike.netdisk.backend.service;
import com.airuike.netdisk.backend.dto.FileDtos.Ticket;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import static com.airuike.netdisk.backend.service.FileRules.fail;

@Service
public class DownloadTicketService {
    public record Grant(long userId,long nodeId,long expiresAt) {}
    private final ConcurrentHashMap<String,Grant> tickets=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    private final Clock clock=Clock.systemUTC();
    private final FileAccess access;
    public DownloadTicketService(FileAccess access) { this.access=access; }
    public synchronized Ticket issue(long userId,long nodeId) {
        access.checkUser(userId,false); var node=access.require(userId,nodeId,false);
        if(!node.getType().equals("FILE")) throw fail(HttpStatus.BAD_REQUEST,"文件夹不能直接下载");
        long now=clock.millis(); tickets.entrySet().removeIf(e->e.getValue().expiresAt()<=now);
        if(tickets.size()>=1000) throw fail(HttpStatus.TOO_MANY_REQUESTS,"下载请求较多，请稍后再试");
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(token,new Grant(userId,nodeId,now+60000));
        return new Ticket("/api/downloads/"+token,60);
    }
    public Grant resolve(String token) {
        Grant grant=tickets.get(token);
        if(grant==null || grant.expiresAt()<=clock.millis()) {
            tickets.remove(token); throw fail(HttpStatus.NOT_FOUND,"下载链接已过期，请重新点击下载");
        }
        // 每次读取都检查账号和文件，删除后的链接立即失效。
        access.checkUser(grant.userId(),false); access.require(grant.userId(),grant.nodeId(),false);
        return grant;
    }
}
