package com.discord.bot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads environment-specific JSON configuration files
 * (package-configuration-{env}.json)
 * and makes the parsed JSON tree available as a Spring bean.
 *
 * These JSON files contain BUSINESS configuration only, never secrets.
 */
@Configuration
public class JsonConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(JsonConfigLoader.class);

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    public JsonNode packageConfiguration(ObjectMapper objectMapper) throws IOException {
        String fileName = "package-configuration-" + activeProfile + ".json";

        Resource resource = new FileSystemResource(fileName);
        if (!resource.exists()) {
            resource = new ClassPathResource(fileName);
        }

        if (!resource.exists()) {
            log.warn("No package configuration file found for profile '{}': {}", activeProfile, fileName);
            return objectMapper.createObjectNode();
        }

        try (InputStream is = resource.getInputStream()) {
            log.info("Loaded package configuration from: {}", fileName);
            return objectMapper.readTree(is);
        }
    }
}
