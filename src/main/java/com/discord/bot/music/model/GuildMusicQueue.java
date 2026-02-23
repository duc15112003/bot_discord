package com.discord.bot.music.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Per-guild music queue state.
 * Holds the track queue, current playing track, and play history.
 * Thread-safe through synchronized access in GuildMusicManager.
 */
public class GuildMusicQueue {

    private static final int MAX_HISTORY_SIZE = 50;

    private final Queue<TrackInfo> queue = new LinkedList<>();
    private final Deque<TrackInfo> history = new ArrayDeque<>();
    private TrackInfo currentTrack;
    private boolean paused;

    public synchronized void enqueue(TrackInfo track) {
        queue.offer(track);
    }

    public synchronized TrackInfo dequeue() {
        return queue.poll();
    }

    public synchronized TrackInfo peek() {
        return queue.peek();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized TrackInfo getCurrentTrack() {
        return currentTrack;
    }

    public synchronized void setCurrentTrack(TrackInfo track) {
        if (this.currentTrack != null) {
            pushToHistory(this.currentTrack);
        }
        this.currentTrack = track;
    }

    public synchronized void pushToHistory(TrackInfo track) {
        if (track != null) {
            history.push(track);
            while (history.size() > MAX_HISTORY_SIZE) {
                history.removeLast();
            }
        }
    }

    public synchronized TrackInfo popFromHistory() {
        return history.isEmpty() ? null : history.pop();
    }

    public synchronized boolean hasHistory() {
        return !history.isEmpty();
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized void setPaused(boolean paused) {
        this.paused = paused;
    }

    public synchronized Queue<TrackInfo> getQueueSnapshot() {
        return new LinkedList<>(queue);
    }
}
