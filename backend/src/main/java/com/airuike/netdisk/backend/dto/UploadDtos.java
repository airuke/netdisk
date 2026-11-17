package com.airuike.netdisk.backend.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class UploadDtos {
    private UploadDtos() {}
    public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,64}") String clientId,
            @NotNull @PositiveOrZero Long parentId, @NotBlank @Size(max=200) String name,
            @NotNull @PositiveOrZero Long size,
            @NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String sha256) {}
    public record Config(int chunkSize, long maxFileSize, int sessionTtlHours) {}
    public record Part(int index, long size, String sha256) {}
    public record Session(String id, String clientId, String name, String parentId,
            String folderName, long size, String sha256, int chunkSize, int chunkCount,
            String status, List<Part> parts, LocalDateTime expiresAt,
            LocalDateTime mergeLeaseUntil, boolean instant, FileDtos.Node node) {}
    public record Instant(boolean instant, Session session) {}
}
