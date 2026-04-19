package com.discord.bot.music.command;

import com.discord.bot.music.service.MusicService;
import com.discord.bot.music.service.MusicRequestParser;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * /play <query> — Search and play a song or URL.
 * Uses MusicRequestParser to intelligently parse user input.
 */
@Component
public class PlayCommand implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(PlayCommand.class);

    private final MusicService musicService;
    private final MusicRequestParser musicRequestParser;

    public PlayCommand(MusicService musicService, MusicRequestParser musicRequestParser) {
        this.musicService = musicService;
        this.musicRequestParser = musicRequestParser;
    }

    @Override
    public String getName() {
        return "play";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("play", "Play a song from YouTube or a URL")
                .addOption(OptionType.STRING, "query", "Song name or URL", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String query = event.getOption("query").getAsString();
        event.deferReply().queue(
                hook -> {
                    // Parse the request asynchronously
                    musicRequestParser.parseRequestAsync(query)
                            .doOnNext(parsedRequest -> {
                                log.debug("Parsed music request: {}", parsedRequest);
                                // Use the parsed keyword for music search
                                extractAndPlay(parsedRequest, event);
                            })
                            .subscribe(
                                    // Success case handled in doOnNext
                                    unused -> {
                                    },
                                    // Error case
                                    error -> {
                                        log.error("Error parsing music request: {}", error.getMessage(), error);
                                        hook.sendMessage("❌ Error parsing your request: " + error.getMessage()).queue();
                                    }
                            );
                },
                error -> log.error("Failed to acknowledge /play interaction {}: {}",
                        event.getId(), error.getMessage(), error));
    }

    private void extractAndPlay(String parsedJson, SlashCommandInteractionEvent event) {
        try {
            // Parse the JSON to extract the keyword
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(parsedJson);

            String type = jsonNode.get("type").asText();

            // If it's an invalid query, inform the user
            if ("INVALID".equals(type)) {
                event.getHook().sendMessage("❌ That doesn't seem to be a music-related request.").queue();
                return;
            }

            // Extract the keyword (search term or URL)
            String keyword = jsonNode.get("keyword").asText();

            if (keyword == null || keyword.isEmpty()) {
                event.getHook().sendMessage("❌ Could not extract search keyword from your request.").queue();
                return;
            }

            // Play the music using the extracted keyword
            musicService.playAsync(event.getGuild(), event.getMember(), keyword)
                    .subscribe(
                            message -> event.getHook().sendMessage(message).queue(),
                            error -> {
                                log.error("Unhandled async error for /play in guild {}: {}",
                                        event.getGuild() != null ? event.getGuild().getId() : "dm",
                                        error.getMessage(), error);
                                event.getHook().sendMessage("❌ An unexpected error occurred while loading audio.").queue();
                            }
                    );
        } catch (Exception e) {
            log.error("Error extracting keyword from parsed request: {}", e.getMessage(), e);
            event.getHook().sendMessage("❌ Error processing your request.").queue();
        }
    }
}
