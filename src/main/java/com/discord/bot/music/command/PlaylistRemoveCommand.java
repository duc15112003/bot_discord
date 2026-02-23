package com.discord.bot.music.command;

import com.discord.bot.music.service.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /playlist-remove <name> <track-number> — Remove a track from a playlist.
 */
@Component
public class PlaylistRemoveCommand implements SlashCommand {

    private final PlaylistService playlistService;

    public PlaylistRemoveCommand(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Override
    public String getName() {
        return "playlist-remove";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("playlist-remove", "Remove a track from your playlist")
                .addOption(OptionType.STRING, "name", "Playlist name", true)
                .addOption(OptionType.INTEGER, "track", "Track number to remove (use /playlist-list to see numbers)",
                        true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String playlistName = event.getOption("name").getAsString();
        int trackPosition = event.getOption("track").getAsInt();

        String result = playlistService.removeTrack(userId, playlistName, trackPosition);
        event.reply(result).queue();
    }
}
