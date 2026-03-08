package com.discord.bot.music.service;

import com.discord.bot.music.audio.BotInstance;
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
 * Supports multi-bot: assigns an available bot instance to each voice channel.
 */
@Service
public class MusicService {

    private static final Logger log = LoggerFactory.getLogger(MusicService.class);

    private final GuildMusicManager guildMusicManager;
    private final PlaylistService playlistService;

    public MusicService(GuildMusicManager guildMusicManager, PlaylistService playlistService) {
        this.guildMusicManager = guildMusicManager;
        this.playlistService = playlistService;
    }

    /**
     * Play all tracks from a stored playlist.
     */
    public String playPlaylist(Guild guild, Member member, String targetUserId, String playlistName) {
        // Check if user is in a voice channel
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "❌ You must be in a voice channel to use this command!";
        }

        AudioChannelUnion channel = voiceState.getChannel();
        long guildId = guild.getIdLong();
        long channelId = channel.getIdLong();
        String userId = member.getId();

        // Get tracks from DB
        List<com.discord.bot.music.entity.PlaylistTrack> dbTracks = playlistService.getPlaylistTracks(targetUserId,
                playlistName);
        if (dbTracks.isEmpty()) {
            return "❌ Playlist **" + playlistName + "** is empty or does not exist.";
        }

        // Find or assign a bot
        BotInstance bot = guildMusicManager.findOrAssignBot(guildId, channelId);
        if (bot == null) {
            return "❌ Tất cả bot đều đang bận! Hãy dùng `/stop` ở channel khác.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        Link link = bot.getLavalinkClient().getOrCreateLink(guildId);
        bot.getJda().getDirectAudioController().connect(channel);

        int addedCount = 0;
        int failedCount = 0;

        for (com.discord.bot.music.entity.PlaylistTrack dbTrack : dbTracks) {
            try {
                LavalinkLoadResult result = link.loadItem(dbTrack.getUri()).block();
                if (result instanceof TrackLoaded trackLoaded) {
                    Track track = trackLoaded.getTrack();
                    TrackInfo info = GuildMusicManager.toTrackInfo(track, userId, member.getEffectiveName());

                    if (queue.getCurrentTrack() == null) {
                        queue.setCurrentTrack(info);
                        link.createOrUpdatePlayer()
                                .setTrack(track)
                                .setPaused(false)
                                .subscribe();
                    } else {
                        queue.enqueue(info);
                    }
                    addedCount++;
                } else if (result instanceof SearchResult searchResult && !searchResult.getTracks().isEmpty()) {
                    Track track = searchResult.getTracks().get(0);
                    TrackInfo info = GuildMusicManager.toTrackInfo(track, userId, member.getEffectiveName());

                    if (queue.getCurrentTrack() == null) {
                        queue.setCurrentTrack(info);
                        link.createOrUpdatePlayer()
                                .setTrack(track)
                                .setPaused(false)
                                .subscribe();
                    } else {
                        queue.enqueue(info);
                    }
                    addedCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                failedCount++;
            }
        }

        String message = "🎶 Loaded **" + addedCount + "** tracks from playlist **" + playlistName + "**.";
        if (failedCount > 0) {
            message += " (" + failedCount + " tracks failed to load)";
        }
        return message;
    }

    /**
     * Load and play a track or add it to the queue.
     */
    public String play(Guild guild, Member member, String query) {
        // Check if user is in a voice channel
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "❌ You must be in a voice channel to use this command!";
        }

        AudioChannelUnion channel = voiceState.getChannel();
        long guildId = guild.getIdLong();
        long channelId = channel.getIdLong();

        // Find or assign a bot to this channel
        BotInstance bot = guildMusicManager.findOrAssignBot(guildId, channelId);
        if (bot == null) {
            int total = guildMusicManager.getBotPool().getTotalCount();
            return "❌ Tất cả bot đều đang bận! (" + total + "/" + total + " đang phát nhạc). "
                    + "Hãy dùng `/stop` ở channel khác hoặc invite thêm bot bằng `/invite`.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        Link link = bot.getLavalinkClient().getOrCreateLink(guildId);

        // Join voice channel using the assigned bot's JDA
        bot.getJda().getDirectAudioController().connect(channel);

        // Determine search prefix
        String searchQuery = query;
        if (!query.startsWith("http://") && !query.startsWith("https://")) {
            searchQuery = "ytsearch:" + query;
        } else {
            searchQuery = stripYoutubeMixParams(searchQuery);
        }

        try {
            LavalinkLoadResult result = link.loadItem(searchQuery).block();

            if (result == null) {
                return "❌ Failed to load track. Please try again.";
            }

            return handleLoadResult(result, queue, link, guildId, channelId, member);
        } catch (Exception e) {
            log.error("Error loading track for guild {} channel {}: {}", guildId, channelId, e.getMessage(), e);
            return "❌ Error loading track: " + e.getMessage();
        }
    }

    private String handleLoadResult(LavalinkLoadResult result, GuildMusicQueue queue,
            Link link, long guildId, long channelId, Member member) {
        String userId = member.getId();
        String userName = member.getEffectiveName();

        if (result instanceof TrackLoaded trackLoaded) {
            Track track = trackLoaded.getTrack();
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
    public String stop(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "❌ You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "❌ No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        queue.clear();
        queue.setCurrentTrack(null);

        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(null)
                .subscribe();

        bot.getJda().getDirectAudioController().disconnect(guild);
        guildMusicManager.cleanup(guildId, channelId);

        return "⏹️ Stopped playback and cleared the queue.";
    }

    /**
     * Skip to the next track in queue.
     */
    public String next(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "❌ You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "❌ No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        TrackInfo next = queue.dequeue();
        if (next == null) {
            queue.setCurrentTrack(null);
            bot.getLavalinkClient().getOrCreateLink(guildId)
                    .createOrUpdatePlayer()
                    .setTrack(null)
                    .subscribe();
            return "⏭️ No more tracks in queue. Playback stopped.";
        }

        queue.setCurrentTrack(next);
        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(next.getLavalinkTrack())
                .setPaused(false)
                .subscribe();

        return "⏭️ Skipped! Now playing: **" + next.getTitle() + "**";
    }

    /**
     * Play the previous track from history.
     */
    public String previous(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "❌ You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "❌ No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        TrackInfo prev = queue.popFromHistory();
        if (prev == null) {
            return "⏮️ No previous tracks in history.";
        }

        queue.setCurrentTrack(prev);
        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(prev.getLavalinkTrack())
                .setPaused(false)
                .subscribe();

        return "⏮️ Playing previous: **" + prev.getTitle() + "**";
    }

    /**
     * Pause the current track.
     */
    public String pause(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "❌ You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "❌ No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        if (queue.getCurrentTrack() == null) {
            return "❌ Nothing is playing right now.";
        }

        if (queue.isPaused()) {
            return "⏸️ Already paused.";
        }

        queue.setPaused(true);
        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setPaused(true)
                .subscribe();

        return "⏸️ Paused: **" + queue.getCurrentTrack().getTitle() + "**";
    }

    /**
     * Resume playback.
     */
    public String resume(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "❌ You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "❌ No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        if (queue.getCurrentTrack() == null) {
            return "❌ Nothing is playing right now.";
        }

        if (!queue.isPaused()) {
            return "▶️ Already playing.";
        }

        queue.setPaused(false);
        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setPaused(false)
                .subscribe();

        return "▶️ Resumed: **" + queue.getCurrentTrack().getTitle() + "**";
    }

    /**
     * Get the currently playing track info.
     */
    public TrackInfo getNowPlaying(long guildId, long channelId) {
        return guildMusicManager.getQueue(guildId, channelId).getCurrentTrack();
    }

    /**
     * Get the guild music manager.
     */
    public GuildMusicManager getGuildMusicManager() {
        return guildMusicManager;
    }

    /**
     * Strip YouTube mix/radio parameters from URLs.
     */
    private String stripYoutubeMixParams(String url) {
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            url = url.replaceAll("[&?]list=RD[^&]*", "");
            url = url.replaceAll("[&?]start_radio=[^&]*", "");
            url = url.replaceAll("[&?]index=[^&]*", "");
            url = url.replaceAll("\\?&", "?");
        }
        return url;
    }
}
