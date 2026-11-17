package com.airuike.netdisk.backend.controller;
import com.airuike.netdisk.backend.common.ApiResponse;
import com.airuike.netdisk.backend.dto.FileDtos;
import com.airuike.netdisk.backend.dto.ShareDtos;
import com.airuike.netdisk.backend.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class PublicShareController {
    private final ShareService shares; private final PublicShareLimiter limiter; private final FileStreamService stream;
    public PublicShareController(ShareService shares,PublicShareLimiter limiter,FileStreamService stream) {
        this.shares=shares; this.limiter=limiter; this.stream=stream;
    }
    @GetMapping("/shares/{code}") public ApiResponse<ShareDtos.Info> info(@PathVariable String code,HttpServletRequest request) {
        limiter.check(request.getRemoteAddr(),"share-info",120); return ApiResponse.success(shares.info(code));
    }
    @PostMapping("/shares/{code}/access") public ApiResponse<ShareDtos.Access> access(@PathVariable String code,
            @Valid @RequestBody ShareDtos.Unlock body,HttpServletRequest request) {
        limiter.check(request.getRemoteAddr(),"share-unlock",20);
        return ApiResponse.success(shares.unlock(code,body.extractionCode()));
    }
    @GetMapping("/shares/{code}/files") public ApiResponse<FileDtos.Page> files(@PathVariable String code,
            @RequestHeader(value="X-Share-Access",required=false) String token,
            @RequestParam(defaultValue="0") long folderId,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="30") int pageSize) {
        return ApiResponse.success(shares.contents(code,token,folderId,page,pageSize));
    }
    @GetMapping("/shares/{code}/files/{id}/preview") public void preview(@PathVariable String code,@PathVariable long id,
            @RequestHeader(value="X-Share-Access",required=false) String token,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var link=shares.authorize(code,token,id); stream.stream(link.userId(),id,true,request,response);
    }
    @PostMapping("/shares/{code}/files/{id}/download-ticket") public ApiResponse<FileDtos.Ticket> ticket(@PathVariable String code,@PathVariable long id,
            @RequestHeader(value="X-Share-Access",required=false) String token,HttpServletRequest request) {
        limiter.check(request.getRemoteAddr(),"share-download",60);
        return ApiResponse.success(shares.downloadTicket(code,token,id));
    }
    @GetMapping("/share-downloads/{ticket}") public void download(@PathVariable String ticket,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var grant=shares.resolveDownload(ticket); var link=shares.linkForDownload(grant);
        stream.stream(link.userId(),grant.nodeId(),false,request,response);
    }
}
