package com.discord.bot.music.listener;

import com.discord.bot.music.audio.GuildMusicManager;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listens for voice channel events.
 * Auto-disconnects and cleans up when the bot is alone in a voice channel.
 */
@Component
public class VoiceChannelListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(VoiceChannelListener.class);

    private final GuildMusicManager guildMusicManager;

    public VoiceChannelListener(GuildMusicManager guildMusicManager) {
        this.guildMusicManager = guildMusicManager;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        // Only care about channel leave events
        AudioChannelUnion channelLeft = event.getChannelLeft();
        if (channelLeft == null)
            return;

        // Check if bot is in this channel
        var selfMember = event.getGuild().getSelfMember();
        GuildVoiceState selfVoiceState = selfMember.getVoiceState();
        if (selfVoiceState == null || !selfVoiceState.inAudioChannel())
            return;

        AudioChannelUnion botChannel = selfVoiceState.getChannel();
        if (botChannel == null || botChannel.getIdLong() != channelLeft.getIdLong())
            return;

        // Check if bot is alone (only bot remains)
        long humanCount = channelLeft.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();

        if (humanCount == 0) {
            log.info("Bot is alone in voice channel in guild {}, disconnecting...", event.getGuild().getId());
            // Use DirectAudioController for disconnect (required for Lavalink)
            event.getJDA().getDirectAudioController().disconnect(event.getGuild());
            guildMusicManager.cleanup(event.getGuild().getIdLong());
        }
    }
}
