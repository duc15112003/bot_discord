package com.discord.bot.music.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages a pool of bot instances for multi-channel music playback.
 * Assigns available bots to voice channels and tracks their state.
 */
@Component
public class BotInstancePool {

    private static final Logger log = LoggerFactory.getLogger(BotInstancePool.class);

    private final List<BotInstance> instances = new CopyOnWriteArrayList<>();

    /**
     * Register a bot instance in the pool.
     */
    public void register(BotInstance instance) {
        instances.add(instance);
        log.info("Registered bot instance #{} ({}): {} [{}]",
                instance.getIndex(),
                instance.isPrimary() ? "primary" : "secondary",
                instance.getBotName(),
                instance.getBotId());
    }

    /**
     * Find a bot already assigned to the given channel, or assign a free one.
     *
     * @return BotInstance or null if no bots are available
     */
    public BotInstance findOrAssignBot(long guildId, long channelId) {
        // 1. Check if a bot is already in this channel
        for (BotInstance bot : instances) {
            if (bot.isInChannel(guildId, channelId)) {
                return bot;
            }
        }

        // 2. Find a free bot for this guild
        for (BotInstance bot : instances) {
            if (bot.isAvailableForGuild(guildId)) {
                bot.markConnected(guildId, channelId);
                log.info("Assigned bot #{} ({}) to guild {} channel {}",
                        bot.getIndex(), bot.getBotName(), guildId, channelId);
                return bot;
            }
        }

        // 3. No bots available
        log.warn("No available bot instances for guild {} channel {}", guildId, channelId);
        return null;
    }

    /**
     * Get the bot that is assigned to a specific channel.
     */
    public BotInstance getBotInChannel(long guildId, long channelId) {
        for (BotInstance bot : instances) {
            if (bot.isInChannel(guildId, channelId)) {
                return bot;
            }
        }
        return null;
    }

    /**
     * Release a bot from a guild channel.
     */
    public void releaseBot(long guildId, long channelId) {
        for (BotInstance bot : instances) {
            if (bot.isInChannel(guildId, channelId)) {
                bot.markDisconnected(guildId);
                log.info("Released bot #{} ({}) from guild {} channel {}",
                        bot.getIndex(), bot.getBotName(), guildId, channelId);
                return;
            }
        }
    }

    /**
     * Get all registered bot instances.
     */
    public List<BotInstance> getAllInstances() {
        return List.copyOf(instances);
    }

    /**
     * Get number of available bots for a guild.
     */
    public int getAvailableCount(long guildId) {
        return (int) instances.stream()
                .filter(bot -> bot.isAvailableForGuild(guildId))
                .count();
    }

    /**
     * Total number of bot instances.
     */
    public int getTotalCount() {
        return instances.size();
    }
}
