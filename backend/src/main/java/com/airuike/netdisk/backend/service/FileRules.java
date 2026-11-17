package com.airuike.netdisk.backend.service;
import com.airuike.netdisk.backend.exception.BusinessException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class FileRules {
    private FileRules() {}
    public static BusinessException fail(HttpStatus status, String message) {
        return new BusinessException(status, message);
    }
    public static String name(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > 200 || value.equals(".") || value.equals("..")
            || value.chars().anyMatch(c -> Character.isISOControl(c) || c=='/' || c=='\\')) {
            throw fail(HttpStatus.BAD_REQUEST, "名称应为 1–200 个字符，且不能包含路径分隔符或控制字符");
        }
        return value;
    }
    public static String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index+1).toLowerCase(Locale.ROOT);
    }
    public static String mime(String name) {
        return switch (extension(name)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "pdf" -> "application/pdf";
            case "txt", "md", "log", "csv", "json" -> "text/plain";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }
    public static String category(String name) {
        return switch (extension(name)) {
            case "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic" -> "IMAGE";
            case "pdf", "txt", "md", "log", "csv", "json", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> "DOCUMENT";
            case "mp4", "mkv", "avi", "mov", "webm" -> "VIDEO";
            case "mp3", "wav", "flac", "m4a", "ogg" -> "AUDIO";
            default -> "OTHER";
        };
    }
    public static String previewKind(String name) {
        return switch (extension(name)) {
            case "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "image";
            case "pdf" -> "pdf";
            case "txt", "md", "log", "csv", "json" -> "text";
            default -> "none";
        };
    }
}
