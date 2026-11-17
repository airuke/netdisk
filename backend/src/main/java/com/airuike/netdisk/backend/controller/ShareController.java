package com.airuike.netdisk.backend.controller;
import com.airuike.netdisk.backend.common.ApiResponse;
import com.airuike.netdisk.backend.dto.ShareDtos;
import com.airuike.netdisk.backend.service.FileAccess;
import com.airuike.netdisk.backend.service.ShareService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shares")
public class ShareController {
    private final ShareService shares; private final FileAccess access;
    public ShareController(ShareService shares,FileAccess access) { this.shares=shares; this.access=access; }
    @PostMapping public ApiResponse<ShareDtos.Created> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ShareDtos.Create body) {
        return ApiResponse.success(shares.create(access.userId(jwt),body));
    }
    @GetMapping public ApiResponse<ShareDtos.Page> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(shares.list(access.userId(jwt),page,pageSize));
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        shares.revoke(access.userId(jwt),id); return ApiResponse.success(null);
    }
}
