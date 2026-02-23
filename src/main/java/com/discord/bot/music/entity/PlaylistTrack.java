package com.discord.bot.music.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity representing a single track within a user's playlist.
 */
@Entity
@Table(name = "playlist_tracks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaylistTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String uri;

    private String author;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(nullable = false)
    private int position;
}
