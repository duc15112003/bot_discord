package com.discord.bot.music.repository;

import com.discord.bot.music.entity.TempVoiceChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for TempVoiceChannel entities.
 */
@Repository
public interface TempVoiceChannelRepository extends JpaRepository<TempVoiceChannel, Long> {

    /**
     * Find a temp voice channel by its Discord channel ID.
     */
    Optional<TempVoiceChannel> findByChannelId(String channelId);

    /**
     * Find all temp voice channels for a specific guild.
     */
    List<TempVoiceChannel> findByGuildId(String guildId);

    /**
     * Find all temp voice channels owned by a specific user in a guild.
     */
    Optional<TempVoiceChannel> findByGuildIdAndOwnerId(String guildId, String ownerId);

    /**
     * Check if a user already owns a temp voice channel in a guild.
     */
    boolean existsByGuildIdAndOwnerId(String guildId, String ownerId);

    /**
     * Find all temp voice channels created before a specific time.
     */
    List<TempVoiceChannel> findByCreatedAtBefore(LocalDateTime dateTime);

    /**
     * Delete a temp voice channel by its Discord channel ID.
     */
    @Modifying
    @Query("DELETE FROM TempVoiceChannel t WHERE t.channelId = :channelId")
    int deleteByChannelId(String channelId);

    /**
     * Count temp voice channels for a guild.
     */
    long countByGuildId(String guildId);
}