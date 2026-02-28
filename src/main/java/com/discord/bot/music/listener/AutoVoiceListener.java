package com.discord.bot.music.listener;

import com.discord.bot.music.entity.TempVoiceChannel;
import com.discord.bot.music.service.AutoVoiceService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JDA event listener for automatic voice channel management.
 * Creates temporary voice channels when users join trigger channels,
 * and deletes them when they become empty.
 *
 * Event flow:
 * 1. User joins trigger channel → Create temp channel, move user
 * 2. User leaves temp channel → Check if empty, delete if so
 * 3. User moves between channels → Handle leave/join appropriately
 */
@Component
public class AutoVoiceListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AutoVoiceListener.class);

    private final AutoVoiceService autoVoiceService;

    public AutoVoiceListener(AutoVoiceService autoVoiceService) {
        this.autoVoiceService = autoVoiceService;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        AudioChannel channelJoined = event.getChannelJoined();
        AudioChannel channelLeft = event.getChannelLeft();

        // Handle user leaving a channel
        if (channelLeft != null) {
            handleChannelLeave(event, channelLeft);
        }

        // Handle user joining a channel
        if (channelJoined != null) {
            handleChannelJoin(event, channelJoined);
        }
    }

    /**
     * Handle user joining a voice channel.
     * If it's a trigger channel, create a temp channel and move the user.
     */
    private void handleChannelJoin(GuildVoiceUpdateEvent event, AudioChannel channelJoined) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        String channelId = channelJoined.getId();

        // Ignore bots
        if (member.getUser().isBot()) {
            return;
        }

        // Check if this is a trigger channel
        if (!autoVoiceService.isTriggerChannel(guildId, channelId)) {
            return;
        }

        // Check if user is on cooldown
        if (autoVoiceService.isOnCooldown(member.getId())) {
            log.debug("User {} is on cooldown, ignoring trigger channel join", member.getId());
            return;
        }

        // Check if user already owns a temp channel
        if (autoVoiceService.userHasTempChannel(guildId, member.getId())) {
            log.debug("User {} already owns a temp channel in guild {}", member.getId(), guildId);
            return;
        }

        log.info("User {} joined trigger channel {} in guild {}",
                member.getEffectiveName(), channelJoined.getName(), guild.getName());

        // Create temp channel
        TempVoiceChannel tempChannel = autoVoiceService.createTempChannel(guild, member, channelJoined);

        if (tempChannel == null) {
            log.warn("Failed to create temp channel for user {}", member.getId());
            return;
        }

        // Move user to the new temp channel
        try {
            VoiceChannel tempVoiceChannel = guild.getVoiceChannelById(tempChannel.getChannelId());
            if (tempVoiceChannel != null) {
                guild.moveVoiceMember(member, tempVoiceChannel).queue(
                        success -> log.info("Moved user {} to temp channel {}",
                                member.getEffectiveName(), tempChannel.getChannelName()),
                        error -> log.error("Failed to move user {} to temp channel: {}",
                                member.getEffectiveName(), error.getMessage())
                );
            }
        } catch (Exception e) {
            log.error("Error moving user to temp channel: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle user leaving a voice channel.
     * If it's a temp channel and now empty, delete it.
     */
    private void handleChannelLeave(GuildVoiceUpdateEvent event, AudioChannel channelLeft) {
        Guild guild = event.getGuild();
        String channelId = channelLeft.getId();

        // Check if this is a temp voice channel
        Optional<TempVoiceChannel> tempChannelOpt = autoVoiceService.getTempChannelByChannelId(channelId);

        if (tempChannelOpt.isEmpty()) {
            return; // Not a temp channel, ignore
        }

        TempVoiceChannel tempChannel = tempChannelOpt.get();

        // Check if the channel is now empty
        // Use a small delay to allow the member list to update
        try {
            Thread.sleep(100); // Small delay for JDA cache to update
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Re-fetch the channel to get updated member list
        AudioChannel currentChannel = guild.getVoiceChannelById(channelId);
        if (currentChannel == null) {
            // Channel already deleted externally
            autoVoiceService.deleteTempChannel(guild, channelId);
            return;
        }

        if (autoVoiceService.shouldDeleteChannel(currentChannel)) {
            log.info("Temp channel {} is empty, scheduling deletion", channelId);

            // Delete after a short delay to prevent immediate deletion on quick reconnections
            try {
                Thread.sleep(2000); // 2 second grace period

                // Re-check if still empty
                AudioChannel recheckChannel = guild.getVoiceChannelById(channelId);
                if (recheckChannel != null && autoVoiceService.shouldDeleteChannel(recheckChannel)) {
                    autoVoiceService.deleteTempChannel(guild, channelId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}