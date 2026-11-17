package com.airuike.netdisk.backend.controller;

import com.airuike.netdisk.backend.common.ApiResponse;
import com.airuike.netdisk.backend.config.UploadProperties;
import com.airuike.netdisk.backend.dto.UploadDtos;
import com.airuike.netdisk.backend.service.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final FileAccess access;
    private final UploadProperties props;
    private final UploadSessionTransactions transactions;
    private final ChunkUploadService chunks;
    public UploadController(FileAccess access,UploadProperties props,UploadSessionTransactions transactions,ChunkUploadService chunks) {
        this.access=access; this.props=props; this.transactions=transactions; this.chunks=chunks;
    }
    @GetMapping("/config") public ApiResponse<UploadDtos.Config> config(@AuthenticationPrincipal Jwt jwt) {
        access.checkUser(access.userId(jwt),false);
        return ApiResponse.success(new UploadDtos.Config(props.chunkSize(),props.maxFileSize(),props.sessionTtlHours()));
    }
    @PostMapping("/instant") public ApiResponse<UploadDtos.Instant> instant(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody UploadDtos.Create body) {
        return ApiResponse.success(transactions.instant(access.userId(jwt),body));
    }
    @PostMapping public ApiResponse<UploadDtos.Session> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody UploadDtos.Create body) {
        return ApiResponse.success(transactions.create(access.userId(jwt),body));
    }
    @GetMapping public ApiResponse<List<UploadDtos.Session>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(transactions.list(access.userId(jwt)));
    }
    @GetMapping("/{id}") public ApiResponse<UploadDtos.Session> info(@AuthenticationPrincipal Jwt jwt,@PathVariable String id) {
        return ApiResponse.success(transactions.info(access.userId(jwt),id));
    }
    @PostMapping(value="/{id}/parts/{index}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadDtos.Part> part(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,
            @PathVariable int index,@RequestParam String sha256,@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(chunks.put(access.userId(jwt),id,index,sha256,file));
    }
    @PostMapping("/{id}/complete") public ApiResponse<UploadDtos.Session> complete(@AuthenticationPrincipal Jwt jwt,@PathVariable String id) {
        return ApiResponse.success(chunks.complete(access.userId(jwt),id));
    }
    @DeleteMapping("/by-client/{clientId}") public ApiResponse<UploadDtos.Session> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable String clientId) {
        return ApiResponse.success(transactions.cancel(access.userId(jwt),clientId));
    }
}
