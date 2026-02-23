package com.discord.bot.music.service;

import com.discord.bot.music.audio.GuildMusicManager;
import com.discord.bot.music.model.GuildMusicQueue;
import com.discord.bot.music.model.TrackInfo;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core music service orchestrating all playback operations.
 * Uses Lavalink for audio processing and GuildMusicManager for state.
 */
@Service
public class MusicService {

    private static final Logger log = LoggerFactory.getLogger(MusicService.class);

    private final GuildMusicManager guildMusicManager;

    public MusicService(GuildMusicManager guildMusicManager) {
        this.guildMusicManager = guildMusicManager;
    }

    /**
     * Load and play a track or add it to the queue.
     *
     * @return a message describing the result
     */
    public String play(Guild guild, Member member, String query) {
        // Check if user is in a voice channel
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "❌ You must be in a voice channel to use this command!";
        }

        AudioChannelUnion channel = voiceState.getChannel();
        long guildId = guild.getIdLong();
        GuildMusicQueue queue = guildMusicManager.getQueue(guildId);
        Link link = guildMusicManager.getLink(guildId);

        // Join voice channel using DirectAudioController (required for Lavalink)
        guild.getJDA().getDirectAudioController().connect(channel);

        // Determine search prefix
        String searchQuery = query;
        if (!query.startsWith("http://") && !query.startsWith("https://")) {
            searchQuery = "ytsearch:" + query;
        }

        try {
            // Load the track synchronously (called from async context)
            LavalinkLoadResult result = link.loadItem(searchQuery).block();

            if (result == null) {
                return "❌ Failed to load track. Please try again.";
            }

            return handleLoadResult(result, queue, link, guildId, member);
        } catch (Exception e) {
            log.error("Error loading track for guild {}: {}", guildId, e.getMessage(), e);
            return "❌ Error loading track: " + e.getMessage();
        }
    }

    private String handleLoadResult(LavalinkLoadResult result, GuildMusicQueue queue,
            Link link, long guildId, Member member) {
        String userId = member.getId();
        String userName = member.getEffectiveName();

        if (result instanceof TrackLoaded trackLoaded) {
            Track track = trackLoaded.getTrack();
            TrackInfo info = GuildMusicManager.toTrackInfo(track, userId, userName);

            if (queue.getCurrentTrack() == null) {
                // Nothing playing, start immediately
                queue.setCurrentTrack(info);
                link.createOrUpdatePlayer()
                        .setTrack(track)
                        .setPaused(false)
                        .subscribe();
                return "🎵 Now playing: **" + info.getTitle() + "** by " + info.getAuthor();
            } else {
                queue.enqueue(info);
                return "➕ Added to queue: **" + info.getTitle() + "** | Position: " + queue.size();
            }

        } else if (result instanceof PlaylistLoaded playlistLoaded) {
            List<Track> tracks = playlistLoaded.getTracks();
            if (tracks.isEmpty()) {
                return "❌ Playlist is empty.";
            }

            boolean startedPlaying = false;
            for (Track track : tracks) {
                TrackInfo info = GuildMusicManager.toTrackInfo(track, userId, userName);
                if (queue.getCurrentTrack() == null && !startedPlaying) {
                    queue.setCurrentTrack(info);
                    link.createOrUpdatePlayer()
                            .setTrack(track)
                            .setPaused(false)
                            .subscribe();
                    startedPlaying = true;
                } else {
                    queue.enqueue(info);
                }
            }
            return "📋 Loaded playlist: **" + playlistLoaded.getInfo().getName()
                    + "** with " + tracks.size() + " tracks";

        } else if (result instanceof SearchResult searchResult) {
            List<Track> tracks = searchResult.getTracks();
            if (tracks.isEmpty()) {
                return "❌ No results found for your search.";
            }

            // Take the first result
            Track track = tracks.get(0);
            TrackInfo info = GuildMusicManager.toTrackInfo(track, userId, userName);

            if (queue.getCurrentTrack() == null) {
                queue.setCurrentTrack(info);
                link.createOrUpdatePlayer()
                        .setTrack(track)
                        .setPaused(false)
                        .subscribe();
                return "🎵 Now playing: **" + info.getTitle() + "** by " + info.getAuthor();
            } else {
                queue.enqueue(info);
                return "➕ Added to queue: **" + info.getTitle() + "** | Position: " + queue.size();
            }

        } else if (result instanceof LoadFailed loadFailed) {
            return "❌ Failed to load: " + loadFailed.getException().getMessage();

        } else {
            return "❌ No matches found.";
        }
    }

    /**
     * Stop playback, clear queue, disconnect from voice.
     */
    public String stop(Guild guild) {
        long guildId = guild.getIdLong();
        GuildMusicQueue queue = guildMusicManager.getQueue(guildId);

        queue.clear();
        queue.setCurrentTrack(null);

        guildMusicManager.getLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(null)
                .subscribe();

        // Disconnect using DirectAudioController (required for Lavalink)
        guild.getJDA().getDirectAudioController().disconnect(guild);
        guildMusicManager.cleanup(guildId);

        return "⏹️ Stopped playback and cleared the queue.";
    }

    /**
     * Skip to the next track in queue.
     */
    public String next(Guild guild) {
        long guildId = guild.getIdLong();
        GuildMusicQueue queue = guildMusicManager.getQueue(guildId);

        TrackInfo next = queue.dequeue();
        if (next == null) {
            queue.setCurrentTrack(null);
            guildMusicManager.getLink(guildId)
                    .createOrUpdatePlayer()
                    .setTrack(null)
                    .subscribe();
            return "⏭️ No more tracks in queue. Playback stopped.";
        }

        queue.setCurrentTrack(next);
        guildMusicManager.getLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(next.getLavalinkTrack())
                .setPaused(false)
                .subscribe();

        return "⏭️ Skipped! Now playing: **" + next.getTitle() + "**";
    }

    /**
     * Play the previous track from history.
     */
    public String previous(Guild guild) {
        long guildId = guild.getIdLong();
        GuildMusicQueue queue = guildMusicManager.getQueue(guildId);

        TrackInfo prev = queue.popFromHistory();
        if (prev == null) {
            return "⏮️ No previous tracks in history.";
        }

        queue.setCurrentTrack(prev);
        guildMusicManager.getLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(prev.getLavalinkTrack())
                .setPaused(false)
                .subscribe();

        return "⏮️ Playing previous: **" + prev.getTitle() + "**";
    }

    /**
     * Pause the current track.
     */
    public String pause(Guild guild) {
        long guildId = guild.getIdLong();
        GuildMusicQueue queue = guildMusicManager.getQueue(guildId);

        if (queue.getCurrentTrack() == null) {
            return "❌ Nothing is playing right now.";
        }

        if (queue.isPaused()) {
            return "⏸️ Already paused.";
        }

        queue.setPaused(true);
        guildMusicManager.getLink(guildId)
                .createOrUpdatePlayer()
                .setPaused(true)
                .subscribe();

        return "⏸️ Paused: **" + queue.getCurrentTrack().getTitle() + "**";
    }

    /**
     * Resume playback.
     */
    public String resume(Guild guild) {
        long guildId = guild.getIdLong();
        GuildMusicQueue queue = guildMusicManager.getQueue(guildId);

        if (queue.getCurrentTrack() == null) {
            return "❌ Nothing is playing right now.";
        }

        if (!queue.isPaused()) {
            return "▶️ Already playing.";
        }

        queue.setPaused(false);
        guildMusicManager.getLink(guildId)
                .createOrUpdatePlayer()
                .setPaused(false)
                .subscribe();

        return "▶️ Resumed: **" + queue.getCurrentTrack().getTitle() + "**";
    }

    /**
     * Get the currently playing track info.
     */
    public TrackInfo getNowPlaying(long guildId) {
        return guildMusicManager.getQueue(guildId).getCurrentTrack();
    }
}
