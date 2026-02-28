package com.discord.bot.music.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry for categorizing and organizing slash commands.
 * Automatically discovers SlashCommand implementations and organizes them by category.
 */
@Component
public class CommandRegistry {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, List<SlashCommandInfo>> commandsByCategory = new LinkedHashMap<>();

    public CommandRegistry(List<SlashCommand> slashCommands) {
        // Initialize categories
        commandsByCategory.put("🎶 Music Controls", new ArrayList<>());
        commandsByCategory.put("📋 Playlist Management", new ArrayList<>());
        commandsByCategory.put("🎙️ Temporary Voice Channels", new ArrayList<>());
        commandsByCategory.put("🔧 Auto Voice (User Commands)", new ArrayList<>());
        commandsByCategory.put("⚙️ Auto Voice (Admin Commands)", new ArrayList<>());
        commandsByCategory.put("ℹ️ Other", new ArrayList<>());

        // Register commands
        for (SlashCommand cmd : slashCommands) {
            String category = categorizeCommand(cmd.getName());
            String description = getCommandDescription(cmd.getName());
            commandsByCategory.get(category).add(new SlashCommandInfo(cmd.getName(), description));
            log.info("Registered command: {} in category: {}", cmd.getName(), category);
        }
    }

    /**
     * Get all commands organized by category.
     */
    public Map<String, List<SlashCommandInfo>> getAllCommands() {
        return new LinkedHashMap<>(commandsByCategory);
    }

    /**
     * Categorize command by name.
     */
    private String categorizeCommand(String commandName) {
        return switch (commandName) {
            case "play", "stop", "next", "pre", "pause", "resume" -> "🎶 Music Controls";
            case "playlist-add", "playlist-list", "playlist-remove" -> "📋 Playlist Management";
            case "set-create-channel" -> "🎙️ Temporary Voice Channels";
            case "autovoice" -> {
                // This will be handled separately with subcommands
                yield "🔧 Auto Voice (User Commands)";
            }
            default -> "ℹ️ Other";
        };
    }

    /**
     * Get description for a command.
     */
    private String getCommandDescription(String commandName) {
        return switch (commandName) {
            // Music Controls
            case "play" -> "Play a song (YouTube search or URL)";
            case "stop" -> "Stop playback, clear queue, leave channel";
            case "next" -> "Skip to the next track";
            case "pre" -> "Play the previous track from history";
            case "pause" -> "Pause the current track";
            case "resume" -> "Resume playback";

            // Playlist Management
            case "playlist-add" -> "Save the current track to a playlist";
            case "playlist-list" -> "View all your playlists and tracks";
            case "playlist-remove" -> "Remove a track from a playlist";

            // Temporary Voice Channels
            case "set-create-channel" -> "Set the voice channel for creating temporary channels (Admin)";

            // Auto Voice (handled separately)
            case "autovoice" -> "Manage automatic voice channel creation";

            // Bot Help
            case "bot-help" -> "Show all available bot commands";

            default -> "No description available";
        };
    }

    /**
     * Get all auto voice subcommands.
     */
    public List<AutoVoiceSubcommandInfo> getAutoVoiceSubcommands() {
        return List.of(
                new AutoVoiceSubcommandInfo("lock", "Lock your temp voice channel", "🔧 User"),
                new AutoVoiceSubcommandInfo("unlock", "Unlock your temp voice channel", "🔧 User"),
                new AutoVoiceSubcommandInfo("rename", "Rename your temp voice channel", "🔧 User"),
                new AutoVoiceSubcommandInfo("limit", "Set user limit for your temp voice channel", "🔧 User"),
                new AutoVoiceSubcommandInfo("info", "Show info about your temp voice channel", "🔧 User"),
                new AutoVoiceSubcommandInfo("setup", "Configure a trigger channel for auto-voice", "⚙️ Admin"),
                new AutoVoiceSubcommandInfo("remove", "Remove a trigger channel configuration", "⚙️ Admin"),
                new AutoVoiceSubcommandInfo("list", "List all auto-voice trigger channels", "⚙️ Admin")
        );
    }

    /**
     * Info about a slash command.
     */
    public static class SlashCommandInfo {
        public final String name;
        public final String description;

        public SlashCommandInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    /**
     * Info about an auto voice subcommand.
     */
    public static class AutoVoiceSubcommandInfo {
        public final String name;
        public final String description;
        public final String type;

        public AutoVoiceSubcommandInfo(String name, String description, String type) {
            this.name = name;
            this.description = description;
            this.type = type;
        }
    }
}

