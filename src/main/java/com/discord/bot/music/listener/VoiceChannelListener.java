package com.discord.bot.music.listener;

import com.discord.bot.music.command.CreateChannelConfig;
import com.discord.bot.music.service.AutoVoiceService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listens for voice channel events.
 * Auto-disconnects and cleans up when the bot is alone in a voice channel.
 * Creates temporary voice channels when users join the configured create
 * channel.
 */
@Component
public class VoiceChannelListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(VoiceChannelListener.class);

    private final CreateChannelConfig createChannelConfig;
    private final AutoVoiceService autoVoiceService;

    public VoiceChannelListener(CreateChannelConfig createChannelConfig, AutoVoiceService autoVoiceService) {
        this.createChannelConfig = createChannelConfig;
        this.autoVoiceService = autoVoiceService;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        long guildId = event.getGuild().getIdLong();

        // [Multi-Bot] If the person moving is a bot, check if it just left a channel
        if (event.getMember().getUser().isBot()) {
            if (event.getChannelLeft() != null) {
                long channelId = event.getChannelLeft().getIdLong();
                // Release the bot in our pool
                com.discord.bot.music.audio.GuildMusicManager gmm = findGuildMusicManager();
                if (gmm != null) {
                    com.discord.bot.music.audio.BotInstance bot = gmm.getBotPool().getBotInChannel(guildId, channelId);
                    if (bot != null && bot.getBotId() == event.getMember().getIdLong()) {
                        log.info("🤖 Bot {} left channel {} in guild {}. Releasing back to pool.",
                                bot.getBotName(), channelId, guildId);
                        gmm.cleanup(guildId, channelId);
                    }
                }
            }
        }

        // Handle user joining the "create channel"
        if (event.getChannelJoined() != null && createChannelConfig.hasCreateChannel(guildId)) {
            long createChannelId = createChannelConfig.getCreateChannelId(guildId);
            if (event.getChannelJoined().getIdLong() == createChannelId) {
                handleCreateChannelJoin(event);
                return;
            }
        }

        // Handle temporary channel cleanup when user leaves
        AudioChannelUnion channelLeft = event.getChannelLeft();
        if (channelLeft == null)
            return;

        // Check if this is a temporary channel
        if (!autoVoiceService.isTemporaryChannel(channelLeft.getId())) {
            return;
        }

        // Check if channel is now empty (no humans remain)
        long humanCount = channelLeft.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();

        if (humanCount == 0) {
            log.info("Temporary voice channel {} is now empty, deleting...", channelLeft.getName());
            autoVoiceService.unregisterVoiceChannelOwner(channelLeft.getId());
            channelLeft.delete().queue(
                    success -> log.info("Deleted empty temporary voice channel: {}", channelLeft.getName()),
                    error -> log.error("Failed to delete temporary voice channel: {}", channelLeft.getName(), error));
        }
    }

    /**
     * Helper to find music manager without circular dependency issues if possible.
     * We'll need to inject it or use ApplicationContext.
     */
    private com.discord.bot.music.audio.GuildMusicManager findGuildMusicManager() {
        // We'll need to inject this in the constructor, but let's see if we can do it
        // via Spring
        return springContext.getBean(com.discord.bot.music.audio.GuildMusicManager.class);
    }

    private org.springframework.context.ApplicationContext springContext;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSpringContext(org.springframework.context.ApplicationContext springContext) {
        this.springContext = springContext;
    }

    private void handleCreateChannelJoin(GuildVoiceUpdateEvent event) {
        var createChannel = (net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel) event.getChannelJoined();
        var category = createChannel.getParentCategory();

        event.getMember().getGuild().createVoiceChannel(event.getMember().getEffectiveName() + "'s Channel")
                .setParent(category)
                .addPermissionOverride(event.getMember(),
                        java.util.EnumSet.of(Permission.MANAGE_CHANNEL, Permission.VOICE_SPEAK,
                                Permission.VOICE_CONNECT),
                        null)
                // Allow everyone to connect and speak (public channel)
                .addPermissionOverride(event.getGuild().getPublicRole(),
                        java.util.EnumSet.of(Permission.VOICE_CONNECT, Permission.VOICE_SPEAK), null)
                .queue(voiceChannel -> {
                    // Register channel owner in AutoVoiceService
                    autoVoiceService.registerVoiceChannelOwner(voiceChannel.getId(), event.getMember().getId());

                    event.getGuild().moveVoiceMember(event.getMember(), voiceChannel).queue(
                            success -> log.info("Moved {} to new voice channel {}",
                                    event.getMember().getEffectiveName(), voiceChannel.getName()),
                            error -> log.error("Failed to move {} to new voice channel {}: {}",
                                    event.getMember().getEffectiveName(), voiceChannel.getName(), error.getMessage()));
                }, error -> log.error("Failed to create temporary voice channel for {}: {}",
                        event.getMember().getEffectiveName(), error.getMessage()));
    }
}
