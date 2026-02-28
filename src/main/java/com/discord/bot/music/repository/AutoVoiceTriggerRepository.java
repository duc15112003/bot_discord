package com.discord.bot.music.repository;

import com.discord.bot.music.entity.AutoVoiceTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for AutoVoiceTrigger entities.
 */
@Repository
public interface AutoVoiceTriggerRepository extends JpaRepository<AutoVoiceTrigger, Long> {

    /**
     * Find trigger configuration by guild and channel ID.
     */
    Optional<AutoVoiceTrigger> findByGuildIdAndTriggerChannelId(String guildId, String triggerChannelId);

    /**
     * Find all trigger configurations for a guild.
     */
    List<AutoVoiceTrigger> findByGuildId(String guildId);

    /**
     * Check if a channel is configured as a trigger.
     */
    boolean existsByGuildIdAndTriggerChannelIdAndEnabledTrue(String guildId, String triggerChannelId);

    /**
     * Find all enabled triggers for a guild.
     */
    List<AutoVoiceTrigger> findByGuildIdAndEnabledTrue(String guildId);
}