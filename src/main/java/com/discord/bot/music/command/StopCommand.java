package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /stop — Stop playback, clear queue, and leave voice.
 */
@Component
public class StopCommand implements SlashCommand {

    private final MusicService musicService;

    public StopCommand(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("stop", "Stop playback, clear queue, and leave voice channel");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String result = musicService.stop(event.getGuild());
        event.reply(result).queue();
    }
}
