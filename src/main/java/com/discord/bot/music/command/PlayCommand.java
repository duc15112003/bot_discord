package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

/**
 * /play <query> — Search and play a song or URL.
 */
@Component
public class PlayCommand implements SlashCommand {

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
        event.deferReply().queue();

        String query = event.getOption("query").getAsString();
        String result = musicService.play(event.getGuild(), event.getMember(), query);

        event.getHook().sendMessage(result).queue();
    }
}
