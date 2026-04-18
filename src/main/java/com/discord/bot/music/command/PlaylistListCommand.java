package com.discord.bot.music.command;

import com.discord.bot.music.entity.Playlist;
import com.discord.bot.music.service.PlaylistService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;

/**
 * /playlist-list — Show all of the user's playlists.
 */
@Component
public class PlaylistListCommand implements SlashCommand {

    private final PlaylistService playlistService;

    public PlaylistListCommand(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Override
    public String getName() {
        return "playlist-list";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("playlist-list", "Show all your saved playlists");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        
        List<Playlist> playlists = playlistService.getUserPlaylists(userId);
        
        if (playlists.isEmpty()) {
            event.reply("📋 You don't have any playlists yet. Use `/playlist-add` to create one!").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📋 Your Playlists");
        embed.setColor(Color.MAGENTA);
        embed.setAuthor(event.getUser().getEffectiveName(), null, event.getUser().getEffectiveAvatarUrl());
        
        StringBuilder description = new StringBuilder();
        description.append("Here are your saved playlists:\n\n");
        for (int i = 0; i < playlists.size(); i++) {
            Playlist pl = playlists.get(i);
            int trackCount = playlistService.getTrackCount(pl.getId());
            description.append(String.format("**%d.** `%s` — %d track%s\n", 
                i + 1, pl.getName(), trackCount, trackCount == 1 ? "" : "s"));
        }
        
        embed.setDescription(description.toString());
        embed.setFooter("Use /play <playlist_name> to play a playlist");
        
        event.replyEmbeds(embed.build()).queue();
    }
}
