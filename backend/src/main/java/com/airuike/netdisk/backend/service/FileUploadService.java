package com.airuike.netdisk.backend.service;

import com.airuike.netdisk.backend.config.StorageProperties;
import com.airuike.netdisk.backend.dto.FileDtos.Node;
import com.airuike.netdisk.backend.storage.ObjectStorage;
import java.io.IOException;
import java.security.DigestInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import static com.airuike.netdisk.backend.service.FileRules.*;

@Service
public class FileUploadService {
    private final UploadTransactionService transactions;
    private final ObjectStorage storage;
    private final StorageProperties props;
    public FileUploadService(UploadTransactionService transactions, ObjectStorage storage, StorageProperties props) {
        this.transactions=transactions; this.storage=storage; this.props=props;
    }
    public Node upload(long userId, long parentId, MultipartFile file) {
        return upload(userId, parentId, file, null);
    }
    public Node upload(long userId, long parentId, MultipartFile file, String expectedHash) {
        String name=name(file.getOriginalFilename());
        if (expectedHash!=null) expectedHash=HashSupport.hash(expectedHash);
        if (file.getSize()>props.maxFileSize())
            throw fail(HttpStatus.PAYLOAD_TOO_LARGE, "普通上传单文件不能超过 200 MB，请使用分片上传");
        var object=transactions.prepare(userId, parentId, name, file.getSize());
        var digest=HashSupport.digest();
        try (var input=new DigestInputStream(file.getInputStream(), digest)) {
            storage.put(object.getBucket(), object.getObjectKey(), input, file.getSize(), object.getMimeType());
        } catch (IOException e) {
            transactions.abort(object.getId()); throw fail(HttpStatus.BAD_REQUEST, "无法读取上传文件");
        } catch (RuntimeException e) { transactions.abort(object.getId()); throw e; }
        String actual=HashSupport.hex(digest);
        if (expectedHash!=null && !HashSupport.matches(actual, expectedHash)) {
            transactions.abort(object.getId()); throw fail(HttpStatus.BAD_REQUEST, "文件校验失败，请重新上传");
        }
        object.setHash(actual);
        try { return Node.of(transactions.complete(userId, parentId, name, object), null); }
        catch (RuntimeException e) { transactions.abort(object.getId()); throw e; }
    }
}
