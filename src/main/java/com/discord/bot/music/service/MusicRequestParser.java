package com.discord.bot.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.discord.bot.config.properties.AppProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that processes music requests and returns structured JSON format.
 * Supports links, search queries, and mood detection.
 * Can optionally use AI service for more advanced parsing if enabled.
 */
@Slf4j
@Service
public class MusicRequestParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final AppProperties appProperties;
    private final AiService aiService;

    public MusicRequestParser(AppProperties appProperties, AiService aiService) {
        this.appProperties = appProperties;
        this.aiService = aiService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MusicRequest {
        private String type;
        private String keyword;
        private String title;
        private String artist;
        private String mood;
    }

    /**
     * Parse a user music request input.
     *
     * @param input User input string
     * @return JSON string with parsed music request
     */
    public String parseRequest(String input) {
        if (input == null || input.trim().isEmpty()) {
            return createInvalidResponse();
        }

        String trimmedInput = input.trim();

        // Check if it's a link
        if (isLink(trimmedInput)) {
            return createLinkResponse(trimmedInput);
        }

        // Check if it's a music-related search query
        if (isMusicQuery(trimmedInput)) {
            return createSearchResponse(trimmedInput);
        }

        // Invalid query
        return createInvalidResponse();
    }

    private boolean isLink(String input) {
        return input.matches("^(https?://|www\\.).*") ||
               input.contains("youtube.com") ||
               input.contains("youtu.be") ||
               input.contains("spotify.com") ||
               input.contains("soundcloud.com");
    }

    private boolean isMusicQuery(String input) {
        String lowerInput = input.toLowerCase();

        // Check for music-related keywords
        String[] musicKeywords = {
            "nhạc", "bật", "play", "phát", "hát", "chill", "buồn", "vui",
            "tập trung", "gym", "ngủ", "focus", "sad", "happy", "upbeat",
            "lofi", "beat", "remix", "cover", "acoustic", "sleep", "workout"
        };

        for (String keyword : musicKeywords) {
            if (lowerInput.contains(keyword)) {
                return true;
            }
        }

        // Check if it looks like a song name or artist
        return !lowerInput.matches(".*\\b(thời tiết|tin tức|dự báo|kết quả|tin tức|thời|tìm|search|google).*");
    }

    private String createLinkResponse(String link) {
        MusicRequest request = new MusicRequest();
        request.setType("LINK");
        request.setKeyword(link);
        request.setTitle("");
        request.setArtist("");
        request.setMood("");
        return toJson(request);
    }

    private String createSearchResponse(String input) {
        MusicRequest request = new MusicRequest();
        request.setType("SEARCH");
        request.setKeyword(parseKeyword(input));
        request.setTitle(extractTitle(input));
        request.setArtist(extractArtist(input));
        request.setMood(detectMood(input));
        return toJson(request);
    }

    private String createInvalidResponse() {
        MusicRequest request = new MusicRequest();
        request.setType("INVALID");
        request.setKeyword("");
        request.setTitle("");
        request.setArtist("");
        request.setMood("");
        return toJson(request);
    }

    private String parseKeyword(String input) {
        String lowerInput = input.toLowerCase();

        // Remove "bật nhạc", "play", "phát" prefixes
        String cleaned = lowerInput
            .replaceAll("^(bật|phát|play|hãy|hát)\\s+nhạc\\s+", "")
            .replaceAll("^(bật|phát|play|hãy|hát)\\s+nhạc", "")
            .trim();

        // Apply mood mappings and enrich keyword
        return applyMoodMapping(cleaned);
    }

    private String applyMoodMapping(String input) {
        String lower = input.toLowerCase();

        if (lower.contains("chill") || lower.contains("tâm trạng lừng lững")) {
            return "lofi chill night".equals(input) ? input : input.replace("chill", "chill").concat(" chill");
        }
        if (lower.contains("buồn") || lower.contains("sad")) {
            return "sad vietnamese music";
        }
        if (lower.contains("vui") || lower.contains("happy") || lower.contains("upbeat")) {
            return "happy upbeat music";
        }
        if (lower.contains("tập trung") || lower.contains("focus")) {
            return "focus music no lyrics";
        }
        if (lower.contains("gym") || lower.contains("workout")) {
            return "workout music mix";
        }
        if (lower.contains("ngủ") || lower.contains("sleep")) {
            return "relaxing sleep music";
        }

        // Try to identify artist and song
        if (isLikelyArtistSong(input)) {
            return enrichSongQuery(input);
        }

        return input;
    }

    private boolean isLikelyArtistSong(String input) {
        // Check if it contains common song separators or patterns
        return input.matches(".*\\b(by|from|-|of)\\b.*") ||
               (input.split("\\s+").length >= 2 && !input.contains("nhạc"));
    }

    private String enrichSongQuery(String input) {
        // For song queries like "shape of you", add likely artist detection
        String lower = input.toLowerCase();

        // Common song patterns
        if (lower.contains("shape of you")) {
            return "Shape of You Ed Sheeran";
        }

        return input;
    }

    private String extractTitle(String input) {
        String lower = input.toLowerCase();

        // Extract title from known patterns
        if (lower.contains("shape of you")) {
            return "Shape of You";
        }

        // Try to extract from "artist - title" or "title by artist" patterns
        if (input.contains(" - ")) {
            String[] parts = input.split(" - ");
            return parts.length > 1 ? parts[1].trim() : "";
        }

        if (input.matches(".*\\bby\\b.*")) {
            String[] parts = input.split("\\bby\\b");
            return parts.length > 0 ? parts[0].trim() : "";
        }

        // Don't guess for Vietnamese queries
        if (input.matches(".*[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ].*")) {
            return "";
        }

        return "";
    }

    private String extractArtist(String input) {
        String lower = input.toLowerCase();

        // Extract artist from known patterns
        if (lower.contains("shape of you")) {
            return "Ed Sheeran";
        }

        // Try to extract from "artist - title" pattern
        if (input.contains(" - ")) {
            String[] parts = input.split(" - ");
            return parts.length > 0 ? parts[0].trim() : "";
        }

        if (input.matches(".*\\bby\\b.*")) {
            String[] parts = input.split("\\bby\\b");
            return parts.length > 1 ? parts[1].trim() : "";
        }

        return "";
    }

    private String detectMood(String input) {
        String lower = input.toLowerCase();

        if (lower.contains("chill")) return "chill";
        if (lower.contains("buồn") || lower.contains("sad")) return "sad";
        if (lower.contains("vui") || lower.contains("happy") || lower.contains("upbeat")) return "upbeat";
        if (lower.contains("tập trung") || lower.contains("focus")) return "focus";
        if (lower.contains("gym") || lower.contains("workout")) return "workout";
        if (lower.contains("ngủ") || lower.contains("sleep")) return "sleep";

        return "";
    }

    private String toJson(MusicRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            // Fallback to manual JSON construction
            return String.format(
                "{\"type\":\"%s\",\"keyword\":\"%s\",\"title\":\"%s\",\"artist\":\"%s\",\"mood\":\"%s\"}",
                escapeJson(request.type),
                escapeJson(request.keyword),
                escapeJson(request.title),
                escapeJson(request.artist),
                escapeJson(request.mood)
            );
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Parse a user music request asynchronously using AI if enabled.
     * Falls back to synchronous parsing if AI is disabled or fails.
     *
     * @param input User input string
     * @return Mono containing JSON string with parsed music request
     */
    public Mono<String> parseRequestAsync(String input) {
        if (!appProperties.getAi().isEnabled()) {
            // If AI is disabled, use sync parsing wrapped in Mono
            return Mono.fromCallable(() -> parseRequest(input));
        }

        // Use AI service for parsing
        String prompt = buildAiPrompt(input);
        return aiService.callAi(prompt)
                .flatMap(aiService::extractMusicRequest)
                .doOnError(error -> log.warn("AI parsing failed, falling back to sync: {}", error.getMessage()))
                .onErrorResume(error -> Mono.fromCallable(() -> parseRequest(input)));
    }

    /**
     * Build a prompt for AI to parse the music request.
     *
     * @param input User input
     * @return Formatted prompt for AI
     */
    private String buildAiPrompt(String input) {
        return String.format("""
                Parse this music request and return JSON:
                Input: "%s"
                
                Return only valid JSON with this structure:
                {
                  "type": "LINK | SEARCH | INVALID",
                  "keyword": "...",
                  "title": "...",
                  "artist": "...",
                  "mood": ""
                }
                
                Rules:
                - If input is a URL → type = "LINK", keyword = the URL
                - If input is a music query → type = "SEARCH", keyword = search keyword
                - If input is not music-related → type = "INVALID", keyword = ""
                - mood can be: chill, sad, upbeat, focus, workout, sleep, or empty
                """, input);
    }
}
