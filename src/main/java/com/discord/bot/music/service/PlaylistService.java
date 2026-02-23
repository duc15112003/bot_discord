package com.discord.bot.music.service;

import com.discord.bot.music.entity.Playlist;
import com.discord.bot.music.entity.PlaylistTrack;
import com.discord.bot.music.model.TrackInfo;
import com.discord.bot.music.repository.PlaylistRepository;
import com.discord.bot.music.repository.PlaylistTrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Database-backed playlist service for user playlist management.
 */
@Service
@Transactional
public class PlaylistService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistService.class);

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;

    public PlaylistService(PlaylistRepository playlistRepository,
            PlaylistTrackRepository playlistTrackRepository) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
    }

    /**
     * Add a track to a user's playlist. Creates the playlist if it doesn't exist.
     */
    public String addTrack(String userId, String playlistName, TrackInfo trackInfo) {
        if (trackInfo == null) {
            return "❌ No track is currently playing. Play a track first!";
        }

        Playlist playlist = playlistRepository.findByUserIdAndName(userId, playlistName)
                .orElseGet(() -> {
                    Playlist newPlaylist = Playlist.builder()
                            .userId(userId)
                            .name(playlistName)
                            .build();
                    return playlistRepository.save(newPlaylist);
                });

        int nextPosition = playlistTrackRepository.countByPlaylistId(playlist.getId()) + 1;

        PlaylistTrack track = PlaylistTrack.builder()
                .playlist(playlist)
                .title(trackInfo.getTitle())
                .uri(trackInfo.getUri())
                .author(trackInfo.getAuthor())
                .durationMs(trackInfo.getDurationMs())
                .position(nextPosition)
                .build();

        playlistTrackRepository.save(track);

        log.info("Added track '{}' to playlist '{}' for user {}", trackInfo.getTitle(), playlistName, userId);

        return "✅ Added **" + trackInfo.getTitle() + "** to playlist **" + playlistName
                + "** (Track #" + nextPosition + ")";
    }

    /**
     * List all playlists for a user with track counts.
     */
    @Transactional(readOnly = true)
    public String listPlaylists(String userId) {
        List<Playlist> playlists = playlistRepository.findByUserId(userId);

        if (playlists.isEmpty()) {
            return "📋 You don't have any playlists yet. Use `/playlist-add` to create one!";
        }

        StringBuilder sb = new StringBuilder("📋 **Your Playlists:**\n\n");
        for (int i = 0; i < playlists.size(); i++) {
            Playlist pl = playlists.get(i);
            int trackCount = playlistTrackRepository.countByPlaylistId(pl.getId());
            sb.append(String.format("`%d.` **%s** — %d track%s\n",
                    i + 1, pl.getName(), trackCount, trackCount == 1 ? "" : "s"));

            // Show tracks in this playlist
            List<PlaylistTrack> tracks = playlistTrackRepository
                    .findByPlaylistIdOrderByPositionAsc(pl.getId());
            for (PlaylistTrack track : tracks) {
                long minutes = track.getDurationMs() / 60000;
                long seconds = (track.getDurationMs() % 60000) / 1000;
                sb.append(String.format("   ↳ `%d.` %s — %s (`%d:%02d`)\n",
                        track.getPosition(), track.getTitle(), track.getAuthor(),
                        minutes, seconds));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Remove a track from a user's playlist by track position.
     */
    public String removeTrack(String userId, String playlistName, int trackPosition) {
        Optional<Playlist> playlistOpt = playlistRepository.findByUserIdAndName(userId, playlistName);

        if (playlistOpt.isEmpty()) {
            return "❌ Playlist **" + playlistName + "** not found.";
        }

        Playlist playlist = playlistOpt.get();
        List<PlaylistTrack> tracks = playlistTrackRepository
                .findByPlaylistIdOrderByPositionAsc(playlist.getId());

        if (trackPosition < 1 || trackPosition > tracks.size()) {
            return "❌ Invalid track position. Use `/playlist-list` to see track positions.";
        }

        PlaylistTrack trackToRemove = tracks.get(trackPosition - 1);
        String trackTitle = trackToRemove.getTitle();
        playlistTrackRepository.delete(trackToRemove);

        // Re-order remaining tracks
        List<PlaylistTrack> remaining = playlistTrackRepository
                .findByPlaylistIdOrderByPositionAsc(playlist.getId());
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i + 1);
        }
        playlistTrackRepository.saveAll(remaining);

        // Delete playlist if empty
        if (remaining.isEmpty()) {
            playlistRepository.delete(playlist);
            return "🗑️ Removed **" + trackTitle + "** and deleted empty playlist **" + playlistName + "**.";
        }

        return "🗑️ Removed **" + trackTitle + "** from playlist **" + playlistName + "**.";
    }
}
