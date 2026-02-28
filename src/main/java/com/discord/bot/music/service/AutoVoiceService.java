package com.discord.bot.music.service;

import com.discord.bot.music.entity.AutoVoiceTrigger;
import com.discord.bot.music.entity.TempVoiceChannel;
import com.discord.bot.music.repository.AutoVoiceTriggerRepository;
import com.discord.bot.music.repository.TempVoiceChannelRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.Permission;

/**
 * Service for managing automatic temporary voice channels.
 * Handles creation, deletion, ownership, and configuration of temp voice channels.
 */
@Service
@Transactional
public class AutoVoiceService {

    private static final Logger log = LoggerFactory.getLogger(AutoVoiceService.class);

    private final TempVoiceChannelRepository tempVoiceChannelRepository;
    private final AutoVoiceTriggerRepository autoVoiceTriggerRepository;

    // In-memory locks to prevent duplicate channel creation (guild_userId -> lock)
    private final ConcurrentHashMap<String, Object> creationLocks = new ConcurrentHashMap<>();

    // Cooldown tracking (userId -> last creation time)
    private final ConcurrentHashMap<String, Long> creationCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 5000; // 5 second cooldown

    // Track owner of VoiceChannelListener-created channels (channelId -> ownerId)
    private final ConcurrentHashMap<String, String> voiceChannelOwners = new ConcurrentHashMap<>();

    public AutoVoiceService(TempVoiceChannelRepository tempVoiceChannelRepository,
                           AutoVoiceTriggerRepository autoVoiceTriggerRepository) {
        this.tempVoiceChannelRepository = tempVoiceChannelRepository;
        this.autoVoiceTriggerRepository = autoVoiceTriggerRepository;
    }

    /**
     * Check if a channel is configured as a trigger channel for auto-voice.
     */
    @Transactional(readOnly = true)
    public boolean isTriggerChannel(String guildId, String channelId) {
        return autoVoiceTriggerRepository.existsByGuildIdAndTriggerChannelIdAndEnabledTrue(guildId, channelId);
    }

    /**
     * Get trigger configuration for a channel.
     */
    @Transactional(readOnly = true)
    public Optional<AutoVoiceTrigger> getTriggerConfig(String guildId, String channelId) {
        return autoVoiceTriggerRepository.findByGuildIdAndTriggerChannelId(guildId, channelId);
    }

    /**
     * Check if user is on cooldown for creating a temp channel.
     */
    public boolean isOnCooldown(String userId) {
        Long lastCreation = creationCooldowns.get(userId);
        if (lastCreation == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastCreation) < COOLDOWN_MS;
    }

    /**
     * Check if user already owns a temp voice channel in this guild.
     */
    @Transactional(readOnly = true)
    public boolean userHasTempChannel(String guildId, String ownerId) {
        return tempVoiceChannelRepository.existsByGuildIdAndOwnerId(guildId, ownerId);
    }

    /**
     * Get temp voice channel by Discord channel ID.
     */
    @Transactional(readOnly = true)
    public Optional<TempVoiceChannel> getTempChannelByChannelId(String channelId) {
        return tempVoiceChannelRepository.findByChannelId(channelId);
    }

    /**
     * Get temp voice channel owned by a user in a guild.
     */
    @Transactional(readOnly = true)
    public Optional<TempVoiceChannel> getTempChannelByOwner(String guildId, String ownerId) {
        return tempVoiceChannelRepository.findByGuildIdAndOwnerId(guildId, ownerId);
    }

    /**
     * Create a temporary voice channel for a user.
     * Uses per-user locks to prevent duplicate creation.
     */
    public TempVoiceChannel createTempChannel(Guild guild, Member owner, AudioChannel triggerChannel) {
        String guildId = guild.getId();
        String ownerId = owner.getId();
        String lockKey = guildId + "_" + ownerId;

        // Use per-user lock to prevent duplicate creation
        Object lock = creationLocks.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
            try {
                // Double-check inside lock
                if (userHasTempChannel(guildId, ownerId)) {
                    log.debug("User {} already owns a temp channel in guild {}", ownerId, guildId);
                    return null;
                }

                // Check cooldown
                if (isOnCooldown(ownerId)) {
                    log.debug("User {} is on cooldown for temp channel creation", ownerId);
                    return null;
                }

                // Get trigger configuration
                Optional<AutoVoiceTrigger> triggerOpt = getTriggerConfig(guildId, triggerChannel.getId());
                String categoryId = triggerOpt.map(AutoVoiceTrigger::getCategoryId).orElse(null);
                Integer maxUserLimit = triggerOpt.map(AutoVoiceTrigger::getMaxUserLimit).orElse(0);

                // Build channel name
                String channelName = owner.getEffectiveName() + "'s Room";

                // Create voice channel
                ChannelAction<VoiceChannel> action = guild.createVoiceChannel(channelName);

                // Set parent category if configured
                if (categoryId != null && !categoryId.isBlank()) {
                    try {
                        action = action.setParent(guild.getCategoryById(categoryId));
                    } catch (Exception e) {
                        log.warn("Could not set category {} for temp channel: {}", categoryId, e.getMessage());
                    }
                }

                // Set user limit (0 = unlimited)
                if (maxUserLimit != null && maxUserLimit > 0) {
                    action = action.setUserlimit(maxUserLimit);
                }

                // Create the channel
                VoiceChannel newChannel = action.complete();
                String newChannelId = newChannel.getId();

                // Save to database
                TempVoiceChannel tempChannel = TempVoiceChannel.builder()
                        .guildId(guildId)
                        .channelId(newChannelId)
                        .ownerId(ownerId)
                        .triggerChannelId(triggerChannel.getId())
                        .channelName(channelName)
                        .userLimit(maxUserLimit != null ? maxUserLimit : 0)
                        .isLocked(false)
                        .build();

                tempChannel = tempVoiceChannelRepository.save(tempChannel);

                // Set cooldown
                creationCooldowns.put(ownerId, System.currentTimeMillis());

                log.info("Created temp voice channel {} for user {} in guild {}",
                        newChannelId, ownerId, guildId);

                return tempChannel;

            } catch (Exception e) {
                log.error("Failed to create temp voice channel for user {}: {}", ownerId, e.getMessage(), e);
                return null;
            } finally {
                // Clean up lock entry after a delay
                creationLocks.remove(lockKey);
            }
        }
    }

    /**
     * Delete a temporary voice channel and its database record.
     */
    public void deleteTempChannel(Guild guild, String channelId) {
        try {
            Optional<TempVoiceChannel> tempChannelOpt = tempVoiceChannelRepository.findByChannelId(channelId);

            if (tempChannelOpt.isEmpty()) {
                log.debug("No temp channel record found for channel {}", channelId);
                return;
            }

            TempVoiceChannel tempChannel = tempChannelOpt.get();

            // Delete the Discord channel
            VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
            if (voiceChannel != null) {
                voiceChannel.delete().queue(
                        success -> log.info("Deleted temp voice channel {} in guild {}", channelId, guild.getId()),
                        error -> log.warn("Failed to delete voice channel {}: {}", channelId, error.getMessage())
                );
            }

            // Delete from database
            tempVoiceChannelRepository.delete(tempChannel);
            log.info("Deleted temp voice channel record {} for owner {}", channelId, tempChannel.getOwnerId());

        } catch (Exception e) {
            log.error("Error deleting temp voice channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    /**
     * Check if a channel is empty and should be deleted.
     */
    public boolean shouldDeleteChannel(AudioChannel channel) {
        if (channel == null) {
            return false;
        }

        // Count non-bot members in the channel
        long humanCount = channel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();

        return humanCount == 0;
    }

    /**
     * Transfer ownership of a temp channel to another user.
     */
    public boolean transferOwnership(String channelId, String newOwnerId) {
        Optional<TempVoiceChannel> tempChannelOpt = tempVoiceChannelRepository.findByChannelId(channelId);

        if (tempChannelOpt.isEmpty()) {
            return false;
        }

        TempVoiceChannel tempChannel = tempChannelOpt.get();
        tempChannel.setOwnerId(newOwnerId);
        tempVoiceChannelRepository.save(tempChannel);

        log.info("Transferred ownership of temp channel {} to user {}", channelId, newOwnerId);
        return true;
    }

    /**
     * Lock a temp channel to only allow the owner.
     */
    public void lockChannel(Guild guild, String channelId) {
        Optional<TempVoiceChannel> tempChannelOpt = tempVoiceChannelRepository.findByChannelId(channelId);

        if (tempChannelOpt.isEmpty()) {
            return;
        }

        TempVoiceChannel tempChannel = tempChannelOpt.get();
        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);

        if (voiceChannel == null) {
            return;
        }

        try {
            // Deny everyone from connecting, except the owner
            VoiceChannelManager manager = voiceChannel.getManager();

            // Deny @everyone connect permission
            manager = manager.putPermissionOverride(
                    guild.getPublicRole(),
                    null,
                    EnumSet.of(Permission.VOICE_CONNECT)
            );

            // Allow owner full permissions
            Member owner = guild.getMemberById(tempChannel.getOwnerId());
            if (owner != null) {
                manager = manager.putPermissionOverride(
                        owner,
                        EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL),
                        null
                );
            }

            manager.queue();

            tempChannel.setIsLocked(true);
            tempVoiceChannelRepository.save(tempChannel);

            log.info("Locked temp channel {} for owner {}", channelId, tempChannel.getOwnerId());

        } catch (Exception e) {
            log.error("Failed to lock temp channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    /**
     * Unlock a temp channel.
     */
    public void unlockChannel(Guild guild, String channelId) {
        Optional<TempVoiceChannel> tempChannelOpt = tempVoiceChannelRepository.findByChannelId(channelId);

        if (tempChannelOpt.isEmpty()) {
            return;
        }

        TempVoiceChannel tempChannel = tempChannelOpt.get();
        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);

        if (voiceChannel == null) {
            return;
        }

        try {
            VoiceChannelManager manager = voiceChannel.getManager();

            // Reset @everyone permissions
            manager = manager.putPermissionOverride(
                    guild.getPublicRole(),
                    EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL),
                    null
            );

            manager.queue();

            tempChannel.setIsLocked(false);
            tempVoiceChannelRepository.save(tempChannel);

            log.info("Unlocked temp channel {}", channelId);

        } catch (Exception e) {
            log.error("Failed to unlock temp channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    /**
     * Rename a temp channel (owner only).
     */
    public boolean renameChannel(Guild guild, String channelId, String newName, String requesterId) {
        Optional<TempVoiceChannel> tempChannelOpt = tempVoiceChannelRepository.findByChannelId(channelId);

        if (tempChannelOpt.isEmpty()) {
            return false;
        }

        TempVoiceChannel tempChannel = tempChannelOpt.get();

        // Only owner can rename
        if (!tempChannel.getOwnerId().equals(requesterId)) {
            log.debug("User {} attempted to rename channel {} owned by {}", requesterId, channelId, tempChannel.getOwnerId());
            return false;
        }

        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
        if (voiceChannel == null) {
            return false;
        }

        try {
            voiceChannel.getManager().setName(newName).queue();
            tempChannel.setChannelName(newName);
            tempVoiceChannelRepository.save(tempChannel);

            log.info("Renamed temp channel {} to '{}'", channelId, newName);
            return true;

        } catch (Exception e) {
            log.error("Failed to rename temp channel {}: {}", channelId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Set user limit for a temp channel (owner only).
     */
    public boolean setUserLimit(Guild guild, String channelId, int limit, String requesterId) {
        Optional<TempVoiceChannel> tempChannelOpt = tempVoiceChannelRepository.findByChannelId(channelId);

        if (tempChannelOpt.isEmpty()) {
            return false;
        }

        TempVoiceChannel tempChannel = tempChannelOpt.get();

        // Only owner can change limit
        if (!tempChannel.getOwnerId().equals(requesterId)) {
            return false;
        }

        VoiceChannel voiceChannel = guild.getVoiceChannelById(channelId);
        if (voiceChannel == null) {
            return false;
        }

        try {
            voiceChannel.getManager()
                    .setUserLimit(limit)
                    .queue();
            tempChannel.setUserLimit(limit);
            tempVoiceChannelRepository.save(tempChannel);

            log.info("Set user limit for temp channel {} to {}", channelId, limit);
            return true;

        } catch (Exception e) {
            log.error("Failed to set user limit for temp channel {}: {}", channelId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Configure a trigger channel for auto-voice.
     */
    public AutoVoiceTrigger configureTriggerChannel(Guild guild, String triggerChannelId, String categoryId, Integer maxUserLimit) {
        // Verify the trigger channel exists
        if (guild.getVoiceChannelById(triggerChannelId) == null) {
            throw new IllegalArgumentException("Trigger channel not found: " + triggerChannelId);
        }

        // Verify category exists if provided
        if (categoryId != null && !categoryId.isBlank() && guild.getCategoryById(categoryId) == null) {
            throw new IllegalArgumentException("Category not found: " + categoryId);
        }

        AutoVoiceTrigger trigger = autoVoiceTriggerRepository
                .findByGuildIdAndTriggerChannelId(guild.getId(), triggerChannelId)
                .orElseGet(() -> AutoVoiceTrigger.builder()
                        .guildId(guild.getId())
                        .triggerChannelId(triggerChannelId)
                        .build());

        if (categoryId != null) {
            trigger.setCategoryId(categoryId);
        }
        if (maxUserLimit != null) {
            trigger.setMaxUserLimit(maxUserLimit);
        }
        trigger.setEnabled(true);

        return autoVoiceTriggerRepository.save(trigger);
    }

    /**
     * Remove a trigger channel configuration.
     */
    public boolean removeTriggerChannel(String guildId, String triggerChannelId) {
        Optional<AutoVoiceTrigger> triggerOpt = autoVoiceTriggerRepository
                .findByGuildIdAndTriggerChannelId(guildId, triggerChannelId);

        if (triggerOpt.isPresent()) {
            autoVoiceTriggerRepository.delete(triggerOpt.get());
            log.info("Removed trigger channel {} from guild {}", triggerChannelId, guildId);
            return true;
        }
        return false;
    }

    /**
     * Get all trigger channels for a guild.
     */
    @Transactional(readOnly = true)
    public List<AutoVoiceTrigger> getTriggerChannels(String guildId) {
        return autoVoiceTriggerRepository.findByGuildId(guildId);
    }

    /**
     * Clean up orphaned database records (channels that no longer exist in Discord).
     */
    @Transactional
    public int cleanupOrphanedRecords(Guild guild) {
        List<TempVoiceChannel> channels = tempVoiceChannelRepository.findByGuildId(guild.getId());
        int cleaned = 0;

        for (TempVoiceChannel channel : channels) {
            if (guild.getVoiceChannelById(channel.getChannelId()) == null) {
                tempVoiceChannelRepository.delete(channel);
                cleaned++;
                log.info("Cleaned up orphaned temp channel record: {}", channel.getChannelId());
            }
        }

        return cleaned;
    }

    /**
     * Get count of active temp channels in a guild.
     */
    @Transactional(readOnly = true)
    public long getActiveTempChannelCount(String guildId) {
        return tempVoiceChannelRepository.countByGuildId(guildId);
    }

    /**
     * Check if a member owns a temporary voice channel.
     * Supports both database-stored channels (AutoVoice system) and
     * VoiceChannelListener-created channels (tracked via voiceChannelOwners).
     *
     * @param member the member to check
     * @param channelId the Discord ID of the voice channel
     * @return true if the member owns the temporary channel, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isMemberChannelOwner(Member member, String channelId) {
        // Check if it's a database-stored temp channel
        var tempChannelOpt = getTempChannelByChannelId(channelId);
        if (tempChannelOpt.isPresent()) {
            return tempChannelOpt.get().getOwnerId().equals(member.getId());
        }

        // Check if it's a VoiceChannelListener-created channel (tracked)
        String trackedOwnerId = getVoiceChannelOwner(channelId);
        if (trackedOwnerId != null) {
            return trackedOwnerId.equals(member.getId());
        }

        return false;
    }

    /**
     * Check if a voice channel is a temporary channel.
     * Supports both database-stored channels and VoiceChannelListener-created channels.
     *
     * @param channelId the Discord ID of the voice channel
     * @return true if the channel is a temporary channel, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isTemporaryChannel(String channelId) {
        // Check database
        if (getTempChannelByChannelId(channelId).isPresent()) {
            return true;
        }

        // Check if tracked as VoiceChannelListener-created channel
        return voiceChannelOwners.containsKey(channelId);
    }

    /**
     * Register a VoiceChannelListener-created channel owner.
     * Used to track channels created by VoiceChannelListener.
     *
     * @param channelId the Discord channel ID
     * @param ownerId the Discord user ID of the owner
     */
    public void registerVoiceChannelOwner(String channelId, String ownerId) {
        voiceChannelOwners.put(channelId, ownerId);
        log.debug("Registered voice channel {} owner: {}", channelId, ownerId);
    }

    /**
     * Unregister a VoiceChannelListener-created channel.
     * Called when the channel is deleted.
     *
     * @param channelId the Discord channel ID
     */
    public void unregisterVoiceChannelOwner(String channelId) {
        voiceChannelOwners.remove(channelId);
        log.debug("Unregistered voice channel owner for: {}", channelId);
    }

    /**
     * Get the owner ID of a VoiceChannelListener-created channel.
     *
     * @param channelId the Discord channel ID
     * @return the owner ID, or null if not found
     */
    public String getVoiceChannelOwner(String channelId) {
        return voiceChannelOwners.get(channelId);
    }
}