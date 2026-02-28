package com.discord.bot.music.command;

import com.discord.bot.music.entity.AutoVoiceTrigger;
import com.discord.bot.music.service.AutoVoiceService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.ChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Slash commands for managing the auto-voice system.
 * Provides admin commands for setup and user commands for channel management.
 */
@Component
public class AutoVoiceCommand implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(AutoVoiceCommand.class);

    private final AutoVoiceService autoVoiceService;

    public AutoVoiceCommand(AutoVoiceService autoVoiceService) {
        this.autoVoiceService = autoVoiceService;
    }

    @Override
    public String getName() {
        return "autovoice";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("autovoice", "Manage automatic voice channel creation")
                // Setup subcommand - admin only
                .addSubcommands(
                        new SubcommandData("setup", "Configure a trigger channel for auto-voice (Admin)")
                                .addOption(OptionType.CHANNEL, "trigger", "The voice channel to use as trigger", true)
                                .addOption(OptionType.CHANNEL, "category", "Category to create temp channels in", false)
                                .addOption(OptionType.INTEGER, "maxusers", "Maximum users per temp channel (0 = unlimited)", false),
                        new SubcommandData("remove", "Remove a trigger channel configuration (Admin)")
                                .addOption(OptionType.CHANNEL, "trigger", "The trigger channel to remove", true),
                        new SubcommandData("list", "List all auto-voice trigger channels"),
                        new SubcommandData("lock", "Lock your temp voice channel (Owner only)"),
                        new SubcommandData("unlock", "Unlock your temp voice channel (Owner only)"),
                        new SubcommandData("rename", "Rename your temp voice channel (Owner only)")
                                .addOption(OptionType.STRING, "name", "New name for your channel", true),
                        new SubcommandData("limit", "Set user limit for your temp voice channel (Owner only)")
                                .addOption(OptionType.INTEGER, "max", "Maximum users (0 = unlimited)", true),
                        new SubcommandData("info", "Show info about your current temp voice channel")
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("❌ Invalid subcommand.").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "setup" -> handleSetup(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
            case "lock" -> handleLock(event);
            case "unlock" -> handleUnlock(event);
            case "rename" -> handleRename(event);
            case "limit" -> handleLimit(event);
            case "info" -> handleInfo(event);
            default -> event.reply("❌ Unknown subcommand.").setEphemeral(true).queue();
        }
    }

    /**
     * Handle /autovoice setup - Admin only.
     * Configure a trigger channel for auto-voice.
     */
    private void handleSetup(SlashCommandInteractionEvent event) {
        // Check admin permission
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permission to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        // Get trigger channel
        var triggerChannelOption = event.getOption("trigger");
        if (triggerChannelOption == null) {
            event.reply("❌ Please specify a trigger channel.").setEphemeral(true).queue();
            return;
        }

        ChannelUnion triggerChannelUnion = (ChannelUnion) triggerChannelOption.getAsChannel();
        if (!triggerChannelUnion.getType().isAudio()) {
            event.reply("❌ The trigger channel must be a voice channel.").setEphemeral(true).queue();
            return;
        }

        VoiceChannel triggerChannel = triggerChannelUnion.asVoiceChannel();
        String categoryId = null;
        Integer maxUsers = null;

        // Get optional category
        var categoryOption = event.getOption("category");
        if (categoryOption != null) {
            categoryId = categoryOption.getAsChannel().getId();
        }

        // Get optional max users
        var maxUsersOption = event.getOption("maxusers");
        if (maxUsersOption != null) {
            maxUsers = maxUsersOption.getAsInt();
        }

        try {
            AutoVoiceTrigger trigger = autoVoiceService.configureTriggerChannel(
                    guild, triggerChannel.getId(), categoryId, maxUsers);

            StringBuilder response = new StringBuilder()
                    .append("✅ **Auto-Voice Trigger Configured**\n\n")
                    .append("**Trigger Channel:** ").append(triggerChannel.getAsMention()).append("\n");

            if (categoryId != null) {
                var category = guild.getCategoryById(categoryId);
                response.append("**Category:** ").append(category != null ? category.getName() : "Unknown").append("\n");
            }

            response.append("**Max Users:** ").append(maxUsers != null && maxUsers > 0 ? maxUsers : "Unlimited").append("\n\n")
                    .append("When users join the trigger channel, a temporary voice channel will be created for them.");

            event.reply(response.toString()).queue();

            log.info("Auto-voice trigger configured: guild={}, channel={}, category={}, maxUsers={}",
                    guild.getId(), triggerChannel.getId(), categoryId, maxUsers);

        } catch (IllegalArgumentException e) {
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("Failed to configure auto-voice trigger", e);
            event.reply("❌ Failed to configure trigger channel: " + e.getMessage())
                    .setEphemeral(true).queue();
        }
    }

    /**
     * Handle /autovoice remove - Admin only.
     * Remove a trigger channel configuration.
     */
    private void handleRemove(SlashCommandInteractionEvent event) {
        // Check admin permission
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permission to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        var triggerChannelOption = event.getOption("trigger");
        if (triggerChannelOption == null) {
            event.reply("❌ Please specify a trigger channel.").setEphemeral(true).queue();
            return;
        }

        ChannelUnion triggerChannelUnion = (ChannelUnion) triggerChannelOption.getAsChannel();
        if (!triggerChannelUnion.getType().isAudio()) {
            event.reply("❌ The trigger channel must be a voice channel.").setEphemeral(true).queue();
            return;
        }

        VoiceChannel triggerChannel = triggerChannelUnion.asVoiceChannel();

        boolean removed = autoVoiceService.removeTriggerChannel(guild.getId(), triggerChannel.getId());

        if (removed) {
            event.reply("✅ Removed trigger channel configuration for " + triggerChannel.getAsMention()).queue();
        } else {
            event.reply("❌ This channel is not configured as a trigger channel.")
                    .setEphemeral(true).queue();
        }
    }

    /**
     * Handle /autovoice list.
     * List all trigger channels for the guild.
     */
    private void handleList(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        List<AutoVoiceTrigger> triggers = autoVoiceService.getTriggerChannels(guild.getId());
        long activeTempChannels = autoVoiceService.getActiveTempChannelCount(guild.getId());

        if (triggers.isEmpty()) {
            event.reply("📋 No auto-voice trigger channels configured.\n\n" +
                    "Use `/autovoice setup` to configure one.").setEphemeral(true).queue();
            return;
        }

        StringBuilder response = new StringBuilder()
                .append("📋 **Auto-Voice Configuration**\n\n")
                .append("**Active Temp Channels:** ").append(activeTempChannels).append("\n\n")
                .append("**Trigger Channels:**\n");

        for (AutoVoiceTrigger trigger : triggers) {
            var channel = guild.getVoiceChannelById(trigger.getTriggerChannelId());
            var category = trigger.getCategoryId() != null ? guild.getCategoryById(trigger.getCategoryId()) : null;

            response.append("• ");
            if (channel != null) {
                response.append(channel.getAsMention());
            } else {
                response.append("`").append(trigger.getTriggerChannelId()).append("` (deleted)");
            }

            if (category != null) {
                response.append(" → Category: ").append(category.getName());
            }

            if (trigger.getMaxUserLimit() != null && trigger.getMaxUserLimit() > 0) {
                response.append(" | Max: ").append(trigger.getMaxUserLimit());
            }

            response.append(trigger.getEnabled() ? " ✅" : " ⚠️ Disabled");
            response.append("\n");
        }

        event.reply(response.toString()).queue();
    }

    /**
     * Handle /autovoice lock - Owner only.
     * Lock a temp voice channel.
     */
    private void handleLock(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        // Check if user is in a voice channel
        var voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("❌ You must be in a voice channel to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        String channelId = voiceState.getChannel().getId();
        String channelName = voiceState.getChannel().getName();

        // Check if this is a temporary channel
        if (!autoVoiceService.isTemporaryChannel(channelId)) {
            event.reply("❌ This is not a temporary voice channel.")
                    .setEphemeral(true).queue();
            return;
        }

        // Check if user is the owner
        if (!autoVoiceService.isMemberChannelOwner(member, channelId)) {
            event.reply("❌ Only the channel owner can lock this channel.")
                    .setEphemeral(true).queue();
            return;
        }

        // For database-stored temp channels, check lock status and lock
        var tempChannelOpt = autoVoiceService.getTempChannelByChannelId(channelId);
        if (tempChannelOpt.isPresent()) {
            var tempChannel = tempChannelOpt.get();

            if (tempChannel.getIsLocked()) {
                event.reply("⚠️ This channel is already locked.").setEphemeral(true).queue();
                return;
            }

            autoVoiceService.lockChannel(guild, channelId);
            event.reply("🔒 Your channel has been locked. Only you can join now.").queue();
        } else {
            // For VoiceChannelListener-created channels, lock it by changing permissions
            var voiceChannel = (net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel) voiceState.getChannel();
            if (voiceChannel != null) {
                voiceChannel.getManager()
                        .putPermissionOverride(member, java.util.EnumSet.of(net.dv8tion.jda.api.Permission.VOICE_CONNECT),
                                java.util.EnumSet.of(net.dv8tion.jda.api.Permission.VOICE_CONNECT))
                        .putPermissionOverride(guild.getPublicRole(),
                                java.util.EnumSet.noneOf(net.dv8tion.jda.api.Permission.class),
                                java.util.EnumSet.of(net.dv8tion.jda.api.Permission.VOICE_CONNECT))
                        .queue(
                            success -> event.reply("🔒 Your channel has been locked. Only you can join now.").queue(),
                            error -> event.reply("❌ Failed to lock channel: " + error.getMessage()).setEphemeral(true).queue()
                        );
            }
        }
    }

    /**
     * Handle /autovoice unlock - Owner only.
     * Unlock a temp voice channel.
     */
    private void handleUnlock(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        var voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("❌ You must be in a voice channel to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        String channelId = voiceState.getChannel().getId();
        String channelName = voiceState.getChannel().getName();

        // Check if this is a temporary channel
        if (!autoVoiceService.isTemporaryChannel(channelId)) {
            event.reply("❌ This is not a temporary voice channel.")
                    .setEphemeral(true).queue();
            return;
        }

        // Check if user is the owner
        if (!autoVoiceService.isMemberChannelOwner(member, channelId)) {
            event.reply("❌ Only the channel owner can unlock this channel.")
                    .setEphemeral(true).queue();
            return;
        }

        // For database-stored temp channels, check lock status and unlock
        var tempChannelOpt = autoVoiceService.getTempChannelByChannelId(channelId);
        if (tempChannelOpt.isPresent()) {
            var tempChannel = tempChannelOpt.get();

            if (!tempChannel.getIsLocked()) {
                event.reply("⚠️ This channel is not locked.").setEphemeral(true).queue();
                return;
            }

            autoVoiceService.unlockChannel(guild, channelId);
            event.reply("🔓 Your channel has been unlocked. Anyone can join now.").queue();
        } else {
            // For VoiceChannelListener-created channels, they don't have lock status in DB
            // Just notify user
            event.reply("ℹ️ This is a temporary channel. It will be automatically deleted when empty.").queue();
        }
    }

    /**
     * Handle /autovoice rename - Owner only.
     * Rename a temp voice channel.
     */
    private void handleRename(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        var voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("❌ You must be in a voice channel to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        var nameOption = event.getOption("name");
        if (nameOption == null) {
            event.reply("❌ Please provide a new name for the channel.").setEphemeral(true).queue();
            return;
        }

        String newName = nameOption.getAsString();
        if (newName.length() > 100) {
            event.reply("❌ Channel name must be 100 characters or less.").setEphemeral(true).queue();
            return;
        }

        String channelId = voiceState.getChannel().getId();

        // Check if this is a temporary channel
        if (!autoVoiceService.isTemporaryChannel(channelId)) {
            event.reply("❌ This is not a temporary voice channel.")
                    .setEphemeral(true).queue();
            return;
        }

        // Check if user is the owner
        if (!autoVoiceService.isMemberChannelOwner(member, channelId)) {
            event.reply("❌ Only the channel owner can rename this channel.")
                    .setEphemeral(true).queue();
            return;
        }

        // For database-stored channels, use service method
        var tempChannelOpt = autoVoiceService.getTempChannelByChannelId(channelId);
        if (tempChannelOpt.isPresent()) {
            boolean success = autoVoiceService.renameChannel(guild, channelId, newName, member.getId());
            if (success) {
                event.reply("✏️ Channel renamed to **" + newName + "**").queue();
            } else {
                event.reply("❌ Failed to rename channel.").setEphemeral(true).queue();
            }
        } else {
            // For VoiceChannelListener-created channels, rename directly
            var voiceChannel = (net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel) voiceState.getChannel();
            if (voiceChannel != null) {
                voiceChannel.getManager().setName(newName).queue(
                    success -> event.reply("✏️ Channel renamed to **" + newName + "**").queue(),
                    error -> event.reply("❌ Failed to rename channel: " + error.getMessage()).setEphemeral(true).queue()
                );
            }
        }
    }

    /**
     * Handle /autovoice limit - Owner only.
     * Set user limit for a temp voice channel.
     */
    private void handleLimit(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        var voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("❌ You must be in a voice channel to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        var maxOption = event.getOption("max");
        if (maxOption == null) {
            event.reply("❌ Please provide a maximum user limit.").setEphemeral(true).queue();
            return;
        }

        int limit = maxOption.getAsInt();
        String channelId = voiceState.getChannel().getId();

        // Check if this is a temporary channel
        if (!autoVoiceService.isTemporaryChannel(channelId)) {
            event.reply("❌ This is not a temporary voice channel.")
                    .setEphemeral(true).queue();
            return;
        }

        // Check if user is the owner
        if (!autoVoiceService.isMemberChannelOwner(member, channelId)) {
            event.reply("❌ Only the channel owner can set user limit.")
                    .setEphemeral(true).queue();
            return;
        }

        // For database-stored channels, use service method
        var tempChannelOpt = autoVoiceService.getTempChannelByChannelId(channelId);
        if (tempChannelOpt.isPresent()) {
            boolean success = autoVoiceService.setUserLimit(guild, channelId, limit, member.getId());
            if (success) {
                String limitText = limit > 0 ? String.valueOf(limit) : "unlimited";
                event.reply("👥 User limit set to **" + limitText + "**").queue();
            } else {
                event.reply("❌ Failed to set user limit.").setEphemeral(true).queue();
            }
        } else {
            // For VoiceChannelListener-created channels, set user limit directly
            var voiceChannel = (net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel) voiceState.getChannel();
            if (voiceChannel != null) {
                voiceChannel.getManager().setUserLimit(limit).queue(
                    success -> {
                        String limitText = limit > 0 ? String.valueOf(limit) : "unlimited";
                        event.reply("👥 User limit set to **" + limitText + "**").queue();
                    },
                    error -> event.reply("❌ Failed to set user limit: " + error.getMessage()).setEphemeral(true).queue()
                );
            }
        }
    }

    /**
     * Handle /autovoice info.
     * Show info about the user's temp voice channel.
     */
    private void handleInfo(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        var voiceState = member.getVoiceState();

        // First try: Check if user is in a voice channel (supports both types)
        if (voiceState != null && voiceState.inAudioChannel()) {
            String channelId = voiceState.getChannel().getId();
            String channelName = voiceState.getChannel().getName();

            // Check if this is a temporary channel
            if (!autoVoiceService.isTemporaryChannel(channelId)) {
                event.reply("❌ You are not in a temporary voice channel.")
                        .setEphemeral(true).queue();
                return;
            }

            // Check if user is the owner
            if (!autoVoiceService.isMemberChannelOwner(member, channelId)) {
                event.reply("❌ You are not the owner of this channel.")
                        .setEphemeral(true).queue();
                return;
            }

            // Try to get info from database
            var tempChannelOpt = autoVoiceService.getTempChannelByChannelId(channelId);

            StringBuilder response = new StringBuilder()
                    .append("📋 **Your Temporary Voice Channel**\n\n")
                    .append("**Channel:** <#").append(channelId).append(">\n")
                    .append("**Name:** ").append(channelName).append("\n");

            if (tempChannelOpt.isPresent()) {
                // Database-stored channel
                var tempChannel = tempChannelOpt.get();
                response.append("**Locked:** ").append(tempChannel.getIsLocked() ? "Yes 🔒" : "No").append("\n")
                        .append("**User Limit:** ").append(tempChannel.getUserLimit() > 0 ? tempChannel.getUserLimit() : "Unlimited").append("\n")
                        .append("**Created:** <t:").append(tempChannel.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC)).append(":R>").append("\n\n");
            } else {
                // VoiceChannelListener-created channel
                response.append("**Type:** Temporary Channel (Auto-created)\n")
                        .append("**Locked:** No\n")
                        .append("**Info:** This channel will be automatically deleted when empty.\n\n");
            }

            response.append("**Commands:**\n")
                    .append("• `/autovoice lock` - Lock your channel\n")
                    .append("• `/autovoice unlock` - Unlock your channel\n")
                    .append("• `/autovoice rename` - Rename your channel\n")
                    .append("• `/autovoice limit` - Set user limit");

            event.reply(response.toString()).setEphemeral(true).queue();
            return;
        }

        // Second try: Check if user owns a temp channel in database (fallback for offline/not in voice)
        var tempChannelOpt = autoVoiceService.getTempChannelByOwner(guild.getId(), member.getId());

        if (tempChannelOpt.isEmpty()) {
            event.reply("📋 You don't own a temporary voice channel.\n\n" +
                    "Join a trigger channel to create one, or join your temp channel to see info!")
                    .setEphemeral(true).queue();
            return;
        }

        var tempChannel = tempChannelOpt.get();
        var channel = guild.getVoiceChannelById(tempChannel.getChannelId());

        StringBuilder response = new StringBuilder()
                .append("📋 **Your Temporary Voice Channel**\n\n")
                .append("**Channel:** ").append(channel != null ? channel.getAsMention() : "Deleted").append("\n")
                .append("**Name:** ").append(tempChannel.getChannelName()).append("\n")
                .append("**Locked:** ").append(tempChannel.getIsLocked() ? "Yes 🔒" : "No").append("\n")
                .append("**User Limit:** ").append(tempChannel.getUserLimit() > 0 ? tempChannel.getUserLimit() : "Unlimited").append("\n")
                .append("**Created:** <t:").append(tempChannel.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC)).append(":R>").append("\n\n")
                .append("**Commands:**\n")
                .append("• `/autovoice lock` - Lock your channel\n")
                .append("• `/autovoice unlock` - Unlock your channel\n")
                .append("• `/autovoice rename` - Rename your channel\n")
                .append("• `/autovoice limit` - Set user limit");

        event.reply(response.toString()).setEphemeral(true).queue();
    }
}