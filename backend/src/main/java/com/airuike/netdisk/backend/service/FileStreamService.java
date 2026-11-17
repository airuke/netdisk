package com.airuike.netdisk.backend.service;
import com.airuike.netdisk.backend.config.StorageProperties;
import com.airuike.netdisk.backend.entity.FileObject;
import com.airuike.netdisk.backend.mapper.FileObjectMapper;
import com.airuike.netdisk.backend.storage.ObjectStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import static com.airuike.netdisk.backend.service.FileRules.*;

@Service
public class FileStreamService {
    private final FileAccess access;
    private final FileObjectMapper objects;
    private final ObjectStorage storage;
    private final StorageProperties props;
    private static final Pattern RANGE=Pattern.compile("bytes=(\\d*)-(\\d*)");
    public FileStreamService(FileAccess access,FileObjectMapper objects,ObjectStorage storage,StorageProperties props) {
        this.access=access; this.objects=objects; this.storage=storage; this.props=props;
    }
    public void stream(long userId,long id,boolean preview,HttpServletRequest request,HttpServletResponse response) throws IOException {
        access.checkUser(userId,false); var node=access.require(userId,id,false);
        if(!node.getType().equals("FILE")) throw fail(HttpStatus.BAD_REQUEST,"文件夹不能预览或直接下载");
        FileObject object=objects.selectById(node.getObjectId());
        if(object==null || !object.getStatus().equals("ACTIVE")) throw fail(HttpStatus.NOT_FOUND,"文件对象不存在");
        String kind=previewKind(node.getName());
        if(preview) {
            if(kind.equals("none")) throw fail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"此格式暂不支持预览，请下载后打开");
            long limit=kind.equals("text")?props.textPreviewMaxSize():props.previewMaxSize();
            if(object.getSize()>limit) throw fail(HttpStatus.PAYLOAD_TOO_LARGE,"文件较大，请下载后查看");
            if(!kind.equals("text")) validateMagic(object,kind,mime(node.getName()));
        }
        long size=object.getSize(); long[] range=parseRange(request.getHeader(HttpHeaders.RANGE),size);
        long start=range[0], length=range[1]; boolean partial=range[2]==1;
        // 在提交响应头前打开 MinIO，连接故障仍可返回 JSON 错误。
        try(InputStream input=size==0?InputStream.nullInputStream():storage.open(object.getBucket(),object.getObjectKey(),start,length)) {
            response.setStatus(partial?206:200);
            response.setHeader(HttpHeaders.CACHE_CONTROL,"private, no-store");
            response.setHeader("X-Content-Type-Options","nosniff");
            response.setHeader(HttpHeaders.ACCEPT_RANGES,"bytes");
            response.setHeader("Content-Security-Policy","sandbox");
            response.setContentType(preview ? (kind.equals("text")?"text/plain;charset=UTF-8":mime(node.getName())) : "application/octet-stream");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,(preview?ContentDisposition.inline():ContentDisposition.attachment())
                .filename(node.getName(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(length);
            if(partial) response.setHeader(HttpHeaders.CONTENT_RANGE,"bytes "+start+"-"+(start+length-1)+"/"+size);
            byte[] buffer=new byte[32768]; long remaining=length;
            while(remaining>0) {
                int read=input.read(buffer,0,(int)Math.min(buffer.length,remaining));
                if(read<0) throw new IOException("文件内容不完整");
                response.getOutputStream().write(buffer,0,read); remaining-=read;
            }
        }
    }
    private long[] parseRange(String header,long size) {
        if(header==null) return new long[]{0,size,0};
        var matcher=RANGE.matcher(header);
        if(!matcher.matches() || size==0 || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty()))
            throw fail(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,"文件读取范围无效");
        try {
            long start,end;
            if(matcher.group(1).isEmpty()) {
                long suffix=Long.parseLong(matcher.group(2)); if(suffix<=0) throw new NumberFormatException();
                start=Math.max(0,size-suffix); end=size-1;
            } else {
                start=Long.parseLong(matcher.group(1)); end=matcher.group(2).isEmpty()?size-1:Math.min(size-1,Long.parseLong(matcher.group(2)));
            }
            if(start>=size || end<start) throw new NumberFormatException();
            return new long[]{start,end-start+1,1};
        } catch(NumberFormatException e) { throw fail(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,"文件读取范围无效"); }
    }
    private void validateMagic(FileObject object,String kind,String mime) throws IOException {
        byte[] b;
        try(InputStream input=storage.open(object.getBucket(),object.getObjectKey(),0,Math.min(12,object.getSize()))) { b=input.readNBytes(12); }
        boolean valid=kind.equals("pdf")?starts(b,new byte[]{37,80,68,70,45}):switch(mime) {
            case "image/png" -> starts(b,new byte[]{(byte)137,80,78,71,13,10,26,10});
            case "image/jpeg" -> starts(b,new byte[]{(byte)255,(byte)216,(byte)255});
            case "image/gif" -> starts(b,"GIF87a".getBytes(StandardCharsets.US_ASCII)) || starts(b,"GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> b.length>=12 && new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF") && new String(b,8,4,StandardCharsets.US_ASCII).equals("WEBP");
            case "image/bmp" -> starts(b,new byte[]{66,77});
            default -> false;
        };
        if(!valid) throw fail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"文件内容与格式不符，无法预览");
    }
    private boolean starts(byte[] data,byte[] prefix) { return data.length>=prefix.length && Arrays.equals(Arrays.copyOf(data,prefix.length),prefix); }
}
