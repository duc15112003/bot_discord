package com.discord.bot.music.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing trigger channel configuration for auto-voice.
 * Allows admins to configure which channels trigger temporary voice creation.
 */
@Entity
@Table(name = "auto_voice_triggers", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "guild_id", "trigger_channel_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoVoiceTrigger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "trigger_channel_id", nullable = false)
    private String triggerChannelId;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "max_user_limit")
    @Builder.Default
    private Integer maxUserLimit = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}