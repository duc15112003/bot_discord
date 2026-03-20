package com.discord.bot.music.command;

import com.discord.bot.music.entity.CreateChannelSetting;
import com.discord.bot.music.repository.CreateChannelSettingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores create channel configuration per guild.
 * Persisted in the database so it survives restarts.
 */
@Component
public class CreateChannelConfig {
    private final CreateChannelSettingRepository repository;

    public CreateChannelConfig(CreateChannelSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void setCreateChannelId(long guildId, long channelId) {
        CreateChannelSetting setting = repository.findByGuildId(guildId)
                .orElseGet(() -> CreateChannelSetting.builder()
                        .guildId(guildId)
                        .build());
        setting.setChannelId(channelId);
        repository.save(setting);
    }

    @Transactional(readOnly = true)
    public long getCreateChannelId(long guildId) {
        return repository.findByGuildId(guildId)
                .map(CreateChannelSetting::getChannelId)
                .orElse(-1L);
    }

    @Transactional(readOnly = true)
    public boolean hasCreateChannel(long guildId) {
        return repository.existsByGuildId(guildId);
    }
}

