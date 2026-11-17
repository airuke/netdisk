package com.airuike.netdisk.backend;
import com.airuike.netdisk.backend.storage.ObjectStorage;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MemoryStorageConfig {
    @Bean @Primary
    public MemoryStorage memoryStorage() { return new MemoryStorage(); }
    public static class MemoryStorage implements ObjectStorage {
        public final ConcurrentHashMap<String,byte[]> data=new ConcurrentHashMap<>();
        public volatile boolean failPut=false, failRemove=false;
        public volatile java.util.function.Consumer<String> beforePut;
        public final java.util.concurrent.atomic.AtomicInteger putCalls=new java.util.concurrent.atomic.AtomicInteger();
        @Override public void put(String bucket,String key,InputStream input,long size,String type) {
            putCalls.incrementAndGet();
            if(beforePut!=null) beforePut.accept(key);
            if(failPut) throw new IllegalStateException("simulated storage outage");
            try { data.put(bucket+"/"+key,input.readAllBytes()); } catch(IOException e) { throw new UncheckedIOException(e); }
        }
        @Override public InputStream open(String bucket,String key,long offset,long length) {
            byte[] bytes=data.get(bucket+"/"+key);
            if(bytes==null) throw new IllegalStateException("object missing");
            return new ByteArrayInputStream(Arrays.copyOfRange(bytes,(int)offset,(int)Math.min(bytes.length,offset+length)));
        }
        @Override public void removePrefix(String bucket,String prefix) {
            if(failRemove) throw new IllegalStateException("simulated cleanup outage");
            data.keySet().removeIf(key -> key.startsWith(bucket+"/"+prefix));
        }
        @Override public void remove(String bucket,String key) {
            if(failRemove) throw new IllegalStateException("simulated cleanup outage");
            data.remove(bucket+"/"+key);
        }
    }
}
