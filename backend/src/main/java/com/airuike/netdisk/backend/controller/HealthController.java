package com.airuike.netdisk.backend.controller;

import com.airuike.netdisk.backend.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final String applicationName;

    public HealthController(
            @Value("${spring.application.name}") String applicationName
    ) {
        this.applicationName = applicationName;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(
                Map.of(
                        "application", applicationName,
                        "status", "UP"
                )
        );
    }
}