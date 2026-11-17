package com.airuike.netdisk.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(
                    regexp = "^[a-zA-Z0-9_]{3,32}$",
                    message = "用户名须为3～32位字母、数字或下划线"
            )
            String username,

            @NotBlank(message = "密码不能为空")
            @Pattern(
                    regexp = "^[\\x21-\\x7E]{8,64}$",
                    message = "密码须为8～64位字母、数字或英文符号，且不含空格"
            )
            String password,

            @Size(max = 32, message = "昵称不能超过32个字符")
            String nickname
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(
                    regexp = "^[a-zA-Z0-9_]{3,32}$",
                    message = "用户名格式不正确"
            )
            String username,

            @NotBlank(message = "密码不能为空")
            @Pattern(
                    regexp = "^[\\x21-\\x7E]{8,64}$",
                    message = "密码格式不正确"
            )
            String password
    ) {
    }

    public record UserView(
            Long id,
            String username,
            String nickname
    ) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserView user
    ) {
    }
}