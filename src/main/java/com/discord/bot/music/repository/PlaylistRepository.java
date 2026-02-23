package com.discord.bot.music.repository;

import com.discord.bot.music.entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Playlist entities.
 */
@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByUserId(String userId);

    Optional<Playlist> findByUserIdAndName(String userId, String name);

    boolean existsByUserIdAndName(String userId, String name);
}
