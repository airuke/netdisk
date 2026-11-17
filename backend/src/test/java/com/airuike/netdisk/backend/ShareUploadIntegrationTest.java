package com.airuike.netdisk.backend;

import com.airuike.netdisk.backend.dto.ShareDtos;
import com.airuike.netdisk.backend.dto.UploadDtos;
import com.airuike.netdisk.backend.exception.BusinessException;
import com.airuike.netdisk.backend.mapper.FileNodeMapper;
import com.airuike.netdisk.backend.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"netdisk.upload.chunk-size=1048576", "netdisk.upload.cleanup-delay-ms=86400000"})
@AutoConfigureMockMvc
@ActiveProfiles("files-test")
@TestPropertySource(properties={
    "spring.datasource.url=jdbc:h2:mem:files_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="
})
@Import(MemoryStorageConfig.class)
class ShareUploadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired FileTreeService tree;
    @Autowired FileUploadService files;
    @Autowired FileNodeMapper nodes;
    @Autowired ShareService shares;
    @Autowired UploadSessionTransactions uploads;
    @Autowired ChunkUploadService chunks;
    @Autowired UploadCleanupJob cleanup;
    @Autowired StorageCleanupJob objectCleanup;
    @Autowired MemoryStorageConfig.MemoryStorage storage;
    static final AtomicInteger IPS=new AtomicInteger();
    String owner, other, address;
    static final int CHUNK=1024*1024;

    @BeforeEach void reset() {
        try (var connection=jdbc.getDataSource().getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:files_test"))
                throw new IllegalStateException("Refusing to reset a non-isolated database");
        } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
        for (String table:List.of("file_upload_part","file_upload_session","file_upload_cancel","share_link","file_node","file_object","sys_user"))
            jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO sys_user(id,username,password_hash,nickname,status) VALUES(1,'owner','test','Owner',1),(2,'other','test','Other',1)");
        storage.data.clear(); storage.failPut=false; storage.failRemove=false; storage.beforePut=null; storage.putCalls.set(0);
        owner="Bearer "+jwt.createToken(1L); other="Bearer "+jwt.createToken(2L); address="127.10.0."+IPS.incrementAndGet();
    }
    byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    String hash(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
    MockMultipartFile blob(String name,byte[] data) { return new MockMultipartFile("file",name,"application/octet-stream",data); }
    long file(long user,long parent,String name,byte[] data) { return Long.parseLong(files.upload(user,parent,blob(name,data)).id()); }
    long folder(long parent,String name) { return Long.parseLong(tree.createFolder(1,parent,name).id()); }
    JsonNode result(MockHttpServletRequestBuilder b,int expected) throws Exception {
        b.with(r->{r.setRemoteAddr(address); return r;});
        return json.readTree(mvc.perform(b).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).path("data");
    }
    UploadDtos.Create body(String name,long parent,byte[] data,String sha) {
        return new UploadDtos.Create(UUID.randomUUID().toString(),parent,name,(long)data.length,sha);
    }
    UploadDtos.Create body(String name,byte[] data) throws Exception { return body(name,0,data,hash(data)); }
    JsonNode create(UploadDtos.Create b) throws Exception {
        return result(post("/api/uploads").header("Authorization",owner).contentType("application/json").content(json.writeValueAsBytes(b)),200);
    }
    JsonNode instant(UploadDtos.Create b,String auth) throws Exception {
        return result(post("/api/uploads/instant").header("Authorization",auth).contentType("application/json").content(json.writeValueAsBytes(b)),200);
    }
    byte[] piece(byte[] data,int index) { return Arrays.copyOfRange(data,index*CHUNK,Math.min(data.length,(index+1)*CHUNK)); }
    JsonNode part(String id,int index,byte[] data,String sha,int expected) throws Exception {
        return result(multipart("/api/uploads/{id}/parts/{index}",id,index).file(blob("part",data)).param("sha256",sha).header("Authorization",owner),expected);
    }
    void allParts(String id,byte[] data) throws Exception {
        for(int i=0;i<(data.length+CHUNK-1)/CHUNK;i++) {byte[] p=piece(data,i); part(id,i,p,hash(p),200);}
    }
    JsonNode complete(String id,int expected) throws Exception { return result(post("/api/uploads/{id}/complete",id).header("Authorization",owner),expected); }
    String share(long id,String code) { return shares.create(1,new ShareDtos.Create(id,7,code)).share().code(); }
    String unlock(String code,String extraction) throws Exception {
        return result(post("/api/public/shares/{code}/access",code).contentType("application/json").content(json.writeValueAsBytes(new ShareDtos.Unlock(extraction))),200).path("accessToken").asText();
    }
    byte[] patterned(int size) { byte[] b=new byte[size];new Random(2026).nextBytes(b);return b; }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }

    @Test void privateSharingAndUploadEndpointsRequireJwt() throws Exception {
        mvc.perform(get("/api/shares")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/uploads/config")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/uploads").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        result(get("/api/uploads/config").header("Authorization",owner),200);
    }
    @Test void shareCreationChecksOwnershipInputAndStoresOnlyCodeHash() throws Exception {
        long id=file(1,0,"private.txt",bytes("private"));
        String request=json.writeValueAsString(new ShareDtos.Create(id,7,"Ab12"));
        result(post("/api/shares").header("Authorization",other).contentType("application/json").content(request),404);
        result(post("/api/shares").header("Authorization",owner).contentType("application/json").content("{\"nodeId\":"+id+",\"expiresDays\":366}"),400);
        result(post("/api/shares").header("Authorization",owner).contentType("application/json").content("{\"nodeId\":"+id+",\"expiresDays\":7,\"extractionCode\":\"too-long\"}"),400);
        JsonNode created=result(post("/api/shares").header("Authorization",owner).contentType("application/json").content(request),200);
        assertEquals("AB12",created.path("extractionCode").asText());
        assertTrue(jdbc.queryForObject("SELECT extraction_hash FROM share_link",String.class).startsWith("$2"));
        assertEquals(24,created.path("share").path("code").asText().length());
    }
    @Test void anonymousShareSupportsDownloadAndRanges() throws Exception {
        long id=file(1,0,"shared.txt",bytes("0123456789"));String code=share(id,"");
        assertFalse(result(get("/api/public/shares/{code}",code),200).path("requiresCode").asBoolean());
        String token=unlock(code,"");
        String url=result(post("/api/public/shares/{code}/files/{id}/download-ticket",code,id).header("X-Share-Access",token),200).path("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(bytes("0123456789")));
        mvc.perform(get(url).header("Range","bytes=3-6")).andExpect(status().isPartialContent()).andExpect(content().bytes(bytes("3456")));
    }
    @Test void protectedShareHidesFilenameAndAcceptsCaseInsensitiveCorrectCode() throws Exception {
        long id=file(1,0,"sensitive-name.txt",bytes("safe"));String code=share(id,"AB12");
        JsonNode info=result(get("/api/public/shares/{code}",code),200);
        assertTrue(info.path("requiresCode").asBoolean());assertFalse(info.path("name").asText().contains("sensitive-name"));
        result(post("/api/public/shares/{code}/access",code).contentType("application/json").content("{\"extractionCode\":\"XXXX\"}"),403);
        assertFalse(unlock(code,"ab12").isEmpty());
    }
    @Test void grantsAreRequiredAndBoundToOneShare() throws Exception {
        long a=file(1,0,"a.txt",bytes("a")),b=file(1,0,"b.txt",bytes("b"));String first=share(a,""),second=share(b,"");
        result(get("/api/public/shares/{code}/files",first),403);
        result(get("/api/public/shares/{code}/files",first).header("X-Share-Access","fabricated"),403);
        result(get("/api/public/shares/{code}/files",second).header("X-Share-Access",unlock(first,"")),403);
    }
    @Test void revocationInvalidatesExistingGrantsAndDownloadTickets() throws Exception {
        long id=file(1,0,"a.txt",bytes("a"));String code=share(id,"");String token=unlock(code,"");
        String url=result(post("/api/public/shares/{code}/files/{id}/download-ticket",code,id).header("X-Share-Access",token),200).path("url").asText();
        long shareId=jdbc.queryForObject("SELECT id FROM share_link",Long.class);
        result(delete("/api/shares/{id}",shareId).header("Authorization",owner),200);
        result(get("/api/public/shares/{code}/files",code).header("X-Share-Access",token),410);
        mvc.perform(get(url)).andExpect(status().isGone());
    }
    @Test void expiredOrDisabledSourceCannotBeAccessed() throws Exception {
        long id=file(1,0,"a.txt",bytes("a"));String code=share(id,"");
        jdbc.update("UPDATE share_link SET expires_at=?",LocalDateTime.now().minusMinutes(1));
        result(get("/api/public/shares/{code}",code),410);
        jdbc.update("UPDATE share_link SET expires_at=?",LocalDateTime.now().plusDays(1));
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=1");result(get("/api/public/shares/{code}",code),410);
    }
    @Test void trashRevokesSharesAndRestoreDoesNotReactivateThem() throws Exception {
        long root=folder(0,"folder"),child=file(1,root,"child.txt",bytes("x"));String code=share(child,"");
        tree.trash(1,root);tree.restore(1,root);
        assertFalse(nodes.selectById(child).getDeleted());result(get("/api/public/shares/{code}",code),410);
    }
    @Test void sharedFolderRestrictsSubtreeAndDoesNotExposePrivateAncestors() throws Exception {
        long privateParent=folder(0,"private-parent"),root=folder(privateParent,"shared-root"),nested=folder(root,"nested");
        long child=file(1,nested,"inside.txt",bytes("inside")),outside=file(1,0,"outside.txt",bytes("outside"));
        String code=share(root,""),token=unlock(code,"");
        JsonNode page=result(get("/api/public/shares/{code}/files",code).header("X-Share-Access",token).param("folderId",Long.toString(nested)),200);
        assertEquals("shared-root",page.path("breadcrumbs").get(0).path("name").asText());assertEquals(2,page.path("breadcrumbs").size());
        result(get("/api/public/shares/{code}/files/{id}/preview",code,outside).header("X-Share-Access",token),404);
        tree.move(1,child,0);
        result(get("/api/public/shares/{code}/files/{id}/preview",code,child).header("X-Share-Access",token),404);
    }
    @Test void publicPreviewAppliesSameContentChecks() throws Exception {
        long id=file(1,0,"fake.png",bytes("<script>alert(1)</script>"));String code=share(id,"");String token=unlock(code,"");
        result(get("/api/public/shares/{code}/files/{id}/preview",code,id).header("X-Share-Access",token),415);
    }
    @Test void shareListAndRevokeAreOwnerScoped() throws Exception {
        long id=file(1,0,"a.txt",bytes("a"));shares.create(1,new ShareDtos.Create(id,7,""));
        assertEquals(0,result(get("/api/shares").header("Authorization",other),200).path("total").asInt());
        assertEquals(1,result(get("/api/shares").header("Authorization",owner),200).path("total").asInt());
        result(delete("/api/shares/{id}",jdbc.queryForObject("SELECT id FROM share_link",Long.class)).header("Authorization",other),404);
    }
    @Test void failedCodeAttemptsAreLimitedEvenWhenForwardedForChanges() throws Exception {
        String code=share(file(1,0,"a.txt",bytes("a")),"AB12");
        for(int i=0;i<20;i++) result(post("/api/public/shares/{code}/access",code).header("X-Forwarded-For","10.1.0."+i)
            .contentType("application/json").content("{\"extractionCode\":\"XXXX\"}"),403);
        result(post("/api/public/shares/{code}/access",code).contentType("application/json").content("{\"extractionCode\":\"AB12\"}"),429);
    }
    @Test void instantUploadIsVerifiedOwnerScopedAndIdempotent() throws Exception {
        byte[] data=bytes("existing verified content");long first=file(1,0,"first.txt",data);
        assertEquals(hash(data),jdbc.queryForObject("SELECT hash FROM file_object",String.class));
        UploadDtos.Create b=body("copy.txt",data);JsonNode hit=instant(b,owner);assertTrue(hit.path("instant").asBoolean());
        assertEquals(nodes.selectById(first).getObjectId(),nodes.selectById(hit.path("session").path("node").path("id").asLong()).getObjectId());
        assertTrue(instant(b,owner).path("instant").asBoolean());assertEquals(2,count("file_node"));assertEquals(1,count("file_object"));
        assertEquals(2,jdbc.queryForObject("SELECT reference_count FROM file_object",Integer.class));
    }
    @Test void instantCannotReuseOtherAccountsOrTrashedContent() throws Exception {
        byte[] data=bytes("owned content");long id=file(1,0,"first.txt",data);
        assertFalse(instant(body("other-copy.txt",data),other).path("instant").asBoolean());
        tree.trash(1,id);assertFalse(instant(body("trashed-copy.txt",data),owner).path("instant").asBoolean());
        assertEquals(1,count("file_node"));
    }
    @Test void unverifiedLegacyObjectDoesNotQualifyForInstantUpload() throws Exception {
        byte[] data=bytes("legacy");file(1,0,"legacy.txt",data);jdbc.update("UPDATE file_object SET hash=NULL");
        assertFalse(instant(body("copy.txt",data),owner).path("instant").asBoolean());
    }
    @Test void instantRejectsNameCollisionAndMalformedHash() throws Exception {
        byte[] data=bytes("owned");file(1,0,"existing.txt",data);
        UploadDtos.Create collision=body("existing.txt",data);
        result(post("/api/uploads/instant").header("Authorization",owner).contentType("application/json").content(json.writeValueAsBytes(collision)),409);
        UploadDtos.Create malformed=body("new.txt",0,data,"not-a-hash");
        result(post("/api/uploads").header("Authorization",owner).contentType("application/json").content(json.writeValueAsBytes(malformed)),400);
    }
    @Test void emptyFileHashIsVerifiedAndCanBeReused() throws Exception {
        files.upload(1,0,blob("empty.txt",new byte[0]),hash(new byte[0]));
        assertTrue(instant(body("empty-copy.txt",new byte[0]),owner).path("instant").asBoolean());
        assertEquals(2,count("file_node"));
    }
    @Test void multiPartUploadReassemblesOutOfOrderPartsAndCompleteIsIdempotent() throws Exception {
        byte[] data=patterned(CHUNK*2+113);String id=create(body("large.bin",data)).path("id").asText();
        for(int index:new int[]{2,0,1}) {byte[] p=piece(data,index);part(id,index,p,hash(p),200);}
        JsonNode done=complete(id,200);assertEquals("COMPLETED",done.path("status").asText());
        long node=done.path("node").path("id").asLong();
        mvc.perform(get("/api/files/{id}/download",node).header("Authorization",owner)).andExpect(status().isOk()).andExpect(content().bytes(data));
        assertEquals(node,complete(id,200).path("node").path("id").asLong());assertEquals(1,count("file_node"));assertEquals(1,count("file_object"));
    }
    @Test void initializeIsIdempotentAndNonceCannotDescribeAnotherFile() throws Exception {
        byte[] data=bytes("abc");UploadDtos.Create b=body("file.txt",data);String id=create(b).path("id").asText();
        assertEquals(id,create(b).path("id").asText());assertEquals(1,count("file_upload_session"));
        var changed=new UploadDtos.Create(b.clientId(),0L,"different.txt",b.size(),b.sha256());
        result(post("/api/uploads").header("Authorization",owner).contentType("application/json").content(json.writeValueAsBytes(changed)),409);
    }
    @Test void otherUserCannotReadOrUploadPartsIntoTask() throws Exception {
        byte[] data=bytes("abc");String id=create(body("a.txt",data)).path("id").asText();
        result(get("/api/uploads/{id}",id).header("Authorization",other),404);
        result(multipart("/api/uploads/{id}/parts/{index}",id,0).file(blob("part",data)).param("sha256",hash(data)).header("Authorization",other),404);
        assertEquals(0,count("file_upload_part"));
    }
    @Test void partBoundsSizeAndContentHashAreCheckedBeforeReady() throws Exception {
        byte[] data=bytes("correct");String id=create(body("a.txt",data)).path("id").asText();
        part(id,-1,data,hash(data),400);part(id,1,data,hash(data),400);part(id,0,bytes("x"),hash(bytes("x")),400);
        part(id,0,data,hash(bytes("incorrect")),400);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM file_upload_part WHERE status='READY'",Integer.class));
        part(id,0,data,hash(data),200);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_upload_part WHERE status='READY'",Integer.class));
    }
    @Test void repeatedIdenticalPartDoesNotWriteAgainButChangedHashIsRejected() throws Exception {
        byte[] data=bytes("same");String id=create(body("a.txt",data)).path("id").asText();
        part(id,0,data,hash(data),200);int writes=storage.putCalls.get();part(id,0,data,hash(data),200);assertEquals(writes,storage.putCalls.get());
        part(id,0,data,hash(bytes("different")),409);
    }
    @Test void completionRequiresEveryPart() throws Exception {
        byte[] data=patterned(CHUNK+1);String id=create(body("a.bin",data)).path("id").asText();
        byte[] first=piece(data,0);part(id,0,first,hash(first),200);complete(id,409);
        assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM file_upload_session",String.class));assertEquals(0,count("file_node"));
    }
    @Test void fullHashMismatchNeverPublishesAFile() throws Exception {
        byte[] data=bytes("actual bytes");String id=create(body("a.txt",0,data,hash(bytes("other bytes")))).path("id").asText();
        allParts(id,data);complete(id,400);assertEquals(0,count("file_node"));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM file_upload_session",String.class));
        assertEquals("DELETING",jdbc.queryForObject("SELECT status FROM file_object",String.class));
    }
    @Test void ordinaryUploadDoesNotTrustClaimedHash() throws Exception {
        result(multipart("/api/files/upload").file(blob("a.txt",bytes("actual"))).param("parentId","0")
            .param("sha256",hash(bytes("different"))).header("Authorization",owner),400);
        assertEquals(0,count("file_node"));
    }
    @Test void cancellingBeforeInitializePreventsLateRequests() throws Exception {
        var b=body("a.txt",bytes("x"));result(delete("/api/uploads/by-client/{id}",b.clientId()).header("Authorization",owner),200);
        result(post("/api/uploads").header("Authorization",owner).contentType("application/json").content(json.writeValueAsBytes(b)),410);
        result(post("/api/uploads/instant").header("Authorization",owner).contentType("application/json").content(json.writeValueAsBytes(b)),410);
        assertEquals(0,count("file_node"));assertEquals(0,count("file_upload_session"));
    }
    @Test void cancelledTaskRejectsNewPartsAndCompletion() throws Exception {
        byte[] data=bytes("x");var b=body("a.txt",data);String id=create(b).path("id").asText();allParts(id,data);
        assertEquals("CANCELLED",result(delete("/api/uploads/by-client/{id}",b.clientId()).header("Authorization",owner),200).path("status").asText());
        part(id,0,data,hash(data),410);complete(id,410);assertEquals(0,count("file_node"));
    }
    @Test void cancelAfterCompletionReturnsReceiptAndKeepsFile() throws Exception {
        byte[] data=bytes("x");var b=body("a.txt",data);String id=create(b).path("id").asText();allParts(id,data);complete(id,200);
        assertEquals("COMPLETED",result(delete("/api/uploads/by-client/{id}",b.clientId()).header("Authorization",owner),200).path("status").asText());
        assertEquals(1,count("file_node"));
    }
    @Test void storageFailureDuringPartCanBeRetried() throws Exception {
        byte[] data=bytes("retry");String id=create(body("a.txt",data)).path("id").asText();
        storage.failPut=true;assertThrows(IllegalStateException.class,()->chunks.put(1,id,0,hash(data),blob("part",data)));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM file_upload_part",String.class));
        storage.failPut=false;part(id,0,data,hash(data),200);complete(id,200);
    }
    @Test void temporaryMergeFailurePreservesPartsForRetry() throws Exception {
        byte[] data=bytes("retry merge");String id=create(body("a.txt",data)).path("id").asText();allParts(id,data);
        storage.failPut=true;assertThrows(IllegalStateException.class,()->chunks.complete(1,id));
        assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM file_upload_session",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_upload_part WHERE status='READY'",Integer.class));
        storage.failPut=false;complete(id,200);assertEquals(1,count("file_node"));
    }
    @Test void expiredTaskCannotContinue() throws Exception {
        String id=create(body("a.txt",bytes("x"))).path("id").asText();jdbc.update("UPDATE file_upload_session SET expires_at=?",LocalDateTime.now().minusSeconds(1));
        assertEquals("EXPIRED",result(get("/api/uploads/{id}",id).header("Authorization",owner),200).path("status").asText());complete(id,410);
    }
    @Test void completedTaskCleanupRemovesPartsAndKeepsFinalDownload() throws Exception {
        byte[] data=patterned(CHUNK+7);String id=create(body("a.bin",data)).path("id").asText();allParts(id,data);long node=complete(id,200).path("node").path("id").asLong();
        assertEquals(3,storage.data.size());jdbc.update("UPDATE file_upload_session SET closed_at=?",LocalDateTime.now().minusHours(2));cleanup.cleanup();
        assertEquals(1,storage.data.size());assertEquals(0,count("file_upload_part"));
        mvc.perform(get("/api/files/{id}/download",node).header("Authorization",owner)).andExpect(content().bytes(data));
        assertEquals("COMPLETED",result(get("/api/uploads/{id}",id).header("Authorization",owner),200).path("status").asText());
    }
    @Test void cleanupFailureIsRetriedWithoutRemovingFinalObject() throws Exception {
        byte[] data=bytes("keep");String id=create(body("a.txt",data)).path("id").asText();allParts(id,data);complete(id,200);
        jdbc.update("UPDATE file_upload_session SET closed_at=?",LocalDateTime.now().minusHours(2));storage.failRemove=true;cleanup.cleanup();
        assertEquals(2,storage.data.size());assertFalse(jdbc.queryForObject("SELECT parts_cleaned FROM file_upload_session",Boolean.class));
        storage.failRemove=false;cleanup.cleanup();assertEquals(1,storage.data.size());assertTrue(jdbc.queryForObject("SELECT parts_cleaned FROM file_upload_session",Boolean.class));
    }
    @Test void staleMergeLeaseRecoversAndRejectsOldCommit() throws Exception {
        byte[] data=bytes("recover");String id=create(body("a.txt",data)).path("id").asText();allParts(id,data);
        var old=uploads.beginMerge(1,id);jdbc.update("UPDATE file_upload_session SET merge_lease_until=?",LocalDateTime.now().minusMinutes(1));
        assertEquals("OPEN",result(get("/api/uploads/{id}",id).header("Authorization",owner),200).path("status").asText());
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->uploads.finishMerge(1,old,hash(data))).getStatus());
        complete(id,200);assertEquals(1,count("file_node"));
    }
    @Test void destinationDeletionAndLateNameCollisionBlockPublication() throws Exception {
        byte[] data=bytes("safe");long dir=folder(0,"target");String id=create(body("a.txt",dir,data,hash(data))).path("id").asText();allParts(id,data);
        tree.trash(1,dir);complete(id,404);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM file_node WHERE type='FILE'",Integer.class));
        String another=create(body("same.txt",data)).path("id").asText();allParts(another,data);file(1,0,"same.txt",bytes("existing"));complete(another,409);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_node WHERE type='FILE'",Integer.class));
    }
    @Test void cancelDuringMergePreventsLatePublication() throws Exception {
        byte[] data=bytes("race");var b=body("a.txt",data);String id=create(b).path("id").asText();allParts(id,data);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        storage.beforePut=key->{ if(key.startsWith("users/")) {entered.countDown();try {if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}} };
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<UploadDtos.Session> f=executor.submit(()->chunks.complete(1,id));assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals("CANCELLED",uploads.cancel(1,b.clientId()).status());release.countDown();
            assertThrows(ExecutionException.class,()->f.get(5,TimeUnit.SECONDS));assertEquals(0,count("file_node"));
            assertEquals("DELETING",jdbc.queryForObject("SELECT status FROM file_object",String.class));
        } finally {release.countDown();storage.beforePut=null;executor.shutdownNow();}
    }
    @Test void instantCopyPreviewUsesItsOwnFilenameAndChecksImageBytes() throws Exception {
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aHb8AAAAASUVORK5CYII=");
        file(1,0,"original.txt",png);long copy=instant(body("picture.png",png),owner).path("session").path("node").path("id").asLong();
        mvc.perform(get("/api/files/{id}/preview",copy).header("Authorization",owner))
            .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(png));
    }
    @SuppressWarnings("unchecked")
    @Test void expiredShareGrantAlsoInvalidatesItsDownloadTicket() throws Exception {
        long id=file(1,0,"a.txt",bytes("expire"));String code=share(id,"");String token=unlock(code,"");
        String url=result(post("/api/public/shares/{code}/files/{id}/download-ticket",code,id).header("X-Share-Access",token),200).path("url").asText();
        var grants=(java.util.Map<String,ShareService.Grant>)org.springframework.test.util.ReflectionTestUtils.getField(shares,"grants");
        grants.put(token,new ShareService.Grant(code,System.currentTimeMillis()-1));
        result(get("/api/public/shares/{code}/files",code).header("X-Share-Access",token),403);
        mvc.perform(get(url)).andExpect(status().isForbidden());
    }
    @Test void staleWritingAttemptCannotOverwriteAReplacementPart() throws Exception {
        byte[] data=bytes("same part");String id=create(body("a.txt",data)).path("id").asText();
        var old=uploads.beginPart(1,id,0,data.length,hash(data));part(id,0,data,hash(data),409);
        jdbc.update("UPDATE file_upload_part SET lease_until=?",LocalDateTime.now().minusMinutes(1));part(id,0,data,hash(data),200);
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->uploads.finishPart(1,old,hash(data))).getStatus());
        complete(id,200);assertEquals(1,count("file_node"));
    }
}
