package com.discord.bot.music.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.awt.*;

/**
 * /bot-help — Display all available commands.
 */
@Component
public class BotHelpCommand implements SlashCommand {

    @Override
    public String getName() {
        return "bot-help";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("bot-help", "Show all available bot commands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎵 Music Bot — Help")
                .setColor(new Color(88, 101, 242)) // Discord Blurple
                .setDescription("Here are all available commands:")
                .addField("🎶 Music Controls", """
                        `/play <query>` — Play a song (YouTube search or URL)
                        `/stop` — Stop playback, clear queue, leave channel
                        `/next` — Skip to the next track
                        `/pre` — Play the previous track from history
                        `/pause` — Pause the current track
                        `/resume` — Resume playback
                        """, false)
                .addField("📋 Playlist Management", """
                        `/playlist-add <name>` — Save the current track to a playlist
                        `/playlist-list` — View all your playlists and tracks
                        `/playlist-remove <name> <track#>` — Remove a track from a playlist
                        """, false)
                .addField("ℹ️ Other", """
                        `/bot-help` — Show this help message
                        """, false)
                .setFooter("Made with ❤️ using Spring Boot + JDA + Lavalink");

        event.replyEmbeds(embed.build()).queue();
    }
}
