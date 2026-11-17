package com.airuike.netdisk.backend.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] keyBytes =
                Base64.getDecoder().decode(properties.secretBase64());

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT密钥解码后至少需要32字节"
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<>(jwtSecretKey)
        );
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties properties) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> standardValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());

        OAuth2TokenValidator<Jwt> requiredClaimsValidator = jwt -> {
            boolean valid = jwt.getExpiresAt() != null
                    && jwt.getSubject() != null
                    && !jwt.getSubject().isBlank()
                    && jwt.getAudience() != null
                    && jwt.getAudience().contains(properties.audience());

            if (valid) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "令牌信息不完整或签发对象不正确",
                            null
                    )
            );
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        standardValidator,
                        requiredClaimsValidator
                )
        );

        return decoder;
    }
}