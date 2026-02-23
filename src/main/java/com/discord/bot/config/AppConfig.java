package com.discord.bot.config;

import com.discord.bot.config.properties.AppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration class that enables all @ConfigurationProperties
 * bindings.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}
