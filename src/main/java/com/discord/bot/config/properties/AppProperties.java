package com.discord.bot.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Binds application-level configuration properties from
 * application-{profile}.yml.
 * Secrets are injected via environment variables at runtime.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Features features = new Features();
    private Integration integration = new Integration();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Features getFeatures() {
        return features;
    }

    public void setFeatures(Features features) {
        this.features = features;
    }

    public Integration getIntegration() {
        return integration;
    }

    public void setIntegration(Integration integration) {
        this.integration = integration;
    }

    /**
     * JWT configuration - secrets come from environment variables.
     */
    public static class Jwt {

        private String secret = "default-secret";

        private long expiration = 3600000;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpiration() {
            return expiration;
        }

        public void setExpiration(long expiration) {
            this.expiration = expiration;
        }
    }

    /**
     * Feature flags - loaded from product-config.yaml and package-override.yaml.
     */
    public static class Features {

        private boolean registrationEnabled = true;
        private int maxUsers = 1000;
        private boolean betaEnabled = false;

        public boolean isRegistrationEnabled() {
            return registrationEnabled;
        }

        public void setRegistrationEnabled(boolean registrationEnabled) {
            this.registrationEnabled = registrationEnabled;
        }

        public int getMaxUsers() {
            return maxUsers;
        }

        public void setMaxUsers(int maxUsers) {
            this.maxUsers = maxUsers;
        }

        public boolean isBetaEnabled() {
            return betaEnabled;
        }

        public void setBetaEnabled(boolean betaEnabled) {
            this.betaEnabled = betaEnabled;
        }
    }

    /**
     * Integration configuration - loaded from package-configuration-{env}.json
     * via package-override.yaml mapping.
     */
    public static class Integration {

        private int apiTimeoutMs = 5000;
        private int retryAttempts = 3;
        private String vendorUrl = "https://api.default.example.com";

        public int getApiTimeoutMs() {
            return apiTimeoutMs;
        }

        public void setApiTimeoutMs(int apiTimeoutMs) {
            this.apiTimeoutMs = apiTimeoutMs;
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public String getVendorUrl() {
            return vendorUrl;
        }

        public void setVendorUrl(String vendorUrl) {
            this.vendorUrl = vendorUrl;
        }
    }
}
