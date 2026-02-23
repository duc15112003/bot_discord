package com.discord.bot.music.audio;

import dev.arbjerg.lavalink.client.Helpers;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Spring configuration for the Lavalink client.
 * Connects to Lavalink server nodes defined in application.yml.
 */
@Configuration
public class LavalinkConfig {

    private static final Logger log = LoggerFactory.getLogger(LavalinkConfig.class);

    @Value("${discord.token}")
    private String discordToken;

    @Value("${lavalink.nodes[0].uri}")
    private String nodeUri;

    @Value("${lavalink.nodes[0].password}")
    private String nodePassword;

    @Value("${lavalink.nodes[0].name:main-node}")
    private String nodeName;

    @Bean
    public LavalinkClient lavalinkClient(TrackScheduler trackScheduler) {
        // Extract bot user ID from the token using lavalink-client helper
        long userId = Helpers.getUserIdFromToken(discordToken);
        LavalinkClient client = new LavalinkClient(userId);

        // Add Lavalink node
        client.addNode(new NodeOptions.Builder()
                .setName(nodeName)
                .setServerUri(URI.create(nodeUri))
                .setPassword(nodePassword)
                .build());

        // Register track event listeners
        trackScheduler.registerListeners(client);

        log.info("Lavalink client configured with node: {} at {}", nodeName, nodeUri);
        return client;
    }
}
