package com.discord.bot.music.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Interface for all slash commands.
 * Each command provides its registration data and execution logic.
 */
public interface SlashCommand {

    /**
     * The command name as registered with Discord (e.g. "play").
     */
    String getName();

    /**
     * The JDA slash command data for registration.
     */
    SlashCommandData getCommandData();

    /**
     * Execute the command.
     */
    void execute(SlashCommandInteractionEvent event);
}
