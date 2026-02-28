package com.discord.bot.music.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a temporary voice channel created by the auto-voice system.
 * Tracks ownership and lifecycle of dynamically created voice channels.
 */
@Entity
@Table(name = "temp_voice_channels", indexes = {
        @Index(name = "idx_temp_voice_channel_id", columnList = "channel_id"),
        @Index(name = "idx_temp_voice_guild_owner", columnList = "guild_id, owner_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempVoiceChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "channel_id", nullable = false, unique = true)
    private String channelId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "trigger_channel_id", nullable = false)
    private String triggerChannelId;

    @Column(name = "channel_name")
    private String channelName;

    @Column(name = "user_limit")
    @Builder.Default
    private Integer userLimit = 0;

    @Column(name = "is_locked")
    @Builder.Default
    private Boolean isLocked = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}