package com.discord.bot.music.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.awt.*;

/**
 * /bot-help — Display all available commands.
 * Automatically generates help from CommandRegistry.
 */
@Component
public class BotHelpCommand implements SlashCommand {

    private final CommandRegistry commandRegistry;

    public BotHelpCommand(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    @Override
    public String getName() {
        return "bot-help";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("bot-help", "Show all available bot commands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎵 Music Bot — Help")
                .setColor(new Color(88, 101, 242)) // Discord Blurple
                .setDescription("Here are all available commands:");

        // Add commands organized by category
        var allCommands = commandRegistry.getAllCommands();
        for (var entry : allCommands.entrySet()) {
            String category = entry.getKey();
            var commands = entry.getValue();

            if (commands.isEmpty()) {
                continue; // Skip empty categories
            }

            // Build command list for this category
            StringBuilder commandList = new StringBuilder();
            for (var cmd : commands) {
                if (cmd.name.equals("autovoice")) {
                    // Special handling for autovoice with subcommands
                    commandList.append("`/autovoice <subcommand>` — Manage automatic voice channel creation\n");
                } else {
                    commandList.append("`/").append(cmd.name).append("` — ").append(cmd.description).append("\n");
                }
            }

            if (commandList.length() > 0) {
                embed.addField(category, commandList.toString(), false);
            }
        }

        // Add Auto Voice subcommands section
        var autoVoiceSubcommands = commandRegistry.getAutoVoiceSubcommands();
        StringBuilder userCommands = new StringBuilder();
        StringBuilder adminCommands = new StringBuilder();

        for (var subcmd : autoVoiceSubcommands) {
            String line = "`/autovoice " + subcmd.name + "` — " + subcmd.description + "\n";
            if (subcmd.type.contains("User")) {
                userCommands.append(line);
            } else {
                adminCommands.append(line);
            }
        }

        if (userCommands.length() > 0) {
            embed.addField("🔧 Auto Voice Subcommands (User)", userCommands.toString(), false);
        }
        if (adminCommands.length() > 0) {
            embed.addField("⚙️ Auto Voice Subcommands (Admin)", adminCommands.toString(), false);
        }

        embed.setFooter("Made with ❤️ using Spring Boot + JDA + Lavalink");

        event.replyEmbeds(embed.build()).queue();
    }
}


