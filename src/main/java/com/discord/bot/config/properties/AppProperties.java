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
    private Ai ai = new Ai();

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

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
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

    /**
     * AI service configuration - for calling external AI APIs.
     */
    public static class Ai {

        private boolean enabled = false;
        private String provider = "gemini"; // gemini
        private String apiKey = "";
        private String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
        /** Gemini model hint; endpoint model in apiUrl takes precedence. */
        private String model = "gemini-2.5-flash";
        private int timeoutMs = 45000;
        private int retryAttempts = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }
    }
}


