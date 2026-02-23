package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /pre — Play the previous track from history.
 */
@Component
public class PreviousCommand implements SlashCommand {

    private final MusicService musicService;

    public PreviousCommand(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "pre";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("pre", "Play the previous track from history");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String result = musicService.previous(event.getGuild());
        event.reply(result).queue();
    }
}
