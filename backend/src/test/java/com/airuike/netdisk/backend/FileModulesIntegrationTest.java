package com.airuike.netdisk.backend;
import com.airuike.netdisk.backend.service.*;
import com.airuike.netdisk.backend.exception.BusinessException;
import com.airuike.netdisk.backend.mapper.FileNodeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("files-test")
@TestPropertySource(properties={
    "spring.datasource.url=jdbc:h2:mem:files_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="
})
@Import(MemoryStorageConfig.class)
class FileModulesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired FileTreeService tree;
    @Autowired FileUploadService upload;
    @Autowired FileQueryService query;
    @Autowired FileNodeMapper nodes;
    @Autowired StorageCleanupJob cleanup;
    @Autowired MemoryStorageConfig.MemoryStorage storage;
    String bearer;
    @BeforeEach void reset() {
        try (var connection=jdbc.getDataSource().getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:files_test"))
                throw new IllegalStateException("Refusing to reset a non-isolated database");
        } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
        jdbc.update("DELETE FROM file_upload_part"); jdbc.update("DELETE FROM file_upload_session");
        jdbc.update("DELETE FROM file_upload_cancel"); jdbc.update("DELETE FROM share_link");
        jdbc.update("DELETE FROM file_node"); jdbc.update("DELETE FROM file_object"); jdbc.update("DELETE FROM sys_user");
        jdbc.update("INSERT INTO sys_user(id,username,password_hash,nickname,status) VALUES(1,'owner','test','Owner',1),(2,'other','test','Other',1)");
        storage.data.clear(); storage.failPut=false; storage.failRemove=false; storage.beforePut=null; storage.putCalls.set(0); bearer="Bearer "+jwt.createToken(1L);
    }
    long folder(long parent,String name) { return Long.parseLong(tree.createFolder(1,parent,name).id()); }
    long file(long parent,String name,String content) {
        return Long.parseLong(upload.upload(1,parent,new MockMultipartFile("file",name,"text/plain",content.getBytes(StandardCharsets.UTF_8))).id());
    }
    @Test void anonymousAndOtherUserCannotReadOrMutate() throws Exception {
        long id=file(0,"private.txt","secret");
        mvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
        String other="Bearer "+jwt.createToken(2L);
        mvc.perform(get("/api/files/{id}",id).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/files/{id}/download",id).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/files/{id}",id).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/files").header("Authorization",other)).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void disabledAccountIsRejectedEvenWithJwt() throws Exception {
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=1");
        mvc.perform(get("/api/files").header("Authorization",bearer)).andExpect(status().isUnauthorized());
    }
    @Test void foldersNamesPaginationAndBreadcrumbs() throws Exception {
        long parent=folder(0,"项目资料"); folder(parent,"设计"); file(parent,"notes.txt","notes");
        mvc.perform(get("/api/files").param("parentId",String.valueOf(parent)).param("pageSize","1").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.items[0].type").value("FOLDER"))
            .andExpect(jsonPath("$.data.breadcrumbs[1].name").value("项目资料"));
        assertThrows(BusinessException.class,()->folder(0,"项目资料"));
        assertThrows(BusinessException.class,()->folder(0,"../bad"));
        mvc.perform(get("/api/files").param("sort","id;drop table").header("Authorization",bearer)).andExpect(status().isBadRequest());
    }
    @Test void moveRejectsCycleAndForeignFolder() {
        long a=folder(0,"A"), b=folder(a,"B");
        assertEquals(HttpStatus.CONFLICT,assertThrows(BusinessException.class,()->tree.move(1,a,b)).getStatus());
        assertThrows(BusinessException.class,()->tree.move(1,a,a));
        long foreign=Long.parseLong(tree.createFolder(2,0,"foreign").id());
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(BusinessException.class,()->tree.move(1,b,foreign)).getStatus());
        tree.move(1,b,0); assertEquals(0L,nodes.selectById(b).getParentId());
    }
    @Test void renameKeepsExtensionAndUpdatesName() {
        long id=file(0,"old.txt","old"); tree.rename(1,id,"新名称.txt");
        assertEquals("新名称.txt",nodes.selectById(id).getName());
        assertThrows(BusinessException.class,()->tree.rename(1,id,"new.html"));
    }
    @Test void restoringFolderDoesNotRestorePreviouslyTrashedChild() {
        long root=folder(0,"资料"), kept=file(root,"keep.txt","a"), earlier=file(root,"earlier.txt","b");
        tree.trash(1,earlier); tree.trash(1,root);
        assertEquals(2,query.list(1,0,"trash","","updatedAt","desc",1,30).total());
        tree.restore(1,root);
        assertFalse(nodes.selectById(kept).getDeleted()); assertTrue(nodes.selectById(earlier).getDeleted());
        assertNull(nodes.selectById(kept).getTrashRootId());
    }
    @Test void restoreUsesRootWhenOriginalParentIsTrashed() {
        long parent=folder(0,"parent"), child=file(parent,"child.txt","data");
        tree.trash(1,child); tree.trash(1,parent); tree.restore(1,child);
        assertEquals(0L,nodes.selectById(child).getParentId()); assertFalse(nodes.selectById(child).getDeleted());
    }
    @Test void restoreNameConflictRollsBackEntireGroup() {
        long root=folder(0,"same"), child=file(root,"child.txt","data");
        tree.trash(1,root); folder(0,"same");
        assertThrows(BusinessException.class,()->tree.restore(1,root));
        assertTrue(nodes.selectById(root).getDeleted()); assertTrue(nodes.selectById(child).getDeleted());
    }
    @Test void purgeIncludesEarlierTrashedChildrenAndCleanupRetries() {
        long root=folder(0,"root"), child=file(root,"child.txt","data");
        tree.trash(1,child); tree.trash(1,root); tree.purge(1,root);
        assertNull(nodes.selectById(child)); assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_object WHERE status='DELETING'",Integer.class));
        storage.failRemove=true; cleanup.cleanup(); assertEquals(1,storage.data.size());
        storage.failRemove=false; cleanup.cleanup(); assertTrue(storage.data.isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM file_object",Integer.class));
    }
    @Test void failedUploadDoesNotCreateNodeAndStagesCleanup() {
        storage.failPut=true; assertThrows(IllegalStateException.class,()->file(0,"failed.txt","data"));
        assertEquals(0,query.list(1,0,"files","","name","asc",1,30).total());
        assertEquals("DELETING",jdbc.queryForObject("SELECT status FROM file_object",String.class));
        // Failed writes now have a ten-minute cleanup grace period.
        cleanup.cleanup(); assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_object",Integer.class));
        jdbc.update("UPDATE file_object SET cleanup_after=?",java.time.LocalDateTime.now().minusSeconds(1));
        cleanup.cleanup(); assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM file_object",Integer.class));
    }
    @Test void duplicateUploadPreservesExistingObject() {
        file(0,"a.txt","original"); assertThrows(BusinessException.class,()->file(0,"a.txt","replacement"));
        assertEquals(1,storage.data.size()); assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_object",Integer.class));
    }
    @Test void multipartAndEmptyFilesWork() throws Exception {
        mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","empty.txt","text/plain",new byte[0]))
            .param("parentId","0").header("Authorization",bearer))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.size").value(0));
        assertEquals(1,query.summary(1).fileCount());
    }
    @Test void downloadStreamsBytesSupportsRangesAndUnicodeFilename() throws Exception {
        long id=file(0,"中文 文档.txt","0123456789");
        mvc.perform(get("/api/files/{id}/download",id).header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(content().bytes("0123456789".getBytes()))
            .andExpect(header().string("Content-Length","10"));
        mvc.perform(get("/api/files/{id}/download",id).header("Authorization",bearer).header("Range","bytes=2-5"))
            .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 2-5/10"))
            .andExpect(content().bytes("2345".getBytes()));
        mvc.perform(get("/api/files/{id}/download",id).header("Authorization",bearer).header("Range","bytes=99-100"))
            .andExpect(status().isRequestedRangeNotSatisfiable());
    }
    @Test void previewRejectsSpoofedImagesAndHtmlButTextIsSafe() throws Exception {
        long fake=file(0,"fake.png","<script>alert(1)</script>");
        mvc.perform(get("/api/files/{id}/preview",fake).header("Authorization",bearer)).andExpect(status().isUnsupportedMediaType());
        long html=file(0,"index.html","<script>alert(1)</script>");
        mvc.perform(get("/api/files/{id}/preview",html).header("Authorization",bearer)).andExpect(status().isUnsupportedMediaType());
        long text=file(0,"note.txt","<script>alert(1)</script>");
        mvc.perform(get("/api/files/{id}/preview",text).header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(content().contentType("text/plain;charset=UTF-8"));
    }
    @Test void downloadTicketsAreScopedAndDeletedFilesInvalidateThem() throws Exception {
        long id=file(0,"a.txt","data");
        String response=mvc.perform(post("/api/files/{id}/download-ticket",id).header("Authorization",bearer))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url=json.readTree(response).path("data").path("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes("data".getBytes()));
        mvc.perform(get("/api/downloads/not-a-token")).andExpect(status().isNotFound());
        tree.trash(1,id); mvc.perform(get(url)).andExpect(status().isNotFound());
    }
    @Test void searchTreatsWildcardsLiterallyAndCategoriesUseAllFolders() {
        long root=folder(0,"nested"); file(root,"a_100%.txt","data"); file(root,"abc.txt","other");
        assertEquals(1,query.list(1,0,"files","100%","name","asc",1,30).total());
        assertEquals(1,query.list(1,0,"files","_","name","asc",1,30).total());
        assertEquals(2,query.list(1,0,"document","","name","asc",1,30).total());
        assertEquals(9,query.summary(1).usedBytes());
    }
    @Test void deepRestoreFallsBackToRoot() {
        long parent=folder(0,"parent"), child=folder(parent,"deleted-child");
        tree.trash(1,child);
        long target=0;
        for(int i=0;i<63;i++) target=folder(target,"depth-"+i);
        tree.move(1,parent,target);
        tree.restore(1,child);
        assertEquals(0L,nodes.selectById(child).getParentId());
    }
}
