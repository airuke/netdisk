package com.airuike.netdisk.backend.dto;

import com.airuike.netdisk.backend.entity.FileNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class FileDtos {
    private FileDtos() {
    }

    public record CreateFolder(@NotNull @PositiveOrZero Long parentId,
            @NotBlank @Size(max = 200) String name) {
    }

    public record Rename(@NotBlank @Size(max = 200) String name) {
    }

    public record Move(@NotNull @PositiveOrZero Long parentId) {
    }

    public record Node(String id, String parentId, String name, String type,
            String category, long size, LocalDateTime createdAt, LocalDateTime updatedAt,
            LocalDateTime deletedAt, String originalPath) {
        public static Node of(FileNode n, String path) {
            return new Node(n.getId().toString(), n.getParentId().toString(), n.getName(),
                    n.getType(), n.getCategory(), n.getSize(), n.getCreatedAt(), n.getUpdatedAt(),
                    n.getDeletedAt(), path);
        }
    }

    public record Crumb(String id, String name) {
    }

    public record Page(List<Node> items, long total, int page, int pageSize,
            List<Crumb> breadcrumbs) {
    }

    public record Summary(long fileCount, long folderCount, long usedBytes, long trashCount) {
    }

    public record Ticket(String url, int expiresIn) {
    }
}
