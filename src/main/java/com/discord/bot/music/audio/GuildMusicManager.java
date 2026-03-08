package com.discord.bot.music.audio;

import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-channel audio state: queue, Lavalink link, and track scheduler.
 * Uses composite key (guildId + channelId) to support multi-bot playback.
 */
@Component
public class GuildMusicManager {

    private static final Logger log = LoggerFactory.getLogger(GuildMusicManager.class);

    private final ConcurrentHashMap<String, com.discord.bot.music.model.GuildMusicQueue> queues = new ConcurrentHashMap<>();
    private final BotInstancePool botInstancePool;

    public GuildMusicManager(BotInstancePool botInstancePool) {
        this.botInstancePool = botInstancePool;
    }

    /**
     * Build a composite key for per-channel queue management.
     */
    private String key(long guildId, long channelId) {
        return guildId + ":" + channelId;
    }

    /**
     * Get or create the music queue for a specific channel in a guild.
     */
    public com.discord.bot.music.model.GuildMusicQueue getQueue(long guildId, long channelId) {
        return queues.computeIfAbsent(key(guildId, channelId),
                k -> new com.discord.bot.music.model.GuildMusicQueue());
    }

    /**
     * Get the Lavalink link for the bot assigned to this channel.
     */
    public Link getLink(long guildId, long channelId) {
        BotInstance bot = botInstancePool.getBotInChannel(guildId, channelId);
        if (bot == null) {
            throw new IllegalStateException("No bot assigned to guild " + guildId + " channel " + channelId);
        }
        return bot.getLavalinkClient().getOrCreateLink(guildId);
    }

    /**
     * Get the BotInstance assigned to a channel.
     */
    public BotInstance getBotInChannel(long guildId, long channelId) {
        return botInstancePool.getBotInChannel(guildId, channelId);
    }

    /**
     * Find or assign a bot for a channel.
     */
    public BotInstance findOrAssignBot(long guildId, long channelId) {
        return botInstancePool.findOrAssignBot(guildId, channelId);
    }

    /**
     * Clean up channel state when the bot leaves.
     */
    public void cleanup(long guildId, long channelId) {
        String k = key(guildId, channelId);
        com.discord.bot.music.model.GuildMusicQueue queue = queues.remove(k);
        if (queue != null) {
            queue.clear();
            queue.setCurrentTrack(null);
        }

        BotInstance bot = botInstancePool.getBotInChannel(guildId, channelId);
        if (bot != null) {
            bot.getLavalinkClient().getOrCreateLink(guildId).destroy();
            botInstancePool.releaseBot(guildId, channelId);
        }
        log.info("Cleaned up music state for guild {} channel {}", guildId, channelId);
    }

    /**
     * Convert a Lavalink Track to our TrackInfo DTO.
     */
    public static com.discord.bot.music.model.TrackInfo toTrackInfo(Track track, String requesterId,
            String requesterName) {
        return com.discord.bot.music.model.TrackInfo.builder()
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

    /**
     * Get the bot pool.
     */
    public BotInstancePool getBotPool() {
        return botInstancePool;
    }
}
