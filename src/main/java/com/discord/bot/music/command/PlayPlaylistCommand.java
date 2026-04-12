package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Command to play a saved playlist from the database.
 */
@Component
public class PlayPlaylistCommand implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(PlayPlaylistCommand.class);

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
        OptionMapping nameOption = event.getOption("name");
        OptionMapping userOption = event.getOption("user");

        if (nameOption == null) {
            event.reply("❌ Please provide a playlist name.").setEphemeral(true).queue();
            return;
        }

        String playlistName = nameOption.getAsString();
        User targetUser = userOption != null ? userOption.getAsUser() : event.getUser();

        event.deferReply().queue(
                hook -> musicService.playPlaylistAsync(event.getGuild(), event.getMember(), targetUser.getId(),
                        playlistName)
                        .subscribe(
                                message -> hook.sendMessage(message).queue(),
                                error -> {
                                    log.error("Unhandled async error for /play-playlist in guild {}: {}",
                                            event.getGuild() != null ? event.getGuild().getId() : "dm",
                                            error.getMessage(), error);
                                    hook.sendMessage("❌ An unexpected error occurred while loading the playlist.")
                                            .queue();
                                }),
                error -> log.error("Failed to acknowledge /play-playlist interaction {}: {}",
                        event.getId(), error.getMessage(), error));
    }
}
