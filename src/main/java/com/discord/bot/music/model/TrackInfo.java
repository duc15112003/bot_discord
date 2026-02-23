package com.discord.bot.music.model;

import dev.arbjerg.lavalink.client.player.Track;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO holding metadata for a single audio track.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackInfo {

    private String title;
    private String author;
    private String uri;
    private long durationMs;
    private String requesterId;
    private String requesterName;
    /** The encoded track string from Lavalink, used to replay the track */
    private String encoded;
    /** The Lavalink Track object, used for player API calls */
    private transient Track lavalinkTrack;
}
