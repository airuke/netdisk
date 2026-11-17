package com.airuike.netdisk.backend.storage;

import com.airuike.netdisk.backend.service.UploadRecords.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

// 按序逐片读取。任何时刻只打开一个分片，不把大文件装入内存。
public final class JoinedPartsInputStream extends InputStream {
    private final ObjectStorage storage;
    private final String bucket;
    private final List<Part> parts;
    private int index;
    private InputStream current;
    private long remaining;
    private boolean closed;
    public JoinedPartsInputStream(ObjectStorage storage,String bucket,List<Part> parts) {
        this.storage=storage; this.bucket=bucket; this.parts=parts;
    }
    @Override public int read() throws IOException {
        byte[] one=new byte[1]; return read(one,0,1)<0?-1:one[0]&255;
    }
    @Override public int read(byte[] b,int offset,int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset,length,b.length);
        if (closed) throw new IOException("分片流已关闭");
        if (length==0) return 0;
        while (current==null || remaining==0) {
            if (current!=null) { current.close(); current=null; }
            if (index==parts.size()) return -1;
            Part part=parts.get(index++); remaining=part.size();
            current=storage.open(bucket,part.objectKey(),0,remaining);
        }
        int read=current.read(b,offset,(int)Math.min(length,remaining));
        if (read<0) throw new IOException("分片内容不完整");
        remaining-=read; return read;
    }
    @Override public void close() throws IOException {
        closed=true; if (current!=null) { current.close(); current=null; }
    }
}
