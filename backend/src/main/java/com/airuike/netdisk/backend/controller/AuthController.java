package com.airuike.netdisk.backend.controller;

import com.airuike.netdisk.backend.common.ApiResponse;
import com.airuike.netdisk.backend.dto.AuthDtos.LoginRequest;
import com.airuike.netdisk.backend.dto.AuthDtos.LoginResponse;
import com.airuike.netdisk.backend.dto.AuthDtos.RegisterRequest;
import com.airuike.netdisk.backend.dto.AuthDtos.UserView;
import com.airuike.netdisk.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserView>> register(
            @Valid @RequestBody RegisterRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        authService.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ApiResponse.success(
                authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(
            @AuthenticationPrincipal Jwt jwt) {

        return ApiResponse.success(
                authService.currentUser(jwt.getSubject()));
    }
}