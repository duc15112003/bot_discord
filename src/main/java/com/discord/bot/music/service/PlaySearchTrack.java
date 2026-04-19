package com.discord.bot.music.service;

/**
 * One row for the Discord string select menu + Lavalink playback string.
 *
 * @param searchFallback If the primary {@code playbackQuery} fails (bad URL, etc.), Lavalink retries with this as ytsearch.
 */
public record PlaySearchTrack(String menuLabel, String menuDescription, String playbackQuery, String searchFallback) {
}
