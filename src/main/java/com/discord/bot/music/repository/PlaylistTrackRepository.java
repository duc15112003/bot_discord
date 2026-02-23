package com.discord.bot.music.repository;

import com.discord.bot.music.entity.PlaylistTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for PlaylistTrack entities.
 */
@Repository
public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, Long> {

    List<PlaylistTrack> findByPlaylistIdOrderByPositionAsc(Long playlistId);

    void deleteByPlaylistIdAndUri(Long playlistId, String uri);

    int countByPlaylistId(Long playlistId);
}
