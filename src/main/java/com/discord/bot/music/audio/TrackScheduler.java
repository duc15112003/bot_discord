package com.discord.bot.music.audio;

import com.discord.bot.music.model.GuildMusicQueue;
import com.discord.bot.music.model.TrackInfo;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Handles Lavalink track lifecycle events (end, exception, stuck).
 * Automatically advances to the next track in queue when current finishes.
 */
@Component
public class TrackScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrackScheduler.class);

    private final GuildMusicManager guildMusicManager;

    public TrackScheduler(@Lazy GuildMusicManager guildMusicManager) {
        this.guildMusicManager = guildMusicManager;
    }

    /**
     * Register event listeners on the Lavalink client.
     */
    public void registerListeners(LavalinkClient client) {
        client.on(TrackEndEvent.class).subscribe(this::onTrackEnd);
        client.on(TrackExceptionEvent.class).subscribe(this::onTrackException);
        client.on(TrackStuckEvent.class).subscribe(this::onTrackStuck);
        client.on(TrackStartEvent.class).subscribe(this::onTrackStart);
    }

    private void onTrackStart(TrackStartEvent event) {
        long guildId = event.getGuildId();
        Track track = event.getTrack();
        log.info("Track started in guild {}: {}", guildId, track.getInfo().getTitle());
    }

    private void onTrackEnd(TrackEndEvent event) {
        long guildId = event.getGuildId();

        if (event.getEndReason().getMayStartNext()) {
            playNext(guildId);
        }
    }

    private void onTrackException(TrackExceptionEvent event) {
        long guildId = event.getGuildId();
        log.error("Track exception in guild {}: {}", guildId, event.getException().getMessage());
        playNext(guildId);
    }

    private void onTrackStuck(TrackStuckEvent event) {
        long guildId = event.getGuildId();
        log.warn("Track stuck in guild {} (threshold: {}ms)", guildId, event.getThresholdMs());
        playNext(guildId);
    }

    /**
     * Advance to the next track in the guild's queue.
     */
    public void playNext(long guildId) {
        GuildMusicQueue queue = guildMusicManager.getQueue(guildId);
        TrackInfo next = queue.dequeue();

        if (next != null) {
            queue.setCurrentTrack(next);
            guildMusicManager.getLink(guildId)
                    .createOrUpdatePlayer()
                    .setTrack(next.getLavalinkTrack())
                    .setPaused(false)
                    .subscribe(
                            player -> log.info("Now playing in guild {}: {}", guildId, next.getTitle()),
                            error -> log.error("Failed to play next track in guild {}: {}", guildId,
                                    error.getMessage()));
        } else {
            queue.setCurrentTrack(null);
            log.info("Queue empty in guild {}, playback stopped", guildId);
        }
    }
}
