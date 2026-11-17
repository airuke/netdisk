package com.airuike.netdisk.backend.service;

import com.airuike.netdisk.backend.config.JwtProperties;
import com.airuike.netdisk.backend.dto.AuthDtos.LoginRequest;
import com.airuike.netdisk.backend.dto.AuthDtos.LoginResponse;
import com.airuike.netdisk.backend.dto.AuthDtos.RegisterRequest;
import com.airuike.netdisk.backend.dto.AuthDtos.UserView;
import com.airuike.netdisk.backend.entity.SysUser;
import com.airuike.netdisk.backend.exception.BusinessException;
import com.airuike.netdisk.backend.mapper.SysUserMapper;
import com.airuike.netdisk.backend.security.LoginUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            SysUserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties) {

        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    public UserView register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.username())
        );

        if (count > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "用户名已存在"
            );
        }

        String nickname = request.nickname();

        if (nickname == null || nickname.isBlank()) {
            nickname = request.username();
        } else {
            nickname = nickname.strip();
        }

        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setNickname(nickname);
        user.setStatus(1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 同时注册相同用户名时，由数据库唯一约束兜底。
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "用户名已存在"
            );
        }

        return toView(user);
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken
                                .unauthenticated(
                                        request.username(),
                                        request.password()
                                )
                );

        LoginUser user =
                (LoginUser) authentication.getPrincipal();

        return new LoginResponse(
                jwtService.createToken(user.id()),
                "Bearer",
                jwtProperties.accessTokenTtlSeconds(),
                new UserView(
                        user.id(),
                        user.username(),
                        user.nickname()
                )
        );
    }

    public UserView currentUser(String subject) {
        long userId;

        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "登录状态无效，请重新登录"
            );
        }

        SysUser user = userId > 0
                ? userMapper.selectById(userId)
                : null;

        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "账号不存在或已禁用，请重新登录"
            );
        }

        return toView(user);
    }

    private UserView toView(SysUser user) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getNickname()
        );
    }
}