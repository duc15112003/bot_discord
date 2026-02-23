package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /resume — Resume playback.
 */
@Component
public class ResumeCommand implements SlashCommand {

    private final MusicService musicService;

    public ResumeCommand(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "resume";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("resume", "Resume the paused track");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String result = musicService.resume(event.getGuild());
        event.reply(result).queue();
    }
}
