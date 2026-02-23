package com.discord.bot.music.audio;

import com.discord.bot.music.model.GuildMusicQueue;
import com.discord.bot.music.model.TrackInfo;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-guild audio state: queue, Lavalink link, and track scheduler.
 * Uses ConcurrentHashMap to safely handle multiple guilds.
 */
@Component
public class GuildMusicManager {

    private static final Logger log = LoggerFactory.getLogger(GuildMusicManager.class);

    private final ConcurrentHashMap<Long, GuildMusicQueue> queues = new ConcurrentHashMap<>();
    private final LavalinkClient lavalinkClient;

    public GuildMusicManager(LavalinkClient lavalinkClient) {
        this.lavalinkClient = lavalinkClient;
    }

    /**
     * Get or create the music queue for a guild.
     */
    public GuildMusicQueue getQueue(long guildId) {
        return queues.computeIfAbsent(guildId, id -> new GuildMusicQueue());
    }

    /**
     * Get the Lavalink link for a guild.
     */
    public Link getLink(long guildId) {
        return lavalinkClient.getOrCreateLink(guildId);
    }

    /**
     * Clean up guild state when the bot leaves.
     */
    public void cleanup(long guildId) {
        GuildMusicQueue queue = queues.remove(guildId);
        if (queue != null) {
            queue.clear();
            queue.setCurrentTrack(null);
        }
        lavalinkClient.getOrCreateLink(guildId).destroy();
        log.info("Cleaned up music state for guild {}", guildId);
    }

    /**
     * Convert a Lavalink Track to our TrackInfo DTO.
     */
    public static TrackInfo toTrackInfo(Track track, String requesterId, String requesterName) {
        return TrackInfo.builder()
                .title(track.getInfo().getTitle())
                .author(track.getInfo().getAuthor())
                .uri(track.getInfo().getUri())
                .durationMs(track.getInfo().getLength())
                .requesterId(requesterId)
                .requesterName(requesterName)
                .encoded(track.getEncoded())
                .lavalinkTrack(track)
                .build();
    }
}
