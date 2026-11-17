package com.airuike.netdisk.backend.service;

import com.airuike.netdisk.backend.dto.UploadDtos;
import com.airuike.netdisk.backend.storage.JoinedPartsInputStream;
import com.airuike.netdisk.backend.storage.DeadlineInputStream;
import com.airuike.netdisk.backend.config.UploadProperties;
import com.airuike.netdisk.backend.storage.ObjectStorage;
import java.io.IOException;
import java.security.DigestInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import static com.airuike.netdisk.backend.service.FileRules.fail;

@Service
public class ChunkUploadService {
    private final UploadSessionTransactions transactions;
    private final ObjectStorage storage;
    private final UploadProperties props;
    public ChunkUploadService(UploadSessionTransactions transactions,ObjectStorage storage,UploadProperties props) {
        this.transactions=transactions; this.storage=storage; this.props=props;
    }
    public UploadDtos.Part put(long userId,String id,int index,String hash,MultipartFile file) {
        var plan=transactions.beginPart(userId,id,index,file.getSize(),hash);
        if (plan.alreadyStored()) return new UploadDtos.Part(index,plan.part().size(),plan.part().hash());
        var digest=HashSupport.digest();
        try {
            try (var input=new DigestInputStream(file.getInputStream(),digest)) {
                storage.put(plan.session().bucket(),plan.part().objectKey(),input,file.getSize(),"application/octet-stream");
            }
            return transactions.finishPart(userId,plan,HashSupport.hex(digest));
        } catch (IOException e) {
            transactions.failPart(plan); throw fail(HttpStatus.BAD_REQUEST,"无法读取此分片，请重试");
        } catch (RuntimeException e) { transactions.failPart(plan); throw e; }
    }
    public UploadDtos.Session complete(long userId,String id) {
        var plan=transactions.beginMerge(userId,id);
        if (plan.completed()) return transactions.view(plan.session());
        var digest=HashSupport.digest(); boolean corrupt=false;
        try {
            try (var input=new DigestInputStream(new DeadlineInputStream(new JoinedPartsInputStream(storage,plan.session().bucket(),plan.parts()),
                    props.mergeLeaseMinutes()-4L),digest)) {
                storage.put(plan.object().getBucket(),plan.object().getObjectKey(),input,
                        plan.session().size(),plan.object().getMimeType());
            }
            String actual=HashSupport.hex(digest);
            corrupt=!HashSupport.matches(actual,plan.session().hash());
            if (corrupt) throw fail(HttpStatus.BAD_REQUEST,"完整文件校验失败，请重新上传");
            return transactions.finishMerge(userId,plan,actual);
        } catch (IOException e) {
            transactions.failMerge(plan,false); throw fail(HttpStatus.SERVICE_UNAVAILABLE,"合并读取失败，分片仍保留，可稍后继续");
        } catch (RuntimeException e) { transactions.failMerge(plan,corrupt); throw e; }
    }
}
