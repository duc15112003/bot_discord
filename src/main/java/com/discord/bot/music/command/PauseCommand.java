package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /pause — Pause the current track.
 */
@Component
public class PauseCommand implements SlashCommand {

    private final MusicService musicService;

    public PauseCommand(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "pause";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("pause", "Pause the currently playing track");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String result = musicService.pause(event.getGuild());
        event.reply(result).queue();
    }
}
