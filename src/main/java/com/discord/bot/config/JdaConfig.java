package com.discord.bot.config;

import com.discord.bot.music.audio.BotInstance;
import com.discord.bot.music.audio.BotInstancePool;
import com.discord.bot.music.audio.TrackScheduler;
import com.discord.bot.music.command.CommandManager;
import com.discord.bot.music.listener.AutoVoiceListener;
import com.discord.bot.music.listener.VoiceChannelListener;
import dev.arbjerg.lavalink.client.Helpers;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
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

import java.net.URI;

/**
 * JDA configuration — creates the primary Discord bot instance
 * and optional secondary bot instances for multi-channel music playback.
 */
@Configuration
public class JdaConfig {

        private static final Logger log = LoggerFactory.getLogger(JdaConfig.class);

        @Value("${discord.token}")
        private String botToken;

        @Value("${discord.extra-tokens:}")
        private String extraTokensRaw;

        @Value("${lavalink.nodes[0].uri}")
        private String nodeUri;

        @Value("${lavalink.nodes[0].password}")
        private String nodePassword;

        @Value("${lavalink.nodes[0].name:main-node}")
        private String nodeName;

        @Bean
        public JDA jda(CommandManager commandManager,
                        VoiceChannelListener voiceChannelListener,
                        AutoVoiceListener autoVoiceListener,
                        BotInstancePool botInstancePool,
                        TrackScheduler trackScheduler) throws InterruptedException {

                log.info("🚀 Starting Multi-Bot System initialization...");

                if (botToken == null || botToken.isBlank()) {
                        log.error("❌ Primary Discord bot token is not set!");
                        throw new IllegalStateException("Primary Discord bot token is required");
                }

                // Create primary bot's LavalinkClient
                LavalinkClient primaryLavalink = createLavalinkClient(botToken, trackScheduler);

                // Create primary JDA instance
                JDA primaryJda = JDABuilder.createDefault(botToken)
                                .enableIntents(
                                                GatewayIntent.GUILD_VOICE_STATES,
                                                GatewayIntent.GUILD_MESSAGES,
                                                GatewayIntent.GUILD_MESSAGE_REACTIONS)
                                .enableCache(CacheFlag.VOICE_STATE)
                                .addEventListeners(
                                                commandManager,
                                                voiceChannelListener,
                                                autoVoiceListener)
                                .setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(primaryLavalink))
                                .build();

                primaryJda.awaitReady();
                log.info("⭐ Primary Bot: {} (ID: {})",
                                primaryJda.getSelfUser().getName(), primaryJda.getSelfUser().getIdLong());

                // Register primary bot in pool
                BotInstance primaryInstance = new BotInstance(primaryJda, primaryLavalink, true, 0);
                botInstancePool.register(primaryInstance);

                // Parse extra tokens
                log.info("🔍 Extra Tokens Raw String: '{}'", extraTokensRaw);

                if (extraTokensRaw != null && !extraTokensRaw.trim().isEmpty()) {
                        String[] tokens = extraTokensRaw.split(",");
                        int index = 1;
                        for (String token : tokens) {
                                token = token.trim();
                                if (!token.isEmpty() && !token.startsWith("${")) {
                                        log.info("Initializing secondary bot #{}...", index);
                                        try {
                                                LavalinkClient secondaryLavalink = createLavalinkClient(token,
                                                                trackScheduler);

                                                JDA secondaryJda = JDABuilder.createDefault(token)
                                                                .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
                                                                .enableCache(CacheFlag.VOICE_STATE)
                                                                .setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(
                                                                                secondaryLavalink))
                                                                .build();

                                                secondaryJda.awaitReady();
                                                log.info("🎵 Secondary Bot #{} [{}]: Connected!",
                                                                index, secondaryJda.getSelfUser().getName());

                                                BotInstance secondaryInstance = new BotInstance(secondaryJda,
                                                                secondaryLavalink, false, index);
                                                botInstancePool.register(secondaryInstance);
                                                index++;
                                        } catch (Exception e) {
                                                log.error("❌ Failed to initialize secondary bot #{}: {}", index,
                                                                e.getMessage());
                                        }
                                }
                        }
                }

                log.info("✅ Multi-Bot System: {} bot(s) total available in pool", botInstancePool.getTotalCount());
                return primaryJda;
        }

        private LavalinkClient createLavalinkClient(String token, TrackScheduler trackScheduler) {
                long userId = Helpers.getUserIdFromToken(token);
                LavalinkClient client = new LavalinkClient(userId);

                client.addNode(new NodeOptions.Builder()
                                .setName(nodeName + "-" + userId)
                                .setServerUri(URI.create(nodeUri))
                                .setPassword(nodePassword)
                                .build());

                trackScheduler.registerListeners(client);
                return client;
        }
}
