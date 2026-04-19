package com.discord.bot.music.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds playback strings for /playsearch select menus until picked or TTL expires.
 */
@Component
public class PlaySearchSessionStore {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String create(String requesterUserId, List<PlaySearchTrack> tracks) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(requesterUserId, List.copyOf(tracks), System.currentTimeMillis()));
        return id;
    }

    public boolean canUse(String sessionId, String userId) {
        Session s = sessions.get(sessionId);
        return s != null && !isExpired(s) && s.requesterUserId.equals(userId);
    }

    /**
     * Removes the session and returns the chosen row (primary query + search fallback).
     */
    public PlaySearchTrack takeTrack(String sessionId, int index) {
        Session s = sessions.remove(sessionId);
        if (s == null || isExpired(s)) {
            throw new IllegalStateException("Menu expired or already used.");
        }
        if (index < 0 || index >= s.tracks.size()) {
            throw new IllegalStateException("Invalid selection.");
        }
        return s.tracks.get(index);
    }

    private boolean isExpired(Session s) {
        return System.currentTimeMillis() - s.createdAtMs > TTL.toMillis();
    }

    private record Session(String requesterUserId, List<PlaySearchTrack> tracks, long createdAtMs) {}
}
