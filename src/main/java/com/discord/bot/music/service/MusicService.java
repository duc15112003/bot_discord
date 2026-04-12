package com.discord.bot.music.service;

import com.discord.bot.music.audio.BotInstance;
import com.discord.bot.music.audio.GuildMusicManager;
import com.discord.bot.music.model.GuildMusicQueue;
import com.discord.bot.music.model.TrackInfo;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.player.LavalinkLoadResult;
import dev.arbjerg.lavalink.client.player.LoadFailed;
import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackException;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Core music service orchestrating playback operations.
 * The load path is fully non-blocking so slash command acknowledgement does not
 * wait on Lavalink.
 */
@Service
public class MusicService {

    private static final Logger log = LoggerFactory.getLogger(MusicService.class);
    private static final Duration LAVALINK_LOAD_TIMEOUT = Duration.ofSeconds(10);

    private final GuildMusicManager guildMusicManager;
    private final PlaylistService playlistService;

    public MusicService(GuildMusicManager guildMusicManager, PlaylistService playlistService) {
        this.guildMusicManager = guildMusicManager;
        this.playlistService = playlistService;
    }

    /**
     * Load and play a track or add it to the queue.
     */
    public Mono<String> playAsync(Guild guild, Member member, String query) {
        PlaybackContext context = validatePlaybackContext(guild, member);
        if (context.failureMessage() != null) {
            return Mono.just(context.failureMessage());
        }

        BotInstance bot = context.bot();
        GuildMusicQueue queue = context.queue();
        Link link = context.link();
        long guildId = context.guildId();
        long channelId = context.channelId();

        bot.getJda().getDirectAudioController().connect(context.channel());

        return link.loadItem(normalizeQuery(query))
                .timeout(LAVALINK_LOAD_TIMEOUT)
                .map(result -> {
                    if (result == null) {
                        return "❌ Failed to load track. Please try again.";
                    }
                    return handleLoadResult(result, queue, link, guildId, member);
                })
                .onErrorResume(error -> {
                    log.error("Error loading track for guild {} channel {}: {}", guildId, channelId,
                            error.getMessage(), error);
                    return Mono.just("❌ Error loading track: " + userFriendlyErrorMessage(error));
                });
    }

    /**
     * Play all tracks from a stored playlist without blocking the interaction
     * thread.
     */
    public Mono<String> playPlaylistAsync(Guild guild, Member member, String targetUserId, String playlistName) {
        PlaybackContext context = validatePlaybackContext(guild, member);
        if (context.failureMessage() != null) {
            return Mono.just(context.failureMessage());
        }

        List<com.discord.bot.music.entity.PlaylistTrack> dbTracks = playlistService.getPlaylistTracks(targetUserId,
                playlistName);
        if (dbTracks.isEmpty()) {
            return Mono.just("❌ Playlist **" + playlistName + "** is empty or does not exist.");
        }

        BotInstance bot = context.bot();
        GuildMusicQueue queue = context.queue();
        Link link = context.link();
        long guildId = context.guildId();
        long channelId = context.channelId();
        String requesterId = member.getId();
        String requesterName = member.getEffectiveName();

        bot.getJda().getDirectAudioController().connect(context.channel());

        return Flux.fromIterable(dbTracks)
                .concatMap(dbTrack -> link.loadItem(dbTrack.getUri())
                        .timeout(LAVALINK_LOAD_TIMEOUT)
                        .map(result -> processStoredTrackLoadResult(result, queue, link, guildId,
                                requesterId, requesterName))
                        .onErrorResume(error -> {
                            log.warn("Failed to load saved track '{}' for guild {} channel {}: {}",
                                    dbTrack.getUri(), guildId, channelId, error.getMessage());
                            return Mono.just(false);
                        }), 1)
                .reduce(new PlaylistLoadSummary(), (summary, loaded) -> {
                    summary.record(loaded);
                    return summary;
                })
                .map(summary -> summary.toMessage(playlistName))
                .onErrorResume(error -> {
                    log.error("Unexpected error loading playlist '{}' for guild {} channel {}: {}",
                            playlistName, guildId, channelId, error.getMessage(), error);
                    return Mono.just("❌ Error loading playlist: " + userFriendlyErrorMessage(error));
                });
    }

    private PlaybackContext validatePlaybackContext(Guild guild, Member member) {
        if (guild == null || member == null) {
            return PlaybackContext.failure("❌ This command can only be used inside a server.");
        }

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return PlaybackContext.failure("❌ You must be in a voice channel to use this command.");
        }

        AudioChannelUnion channel = voiceState.getChannel();
        long guildId = guild.getIdLong();
        long channelId = channel.getIdLong();

        BotInstance bot = guildMusicManager.findOrAssignBot(guildId, channelId);
        if (bot == null) {
            int total = guildMusicManager.getBotPool().getTotalCount();
            return PlaybackContext.failure("❌ No available bot instance right now (" + total + "/" + total
                    + " in use). Stop playback in another channel or invite another bot.");
        }

        return PlaybackContext.success(guildId, channelId, channel, bot,
                guildMusicManager.getQueue(guildId, channelId),
                bot.getLavalinkClient().getOrCreateLink(guildId));
    }

    private String handleLoadResult(LavalinkLoadResult result, GuildMusicQueue queue, Link link,
            long guildId, Member member) {
        String userId = member.getId();
        String userName = member.getEffectiveName();

        if (result instanceof TrackLoaded trackLoaded) {
            Track track = trackLoaded.getTrack();
            int queuePosition = enqueueTrack(queue, link, guildId, track, userId, userName);
            return buildTrackResponse(track, queuePosition);
        }

        if (result instanceof PlaylistLoaded playlistLoaded) {
            List<Track> tracks = playlistLoaded.getTracks();
            if (tracks.isEmpty()) {
                return "❌ Playlist is empty.";
            }

            for (Track track : tracks) {
                enqueueTrack(queue, link, guildId, track, userId, userName);
            }

            return "📂 Loaded playlist: **" + playlistLoaded.getInfo().getName()
                    + "** with " + tracks.size() + " tracks.";
        }

        if (result instanceof SearchResult searchResult) {
            List<Track> tracks = searchResult.getTracks();
            if (tracks.isEmpty()) {
                return "❌ No results found for your search.";
            }

            Track track = tracks.get(0);
            int queuePosition = enqueueTrack(queue, link, guildId, track, userId, userName);
            return buildTrackResponse(track, queuePosition);
        }

        if (result instanceof LoadFailed loadFailed) {
            return "❌ Failed to load: " + userFriendlyTrackExceptionMessage(loadFailed.getException());
        }

        return "❌ No matches found.";
    }

    private boolean processStoredTrackLoadResult(LavalinkLoadResult result, GuildMusicQueue queue, Link link,
            long guildId, String requesterId, String requesterName) {
        Track track = extractTrack(result);
        if (track == null) {
            if (result instanceof LoadFailed loadFailed) {
                log.warn("Stored track load failed in guild {}: {}", guildId,
                        userFriendlyTrackExceptionMessage(loadFailed.getException()));
            }
            return false;
        }

        enqueueTrack(queue, link, guildId, track, requesterId, requesterName);
        return true;
    }

    private Track extractTrack(LavalinkLoadResult result) {
        if (result instanceof TrackLoaded trackLoaded) {
            return trackLoaded.getTrack();
        }

        if (result instanceof SearchResult searchResult && !searchResult.getTracks().isEmpty()) {
            return searchResult.getTracks().get(0);
        }

        if (result instanceof PlaylistLoaded playlistLoaded && !playlistLoaded.getTracks().isEmpty()) {
            return playlistLoaded.getTracks().get(0);
        }

        return null;
    }

    private int enqueueTrack(GuildMusicQueue queue, Link link, long guildId, Track track,
            String requesterId, String requesterName) {
        TrackInfo info = GuildMusicManager.toTrackInfo(track, requesterId, requesterName);
        boolean startNow;
        int queuePosition = 0;

        synchronized (queue) {
            if (queue.getCurrentTrack() == null) {
                queue.setCurrentTrack(info);
                startNow = true;
            } else {
                queue.enqueue(info);
                queuePosition = queue.size();
                startNow = false;
            }
        }

        if (startNow) {
            startTrack(link, guildId, track);
        }

        return queuePosition;
    }

    private void startTrack(Link link, long guildId, Track track) {
        link.createOrUpdatePlayer()
                .setTrack(track)
                .setPaused(false)
                .subscribe(
                        ignored -> log.debug("Started track '{}' in guild {}", track.getInfo().getTitle(), guildId),
                        error -> log.error("Failed to start track '{}' in guild {}: {}",
                                track.getInfo().getTitle(), guildId, error.getMessage(), error));
    }

    private String buildTrackResponse(Track track, int queuePosition) {
        if (queuePosition == 0) {
            return "🎵 Now playing: **" + track.getInfo().getTitle() + "** by " + track.getInfo().getAuthor();
        }
        return "➡️ Added to queue: **" + track.getInfo().getTitle() + "** | Position: " + queuePosition;
    }

    private String normalizeQuery(String query) {
        if (!query.startsWith("http://") && !query.startsWith("https://")) {
            return "ytsearch:" + query;
        }
        return stripYoutubeMixParams(query);
    }

    private String userFriendlyErrorMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        if (root instanceof TimeoutException || root instanceof InterruptedIOException) {
            return "Lavalink did not respond in time.";
        }
        if (root instanceof IOException) {
            return "The Lavalink request was canceled or interrupted.";
        }

        String message = root.getMessage();
        return (message == null || message.isBlank()) ? "Unexpected Lavalink error." : message;
    }

    private String userFriendlyTrackExceptionMessage(TrackException error) {
        if (error == null) {
            return "Unexpected Lavalink error.";
        }

        String message = error.getMessage();
        return (message == null || message.isBlank()) ? "Unexpected Lavalink error." : message;
    }

    /**
     * Stop playback, clear queue, disconnect from voice.
     */
    public String stop(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "鉂?You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "鉂?No bot is playing in your channel.";
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

        return "鈴癸笍 Stopped playback and cleared the queue.";
    }

    /**
     * Skip to the next track in queue.
     */
    public String next(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "鉂?You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "鉂?No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        TrackInfo next = queue.dequeue();
        if (next == null) {
            queue.setCurrentTrack(null);
            bot.getLavalinkClient().getOrCreateLink(guildId)
                    .createOrUpdatePlayer()
                    .setTrack(null)
                    .subscribe();
            return "鈴笍 No more tracks in queue. Playback stopped.";
        }

        queue.setCurrentTrack(next);
        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(next.getLavalinkTrack())
                .setPaused(false)
                .subscribe();

        return "鈴笍 Skipped! Now playing: **" + next.getTitle() + "**";
    }

    /**
     * Play the previous track from history.
     */
    public String previous(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "鉂?You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "鉂?No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        TrackInfo prev = queue.popFromHistory();
        if (prev == null) {
            return "鈴笍 No previous tracks in history.";
        }

        queue.setCurrentTrack(prev);
        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setTrack(prev.getLavalinkTrack())
                .setPaused(false)
                .subscribe();

        return "鈴笍 Playing previous: **" + prev.getTitle() + "**";
    }

    /**
     * Pause the current track.
     */
    public String pause(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "鉂?You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "鉂?No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        if (queue.getCurrentTrack() == null) {
            return "鉂?Nothing is playing right now.";
        }

        if (queue.isPaused()) {
            return "鈴革笍 Already paused.";
        }

        queue.setPaused(true);
        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setPaused(true)
                .subscribe();

        return "鈴革笍 Paused: **" + queue.getCurrentTrack().getTitle() + "**";
    }

    /**
     * Resume playback.
     */
    public String resume(Guild guild, Member member) {
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            return "鉂?You must be in a voice channel to use this command!";
        }

        long guildId = guild.getIdLong();
        long channelId = voiceState.getChannel().getIdLong();

        BotInstance bot = guildMusicManager.getBotInChannel(guildId, channelId);
        if (bot == null) {
            return "鉂?No bot is playing in your channel.";
        }

        GuildMusicQueue queue = guildMusicManager.getQueue(guildId, channelId);
        if (queue.getCurrentTrack() == null) {
            return "鉂?Nothing is playing right now.";
        }

        if (!queue.isPaused()) {
            return "鈻讹笍 Already playing.";
        }

        queue.setPaused(false);
        bot.getLavalinkClient().getOrCreateLink(guildId)
                .createOrUpdatePlayer()
                .setPaused(false)
                .subscribe();

        return "鈻讹笍 Resumed: **" + queue.getCurrentTrack().getTitle() + "**";
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

    private record PlaybackContext(long guildId, long channelId, AudioChannelUnion channel, BotInstance bot,
            GuildMusicQueue queue, Link link, String failureMessage) {
        private static PlaybackContext success(long guildId, long channelId, AudioChannelUnion channel,
                BotInstance bot, GuildMusicQueue queue, Link link) {
            return new PlaybackContext(guildId, channelId, channel, bot, queue, link, null);
        }

        private static PlaybackContext failure(String failureMessage) {
            return new PlaybackContext(0L, 0L, null, null, null, null, failureMessage);
        }
    }

    private static final class PlaylistLoadSummary {
        private int addedCount;
        private int failedCount;

        private void record(boolean loaded) {
            if (loaded) {
                addedCount++;
                return;
            }
            failedCount++;
        }

        private String toMessage(String playlistName) {
            String message = "🎶 Loaded **" + addedCount + "** tracks from playlist **" + playlistName + "**.";
            if (failedCount > 0) {
                message += " (" + failedCount + " failed to load)";
            }
            return message;
        }
    }
}
