package com.discord.bot.music.service;

import com.discord.bot.config.properties.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Uses Gemini (via {@link AiService} / WebFlux) to suggest several tracks; user picks one in Discord UI.
 */
@Slf4j
@Service
public class MusicPlaySearchService {

    /** Discord string select: label, description, value max length */
    private static final int DISCORD_TEXT_MAX = 100;

    private static final int AI_MAX_OUTPUT_TOKENS = 2048;

    private final AiService aiService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public MusicPlaySearchService(AiService aiService, AppProperties appProperties, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns several menu rows; playback uses {@link MusicService#playAsyncWithYoutubeSearchFallback}
     * so a bad AI URL can fall back to ytsearch via label/description.
     */
    public Mono<List<PlaySearchTrack>> resolveTrackChoices(String context) {
        if (!appProperties.getAi().isEnabled()) {
            return Mono.error(new IllegalStateException("AI service is disabled"));
        }
        if (context == null || context.isBlank()) {
            return Mono.error(new IllegalArgumentException("Context is empty"));
        }
        String prompt = buildTrackListPrompt(context.trim());
        return aiService.callAi(prompt, AI_MAX_OUTPUT_TOKENS)
                .flatMap(aiService::extractMusicRequest)
                .flatMap(json -> Mono.fromCallable(() -> parseTrackChoices(json))
                        .doOnError(e -> log.warn("Could not parse playsearch JSON: {}", json)));
    }

    private String buildTrackListPrompt(String context) {
        return """
                You are a music assistant. The user describes what they want to listen to.
                Respond with ONLY one JSON object (no markdown fences, no commentary) using this exact shape:
                {"tracks":[{"label":"","description":"","url":"","keyword":""},...]}

                Rules:
                - Provide between 3 and 8 distinct suggestions (never more than 25).
                - "label": Short title for a menu row (max 80 characters).
                - "description": Artist or short hint (max 80 characters), may be empty "".
                - Each track must have either a confident https YouTube watch/youtu.be URL in "url", OR a non-empty "keyword" for search (song + artist).
                - Never invent fake YouTube video IDs. Default to empty "url" and a strong "keyword" unless the URL is widely known — bad links break playback.
                - Use varied, relevant suggestions for the user's context.

                User context:
                %s
                """.formatted(context);
    }

    private List<PlaySearchTrack> parseTrackChoices(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        if (root.has("tracks") && root.get("tracks").isArray()) {
            List<PlaySearchTrack> out = new ArrayList<>();
            for (JsonNode t : root.get("tracks")) {
                String playback = playbackFromTrackNode(t);
                if (playback == null || playback.isBlank()) {
                    continue;
                }
                String label = truncDiscord(textOrEmpty(t, "label"));
                if (label.isEmpty()) {
                    label = truncDiscord(playback);
                }
                String desc = truncDiscord(textOrEmpty(t, "description"));
                out.add(buildPlaySearchTrack(label, desc, playback));
            }
            if (!out.isEmpty()) {
                return capList(out, 25);
            }
        }

        String single = parseLegacySinglePlayback(root);
        if (single != null && !single.isBlank()) {
            String label = truncDiscord(single);
            return List.of(buildPlaySearchTrack(label, "", single));
        }

        throw new IllegalStateException("AI did not return usable tracks");
    }

    private static List<PlaySearchTrack> capList(List<PlaySearchTrack> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        return list.subList(0, max);
    }

    private PlaySearchTrack buildPlaySearchTrack(String menuLabel, String menuDescription, String playbackQuery) {
        return new PlaySearchTrack(menuLabel, menuDescription, playbackQuery,
                searchFallbackFor(menuLabel, menuDescription, playbackQuery));
    }

    /**
     * Text used when primary URL/query fails Lavalink — usually "Title Artist" from the menu.
     */
    private static String searchFallbackFor(String label, String description, String playbackQuery) {
        StringBuilder sb = new StringBuilder();
        if (label != null && !label.isBlank()) {
            sb.append(label.trim());
        }
        if (description != null && !description.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(description.trim());
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        if (playbackQuery != null && !playbackQuery.startsWith("http")) {
            return playbackQuery.trim();
        }
        return "";
    }

    private String playbackFromTrackNode(JsonNode t) {
        String url = textOrEmpty(t, "url");
        if (!url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
            return url;
        }
        return textOrEmpty(t, "keyword");
    }

    private String parseLegacySinglePlayback(JsonNode node) throws Exception {
        String url = textOrEmpty(node, "url");
        if (!url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
            return url;
        }
        String keyword = textOrEmpty(node, "keyword");
        if (!keyword.isEmpty()) {
            return keyword;
        }
        if (node.has("type") && node.has("keyword")) {
            String type = node.get("type").asText("");
            String kw = textOrEmpty(node, "keyword");
            if ("LINK".equals(type) && !kw.isEmpty() && kw.startsWith("http")) {
                return kw;
            }
            if ("SEARCH".equals(type) && !kw.isEmpty()) {
                return kw;
            }
        }
        return null;
    }

    private static String truncDiscord(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() <= DISCORD_TEXT_MAX) {
            return s;
        }
        return s.substring(0, DISCORD_TEXT_MAX - 1) + "…";
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("").trim();
    }
}
