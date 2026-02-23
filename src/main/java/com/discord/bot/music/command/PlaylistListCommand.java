package com.discord.bot.music.command;

import com.discord.bot.music.service.PlaylistService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /playlist-list — Show all of the user's playlists.
 */
@Component
public class PlaylistListCommand implements SlashCommand {

    private final PlaylistService playlistService;

    public PlaylistListCommand(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Override
    public String getName() {
        return "playlist-list";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("playlist-list", "Show all your saved playlists and their tracks");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String result = playlistService.listPlaylists(userId);

        // Discord messages have a 2000 char limit
        if (result.length() > 2000) {
            result = result.substring(0, 1997) + "...";
        }

        event.reply(result).queue();
    }
}
