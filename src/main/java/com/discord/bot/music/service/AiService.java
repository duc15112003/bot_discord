package com.discord.bot.music.service;

import com.discord.bot.config.properties.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * AI service that calls Gemini API using WebFlux for async processing.
 */
@Slf4j
@Service
public class AiService {

    private static final String DEFAULT_GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private final WebClient webClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public AiService(WebClient webClient, AppProperties appProperties) {
        this.webClient = webClient;
        this.appProperties = appProperties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Call AI API to process a music request.
     * This is wrapped by MusicRequestParser for initial parsing,
     * but can be used for more complex AI processing if needed.
     *
     * @param prompt The prompt to send to the AI
     * @return Mono containing the AI response
     */
    public Mono<String> callAi(String prompt) {
        return callAi(prompt, null);
    }

    /**
     * @param maxOutputTokens if null, uses default (500); use a higher value for larger JSON payloads.
     */
    public Mono<String> callAi(String prompt, Integer maxOutputTokens) {
        AppProperties.Ai aiConfig = appProperties.getAi();

        if (!aiConfig.isEnabled()) {
            log.warn("AI service is disabled");
            return Mono.error(new IllegalStateException("AI service is disabled"));
        }

        Map<String, Object> request = buildAiRequest(prompt, maxOutputTokens);
        String uri = buildApiUri(aiConfig);

        return webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", aiConfig.getApiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(aiConfig.getTimeoutMs()))
                .retryWhen(Retry.backoff(Math.max(3, aiConfig.getRetryAttempts()), Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .jitter(0.2)
                        .filter(this::isRetryable))
                .doOnError(error -> log.error("Error calling Gemini API: {}", error.toString(), error))
                .onErrorMap(this::mapError);
    }

    private String buildApiUri(AppProperties.Ai aiConfig) {
        String url = aiConfig.getApiUrl();
        return (url != null && !url.isBlank()) ? url : DEFAULT_GEMINI_URL;
    }

    /**
     * Extract music request JSON from AI response.
     *
     * @param aiResponse The response from AI API
     * @return Mono containing the extracted JSON
     */
    public Mono<String> extractMusicRequest(String aiResponse) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode responseNode = objectMapper.readTree(aiResponse);
                String content = extractContentFromAiResponse(responseNode);

                int jsonStart = content.indexOf('{');
                int jsonEnd = content.lastIndexOf('}');

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    return content.substring(jsonStart, jsonEnd + 1);
                }

                return content;
            } catch (Exception e) {
                log.error("Error extracting music request from AI response: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to parse AI response", e);
            }
        });
    }

    private String extractContentFromAiResponse(JsonNode responseNode) {
        // Gemini returns candidates[].content.parts[].text
        if (responseNode.has("candidates") && responseNode.get("candidates").isArray()
                && !responseNode.get("candidates").isEmpty()) {
            JsonNode candidate = responseNode.get("candidates").get(0);
            if (candidate.has("content") && candidate.get("content").has("parts")
                    && candidate.get("content").get("parts").isArray()
                    && !candidate.get("content").get("parts").isEmpty()) {
                JsonNode part = candidate.get("content").get("parts").get(0);
                if (part.has("text")) {
                    return part.get("text").asText();
                }
            }
        }

        // Fallback: return the raw response
        return responseNode.toString();
    }

    private Map<String, Object> buildAiRequest(String prompt, Integer maxOutputTokensOverride) {
        Map<String, Object> request = new HashMap<>();

        int maxTokens = maxOutputTokensOverride != null ? maxOutputTokensOverride : 500;

        java.util.List<Map<String, Object>> contents = new java.util.ArrayList<>();

        Map<String, Object> userContent = new java.util.HashMap<>();
        userContent.put("role", "user");

        java.util.List<Map<String, String>> parts = new java.util.ArrayList<>();
        Map<String, String> part = new java.util.HashMap<>();
        part.put("text", prompt);
        parts.add(part);

        userContent.put("parts", parts);
        contents.add(userContent);

        request.put("contents", contents);

        Map<String, Object> generationConfig = new java.util.HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", maxTokens);
        request.put("generationConfig", generationConfig);

        return request;
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable t = throwable;
        for (int depth = 0; depth < 6 && t != null; depth++) {
            if (t instanceof WebClientResponseException e) {
                int status = e.getStatusCode().value();
                return status >= 500 || status == 429;
            }
            if (t instanceof TimeoutException) {
                return true;
            }
            if (t instanceof IOException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private Throwable mapError(Throwable throwable) {
        if (isRetryExhausted(throwable)) {
            log.error("AI API retries exhausted: {}", throwable.toString());
            return new RuntimeException(
                    "AI did not respond after several attempts (quota, rate limit, or network). Try again shortly.",
                    unwrapCause(throwable));
        }
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) throwable;
            log.error("AI API error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return new RuntimeException("AI API error: " + e.getStatusCode(), e);
        }
        return throwable;
    }

    private static boolean isRetryExhausted(Throwable throwable) {
        Throwable t = throwable;
        for (int depth = 0; depth < 8 && t != null; depth++) {
            if (t.getClass().getName().endsWith("RetryExhaustedException")) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && msg.contains("Retries exhausted")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static Throwable unwrapCause(Throwable throwable) {
        Throwable t = throwable;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }
}
