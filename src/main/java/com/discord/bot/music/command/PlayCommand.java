package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * /play <query> — Search and play a song or URL.
 */
@Component
public class PlayCommand implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(PlayCommand.class);

    private final MusicService musicService;

    public PlayCommand(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "play";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("play", "Play a song from YouTube or a URL")
                .addOption(OptionType.STRING, "query", "Song name or URL", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String query = event.getOption("query").getAsString();
        event.deferReply().queue(
                hook -> musicService.playAsync(event.getGuild(), event.getMember(), query)
                        .subscribe(
                                message -> hook.sendMessage(message).queue(),
                                error -> {
                                    log.error("Unhandled async error for /play in guild {}: {}",
                                            event.getGuild() != null ? event.getGuild().getId() : "dm",
                                            error.getMessage(), error);
                                    hook.sendMessage("❌ An unexpected error occurred while loading audio.").queue();
                                }),
                error -> log.error("Failed to acknowledge /play interaction {}: {}",
                        event.getId(), error.getMessage(), error));
    }
}
