package com.discord.bot.music.command;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores create channel configuration per guild.
 * Maps guildId -> createChannelId
 */
@Component
public class CreateChannelConfig {
    private final Map<Long, Long> guildCreateChannels = new HashMap<>();

    public void setCreateChannelId(long guildId, long channelId) {
        guildCreateChannels.put(guildId, channelId);
    }

    public long getCreateChannelId(long guildId) {
        return guildCreateChannels.getOrDefault(guildId, -1L);
    }

    public boolean hasCreateChannel(long guildId) {
        return guildCreateChannels.containsKey(guildId);
    }
}

