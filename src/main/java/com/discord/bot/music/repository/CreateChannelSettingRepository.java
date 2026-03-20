package com.discord.bot.music.repository;

import com.discord.bot.music.entity.CreateChannelSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for persisted create-channel settings.
 */
@Repository
public interface CreateChannelSettingRepository extends JpaRepository<CreateChannelSetting, Long> {
    Optional<CreateChannelSetting> findByGuildId(Long guildId);

    boolean existsByGuildId(Long guildId);
}
