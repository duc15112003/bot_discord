package com.discord.bot.controller;

import com.discord.bot.config.properties.AppProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check and configuration verification controller.
 * Useful during development to confirm configs are loaded properly.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final AppProperties appProperties;
    private final Environment environment;

    public HealthController(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length > 0 ? String.join(",", profiles) : "default";
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "profile", profile));
    }

    @GetMapping("/config/info")
    public ResponseEntity<Map<String, Object>> configInfo() {
        return ResponseEntity.ok(Map.of(
                "features", Map.of(
                        "registrationEnabled", appProperties.getFeatures().isRegistrationEnabled(),
                        "maxUsers", appProperties.getFeatures().getMaxUsers(),
                        "betaEnabled", appProperties.getFeatures().isBetaEnabled()),
                "integration", Map.of(
                        "apiTimeoutMs", appProperties.getIntegration().getApiTimeoutMs(),
                        "retryAttempts", appProperties.getIntegration().getRetryAttempts(),
                        "vendorUrl", appProperties.getIntegration().getVendorUrl())));
    }
}
