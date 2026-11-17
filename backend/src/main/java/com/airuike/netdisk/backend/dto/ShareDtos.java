package com.airuike.netdisk.backend.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ShareDtos {
    private ShareDtos() {}
    public record Create(@NotNull @Positive Long nodeId,
            @Min(0) @Max(365) int expiresDays,
            @Pattern(regexp="(|[A-Za-z0-9]{4})") String extractionCode) {}
    public record Unlock(@Size(max=4) String extractionCode) {}
    public record Item(String id,String code,String path,String name,String type,long size,
            boolean hasExtractionCode,String status,LocalDateTime expiresAt,LocalDateTime createdAt) {}
    public record Created(Item share,String extractionCode) {}
    public record Page(List<Item> items,long total,int page,int pageSize) {}
    public record Info(boolean requiresCode,String name,String owner,LocalDateTime expiresAt) {}
    public record Access(String accessToken,int expiresIn,FileDtos.Node root) {}
}