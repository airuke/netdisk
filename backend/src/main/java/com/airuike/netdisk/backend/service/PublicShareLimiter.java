package com.airuike.netdisk.backend.service;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import static com.airuike.netdisk.backend.service.FileRules.fail;

@Component
public class PublicShareLimiter {
    private record Bucket(long until,int count) {}
    private final Map<String,Bucket> buckets=new HashMap<>();
    // 使用服务直接观察的地址，不信任客户端伪造的 X-Forwarded-For。
    public synchronized void check(String address,String operation,int limit) {
        long now=System.currentTimeMillis();
        if (buckets.size()>10000) buckets.entrySet().removeIf(e->e.getValue().until()<=now);
        String key=address+":"+operation; Bucket b=buckets.get(key);
        if (b==null || b.until()<=now) {
            if (buckets.size()>=20000) throw fail(HttpStatus.TOO_MANY_REQUESTS,"访问较多，请稍后再试");
            b=new Bucket(now+60000,0);
        }
        if (b.count()>=limit) throw fail(HttpStatus.TOO_MANY_REQUESTS,"操作过于频繁，请一分钟后再试");
        buckets.put(key,new Bucket(b.until(),b.count()+1));
    }
}