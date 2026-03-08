package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * Command to play a saved playlist from the database.
 */
@Component
public class PlayPlaylistCommand implements SlashCommand {

    private final MusicService musicService;

    public PlayPlaylistCommand(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "play-playlist";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("play-playlist", "Play tracks from a saved playlist")
                .addOption(OptionType.STRING, "name", "The name of the playlist to play", true)
                .addOption(OptionType.USER, "user", "The user whose playlist to play (optional, defaults to you)",
                        false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        OptionMapping nameOption = event.getOption("name");
        OptionMapping userOption = event.getOption("user");

        if (nameOption == null) {
            event.getHook().sendMessage("❌ Please provide a playlist name.").queue();
            return;
        }

        String playlistName = nameOption.getAsString();
        User targetUser = userOption != null ? userOption.getAsUser() : event.getUser();

        // Pass the target user ID to MusicService
        String result = musicService.playPlaylist(event.getGuild(), event.getMember(), targetUser.getId(),
                playlistName);

        event.getHook().sendMessage(result).queue();
    }
}
