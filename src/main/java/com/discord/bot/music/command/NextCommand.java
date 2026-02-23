package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /next — Skip to the next track in queue.
 */
@Component
public class NextCommand implements SlashCommand {

    private final MusicService musicService;

    public NextCommand(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "next";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("next", "Skip to the next track in queue");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String result = musicService.next(event.getGuild());
        event.reply(result).queue();
    }
}
