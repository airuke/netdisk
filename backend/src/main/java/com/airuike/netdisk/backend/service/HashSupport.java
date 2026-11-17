package com.airuike.netdisk.backend.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import static com.airuike.netdisk.backend.service.FileRules.fail;

public final class HashSupport {
    private HashSupport() {}
    public static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String hex(MessageDigest digest) { return HexFormat.of().formatHex(digest.digest()); }
    public static String hash(String value) {
        if (value == null || !value.matches("[a-fA-F0-9]{64}"))
            throw fail(HttpStatus.BAD_REQUEST, "SHA-256 格式无效");
        return value.toLowerCase(Locale.ROOT);
    }
    public static boolean matches(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                b.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
