package com.airuike.netdisk.backend.controller;
import com.airuike.netdisk.backend.common.ApiResponse;
import com.airuike.netdisk.backend.dto.FileDtos.*;
import com.airuike.netdisk.backend.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class FileController {
    private final FileAccess access;
    private final FileQueryService query;
    private final FileTreeService tree;
    private final FileUploadService upload;
    private final FileStreamService stream;
    private final DownloadTicketService tickets;
    public FileController(FileAccess access,FileQueryService query,FileTreeService tree,
            FileUploadService upload,FileStreamService stream,DownloadTicketService tickets) {
        this.access=access; this.query=query; this.tree=tree; this.upload=upload; this.stream=stream; this.tickets=tickets;
    }
    @GetMapping("/files")
    public ApiResponse<Page> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue="0") long parentId,@RequestParam(defaultValue="files") String view,
        @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="updatedAt") String sort,
        @RequestParam(defaultValue="desc") String direction,@RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="30") int pageSize) {
        return ApiResponse.success(query.list(access.userId(jwt),parentId,view,keyword,sort,direction,page,pageSize));
    }
    @GetMapping("/files/summary")
    public ApiResponse<Summary> summary(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(query.summary(access.userId(jwt))); }
    @GetMapping("/files/{id}")
    public ApiResponse<Node> get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return ApiResponse.success(query.get(access.userId(jwt),id)); }
    @PostMapping("/folders") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Node> folder(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateFolder body) {
        return ApiResponse.success(tree.createFolder(access.userId(jwt),body.parentId(),body.name()));
    }
    @PatchMapping("/files/{id}/name")
    public ApiResponse<Node> rename(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Rename body) {
        return ApiResponse.success(tree.rename(access.userId(jwt),id,body.name()));
    }
    @PatchMapping("/files/{id}/parent")
    public ApiResponse<Node> move(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Move body) {
        return ApiResponse.success(tree.move(access.userId(jwt),id,body.parentId()));
    }
    @DeleteMapping("/files/{id}")
    public ApiResponse<Void> trash(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { tree.trash(access.userId(jwt),id); return ApiResponse.success(null); }
    @PostMapping(value="/files/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Node> upload(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") long parentId,
            @RequestPart("file") MultipartFile file, @RequestParam(required=false) String sha256) {
        return ApiResponse.success(upload.upload(access.userId(jwt),parentId,file,sha256));
    }
    @GetMapping("/files/{id}/download")
    public void download(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,HttpServletRequest req,HttpServletResponse res) throws IOException {
        stream.stream(access.userId(jwt),id,false,req,res);
    }
    @GetMapping("/files/{id}/preview")
    public void preview(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,HttpServletRequest req,HttpServletResponse res) throws IOException {
        stream.stream(access.userId(jwt),id,true,req,res);
    }
    @PostMapping("/files/{id}/download-ticket")
    public ApiResponse<Ticket> ticket(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return ApiResponse.success(tickets.issue(access.userId(jwt),id)); }
    @GetMapping("/downloads/{token}")
    public void ticketDownload(@PathVariable String token,HttpServletRequest req,HttpServletResponse res) throws IOException {
        var grant=tickets.resolve(token); stream.stream(grant.userId(),grant.nodeId(),false,req,res);
    }
    @PostMapping("/recycle-bin/{id}/restore")
    public ApiResponse<Node> restore(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { return ApiResponse.success(tree.restore(access.userId(jwt),id)); }
    @DeleteMapping("/recycle-bin/{id}")
    public ApiResponse<Void> purge(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) { tree.purge(access.userId(jwt),id); return ApiResponse.success(null); }
}
