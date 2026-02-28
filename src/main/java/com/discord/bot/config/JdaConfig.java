package com.discord.bot.config;

import com.discord.bot.music.command.CommandManager;
import com.discord.bot.music.listener.AutoVoiceListener;
import com.discord.bot.music.listener.VoiceChannelListener;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JDA configuration — creates the Discord bot instance.
 * Registers event listeners and configures gateway intents for voice.
 */
@Configuration
public class JdaConfig {

        private static final Logger log = LoggerFactory.getLogger(JdaConfig.class);

        @Value("${discord.token}")
        private String botToken;

        @Bean
        public JDA jda(CommandManager commandManager,
                        VoiceChannelListener voiceChannelListener,
                        AutoVoiceListener autoVoiceListener,
                        LavalinkClient lavalinkClient) throws InterruptedException {

                if (botToken == null || botToken.isBlank()) {
                        log.error(
                                        "Discord bot token is not set! Set DISCORD_TOKEN environment variable or configure discord.token in application.yml");
                        throw new IllegalStateException("Discord bot token is required");
                }

                JDA jda = JDABuilder.createDefault(botToken)
                                .enableIntents(
                                                GatewayIntent.GUILD_VOICE_STATES,
                                                GatewayIntent.GUILD_MESSAGES,
                                                GatewayIntent.GUILD_MESSAGE_REACTIONS)
                                .enableCache(CacheFlag.VOICE_STATE)
                                .addEventListeners(
                                                commandManager,
                                                voiceChannelListener,
                                                autoVoiceListener)
                                .setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(lavalinkClient))
                                .build();

                // Wait until JDA is ready
                jda.awaitReady();
                log.info("JDA connected as {} (ID: {})", jda.getSelfUser().getName(), jda.getSelfUser().getIdLong());

                return jda;
        }
}
