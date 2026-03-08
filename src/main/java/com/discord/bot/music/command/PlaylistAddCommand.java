package com.discord.bot.music.command;

import com.discord.bot.music.audio.BotInstance;
import com.discord.bot.music.audio.GuildMusicManager;
import com.discord.bot.music.audio.BotInstancePool;
import com.discord.bot.music.model.TrackInfo;
import com.discord.bot.music.service.MusicService;
import com.discord.bot.music.service.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * /playlist-add <name> [query] — Add the currently playing track or a specific
 * track to a playlist.
 */
@Component
public class PlaylistAddCommand implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(PlaylistAddCommand.class);

    private final PlaylistService playlistService;
    private final MusicService musicService;

    public PlaylistAddCommand(PlaylistService playlistService, MusicService musicService) {
        this.playlistService = playlistService;
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "playlist-add";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("playlist-add", "Add a track to a playlist")
                .addOption(OptionType.STRING, "name", "Playlist name", true)
                .addOption(OptionType.STRING, "query", "Song name or URL (optional, defaults to now playing)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String playlistName = event.getOption("name").getAsString();
        String userId = event.getUser().getId();
        String userName = event.getUser().getName();
        long guildId = event.getGuild().getIdLong();

        var queryOption = event.getOption("query");

        if (queryOption != null) {
            // Addition by URL/Search
            String query = queryOption.getAsString();
            handleUrlAddition(event, userId, userName, playlistName, query, guildId);
        } else {
            // Addition by Now Playing
            handleNowPlayingAddition(event, userId, playlistName, guildId);
        }
    }

    private void handleNowPlayingAddition(SlashCommandInteractionEvent event, String userId, String playlistName,
            long guildId) {
        net.dv8tion.jda.api.entities.GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.getHook().sendMessage("❌ You must be in a voice channel to add the 'now playing' track!").queue();
            return;
        }

        long channelId = voiceState.getChannel().getIdLong();
        TrackInfo nowPlaying = musicService.getNowPlaying(guildId, channelId);

        if (nowPlaying == null) {
            event.getHook().sendMessage("❌ Nothing is currently playing in your channel.").queue();
            return;
        }

        String result = playlistService.addTrack(userId, playlistName, nowPlaying);
        event.getHook().sendMessage(result).queue();
    }

    private void handleUrlAddition(SlashCommandInteractionEvent event, String userId, String userName,
            String playlistName, String query, long guildId) {
        // We use the primary bot to resolve metadata
        BotInstancePool botPool = musicService.getGuildMusicManager().getBotPool();
        BotInstance primaryBot = botPool.getPrimaryBot();

        if (primaryBot == null) {
            event.getHook().sendMessage("❌ Internal error: Primary bot not available.").queue();
            return;
        }

        final String searchQuery = query.startsWith("http") ? query : "ytsearch:" + query;

        // Must load via a Link in Lavalink Client v4
        primaryBot.getLavalinkClient().getOrCreateLink(guildId).loadItem(searchQuery).subscribe(result -> {
            log.info("Lavalink load result for query '{}': {}", searchQuery, result.getClass().getSimpleName());

            if (result instanceof dev.arbjerg.lavalink.client.player.TrackLoaded trackLoaded) {
                var track = trackLoaded.getTrack();
                TrackInfo info = GuildMusicManager.toTrackInfo(track, userId, userName);
                String msg = playlistService.addTrack(userId, playlistName, info);
                event.getHook().sendMessage(msg).queue();
            } else if (result instanceof dev.arbjerg.lavalink.client.player.PlaylistLoaded playlistLoaded) {
                var tracks = playlistLoaded.getTracks();
                log.info("Loaded YouTube playlist with {} tracks", tracks.size());
                int count = 0;
                for (var track : tracks) {
                    TrackInfo info = GuildMusicManager.toTrackInfo(track, userId, userName);
                    playlistService.addTrack(userId, playlistName, info);
                    count++;
                }
                event.getHook()
                        .sendMessage(
                                "✅ Added **" + count + "** tracks from YouTube playlist to **" + playlistName + "**.")
                        .queue();
            } else if (result instanceof dev.arbjerg.lavalink.client.player.SearchResult searchResult
                    && !searchResult.getTracks().isEmpty()) {
                var track = searchResult.getTracks().get(0);
                TrackInfo info = GuildMusicManager.toTrackInfo(track, userId, userName);
                String msg = playlistService.addTrack(userId, playlistName, info);
                event.getHook().sendMessage(msg).queue();
            } else if (result instanceof dev.arbjerg.lavalink.client.player.NoMatches) {
                log.warn("No matches found for query: {}", searchQuery);
                event.getHook().sendMessage("❌ No matches found for: " + query).queue();
            } else if (result instanceof dev.arbjerg.lavalink.client.player.LoadFailed loadFailed) {
                log.error("Lavalink load failed for query '{}': {}", searchQuery,
                        loadFailed.getException().getMessage());
                event.getHook().sendMessage("❌ Failed to load track: " + loadFailed.getException().getMessage())
                        .queue();
            } else {
                log.warn("Unknown load result for query '{}': {}", searchQuery, result.getClass().getSimpleName());
                event.getHook().sendMessage("❌ Could not find or load track: " + query).queue();
            }
        }, error -> {
            log.error("Error during search for query '{}': {}", searchQuery, error.getMessage(), error);
            event.getHook().sendMessage("❌ Error loading track: " + error.getMessage()).queue();
        });
    }
}
