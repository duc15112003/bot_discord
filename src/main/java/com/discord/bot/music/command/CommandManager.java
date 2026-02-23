package com.discord.bot.music.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages slash command registration and event dispatching.
 * Collects all SlashCommand beans and registers them with Discord on ready.
 */
@Component
public class CommandManager extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final Map<String, SlashCommand> commands = new HashMap<>();

    public CommandManager(List<SlashCommand> slashCommands) {
        for (SlashCommand cmd : slashCommands) {
            commands.put(cmd.getName(), cmd);
            log.info("Registered slash command: /{}", cmd.getName());
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        List<SlashCommandData> commandDataList = commands.values().stream()
                .map(SlashCommand::getCommandData)
                .toList();

        event.getJDA().updateCommands()
                .addCommands(commandDataList)
                .queue(
                        success -> log.info("Successfully registered {} global slash commands", commandDataList.size()),
                        error -> log.error("Failed to register slash commands: {}", error.getMessage()));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        SlashCommand command = commands.get(commandName);

        if (command != null) {
            try {
                command.execute(event);
            } catch (Exception e) {
                log.error("Error executing command /{}: {}", commandName, e.getMessage(), e);
                if (event.isAcknowledged()) {
                    event.getHook().sendMessage("❌ An error occurred while executing this command.").queue();
                } else {
                    event.reply("❌ An error occurred while executing this command.")
                            .setEphemeral(true)
                            .queue();
                }
            }
        }
    }

    public Map<String, SlashCommand> getCommands() {
        return commands;
    }
}
