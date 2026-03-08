package com.discord.bot.music.command;

import com.discord.bot.music.model.TrackInfo;
import com.discord.bot.music.service.MusicService;
import com.discord.bot.music.service.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /playlist-add <name> — Add the currently playing track to a playlist.
 */
@Component
public class PlaylistAddCommand implements SlashCommand {

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
        return Commands.slash("playlist-add", "Add the currently playing track to a playlist")
                .addOption(OptionType.STRING, "name", "Playlist name", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String playlistName = event.getOption("name").getAsString();
        String userId = event.getUser().getId();
        long guildId = event.getGuild().getIdLong();

        // Get channel from user's voice state
        net.dv8tion.jda.api.entities.GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("❌ You must be in a voice channel to use this command!").queue();
            return;
        }
        long channelId = voiceState.getChannel().getIdLong();

        TrackInfo nowPlaying = musicService.getNowPlaying(guildId, channelId);
        String result = playlistService.addTrack(userId, playlistName, nowPlaying);

        event.reply(result).queue();
    }
}
