package com.discord.bot.music.audio;

import dev.arbjerg.lavalink.client.LavalinkClient;
import net.dv8tion.jda.api.JDA;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single bot instance (JDA + LavalinkClient pair).
 * Each instance can connect to one voice channel per guild.
 */
public class BotInstance {

    private final JDA jda;
    private final LavalinkClient lavalinkClient;
    private final boolean primary;
    private final int index;

    /**
     * Tracks which channel this bot is connected to in each guild.
     * Key: guildId, Value: channelId
     */
    private final Map<Long, Long> connectedChannels = new ConcurrentHashMap<>();

    public BotInstance(JDA jda, LavalinkClient lavalinkClient, boolean primary, int index) {
        this.jda = jda;
        this.lavalinkClient = lavalinkClient;
        this.primary = primary;
        this.index = index;
    }

    public JDA getJda() {
        return jda;
    }

    public LavalinkClient getLavalinkClient() {
        return lavalinkClient;
    }

    public boolean isPrimary() {
        return primary;
    }

    public int getIndex() {
        return index;
    }

    public String getBotName() {
        return jda.getSelfUser().getName();
    }

    public long getBotId() {
        return jda.getSelfUser().getIdLong();
    }

    /**
     * Check if this bot is available to join a channel in the given guild.
     */
    public boolean isAvailableForGuild(long guildId) {
        return !connectedChannels.containsKey(guildId);
    }

    /**
     * Check if this bot is connected to a specific channel.
     */
    public boolean isInChannel(long guildId, long channelId) {
        Long connected = connectedChannels.get(guildId);
        return connected != null && connected == channelId;
    }

    /**
     * Mark this bot as connected to a channel in a guild.
     */
    public void markConnected(long guildId, long channelId) {
        connectedChannels.put(guildId, channelId);
    }

    /**
     * Release this bot from a guild.
     */
    public void markDisconnected(long guildId) {
        connectedChannels.remove(guildId);
    }

    /**
     * Get the channel this bot is connected to in a guild, or null if not
     * connected.
     */
    public Long getConnectedChannel(long guildId) {
        return connectedChannels.get(guildId);
    }

    /**
     * Get the OAuth2 invite URL for this bot.
     */
    public String getInviteUrl() {
        return "https://discord.com/api/oauth2/authorize?client_id=" + getBotId()
                + "&permissions=36727824&scope=bot%20applications.commands";
    }
}
