package com.discord.bot.music.audio;

import com.discord.bot.music.model.GuildMusicQueue;
import com.discord.bot.music.model.TrackInfo;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Lavalink track lifecycle events (end, exception, stuck).
 * Automatically advances to the next track in queue when current finishes.
 * Supports multi-bot: maps LavalinkClient instances to their BotInstance.
 */
@Component
public class TrackScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrackScheduler.class);

    private final GuildMusicManager guildMusicManager;

    /**
     * Maps LavalinkClient userId to BotInstance for event routing.
     */
    private final Map<Long, BotInstance> clientBotMap = new ConcurrentHashMap<>();

    public TrackScheduler(@Lazy GuildMusicManager guildMusicManager) {
        this.guildMusicManager = guildMusicManager;
    }

    /**
     * Register event listeners on a Lavalink client and map it to its bot instance.
     */
    public void registerListeners(LavalinkClient client) {
        client.on(TrackEndEvent.class).subscribe(this::onTrackEnd);
        client.on(TrackExceptionEvent.class).subscribe(this::onTrackException);
        client.on(TrackStuckEvent.class).subscribe(this::onTrackStuck);
        client.on(TrackStartEvent.class).subscribe(this::onTrackStart);
    }

    /**
     * Associate a LavalinkClient's userId with its BotInstance.
     * Called from JdaConfig after creating each bot instance.
     */
    public void registerBotMapping(long botUserId, BotInstance botInstance) {
        clientBotMap.put(botUserId, botInstance);
        log.info("Mapped LavalinkClient userId {} to bot {}", botUserId, botInstance.getBotName());
    }

    private void onTrackStart(TrackStartEvent event) {
        long guildId = event.getGuildId();
        log.info("Track started in guild {}: {}", guildId, event.getTrack().getInfo().getTitle());
    }

    private void onTrackEnd(TrackEndEvent event) {
        if (event.getEndReason().getMayStartNext()) {
            playNext(event.getGuildId());
        }
    }

    private void onTrackException(TrackExceptionEvent event) {
        log.error("Track exception in guild {}: {}", event.getGuildId(), event.getException().getMessage());
        playNext(event.getGuildId());
    }

    private void onTrackStuck(TrackStuckEvent event) {
        log.warn("Track stuck in guild {} (threshold: {}ms)", event.getGuildId(), event.getThresholdMs());
        playNext(event.getGuildId());
    }

    /**
     * Advance to the next track.
     * Finds the correct bot and channel for this guild from the pool.
     */
    private void playNext(long guildId) {
        // Find which bot(s) are connected in this guild
        BotInstancePool pool = guildMusicManager.getBotPool();
        for (BotInstance bot : pool.getAllInstances()) {
            Long channelId = bot.getConnectedChannel(guildId);
            if (channelId != null) {
                playNextForChannel(guildId, channelId, bot);
                return;
            }
        }
        log.warn("No bot found connected in guild {} for playNext", guildId);
    }

    private void playNextForChannel(long guildId, long channelId, BotInstance bot) {
        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        TrackInfo next = queue.dequeue();

        if (next != null) {
            queue.setCurrentTrack(next);
            Link link = bot.getLavalinkClient().getOrCreateLink(guildId);
            link.createOrUpdatePlayer()
                    .setTrack(next.getLavalinkTrack())
                    .setPaused(false)
                    .subscribe(
                            player -> log.info("Now playing in guild {} channel {}: {}",
                                    guildId, channelId, next.getTitle()),
                            error -> log.error("Failed to play next in guild {} channel {}: {}",
                                    guildId, channelId, error.getMessage()));
        } else {
            queue.setCurrentTrack(null);
            log.info("Queue empty in guild {} channel {}, playback stopped", guildId, channelId);
        }
    }
}
