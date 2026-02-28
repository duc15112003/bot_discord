package com.discord.bot.music.command;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * /set-create-channel <channel> — Set the voice channel for creating temporary channels.
 * Only server admins can use this command.
 */
@Component
public class SetCreateChannelCommand implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(SetCreateChannelCommand.class);

    private final CreateChannelConfig createChannelConfig;

    public SetCreateChannelCommand(CreateChannelConfig createChannelConfig) {
        this.createChannelConfig = createChannelConfig;
    }

    @Override
    public String getName() {
        return "set-create-channel";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Set the voice channel for creating temporary channels (Admin only)")
                .addOption(OptionType.CHANNEL, "channel", "The voice channel where users can create their own channels", true)
                .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Check if user is admin
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ You must be an administrator to use this command!").setEphemeral(true).queue();
            return;
        }

        var channelOption = event.getOption("channel");
        if (channelOption == null) {
            event.reply("❌ Please select a voice channel!").setEphemeral(true).queue();
            return;
        }

        Channel channel = channelOption.getAsChannel();

        // Check if it's a voice channel
        if (channel.getType() != net.dv8tion.jda.api.entities.channel.ChannelType.VOICE) {
            event.reply("❌ Please select a voice channel!").setEphemeral(true).queue();
            return;
        }

        // Store the channel ID in the config
        if (event.getGuild() != null) {
            createChannelConfig.setCreateChannelId(event.getGuild().getIdLong(), channel.getIdLong());

            event.reply("✅ Create channel set to: <#" + channel.getId() + ">").setEphemeral(true).queue();
            log.info("Set create channel to {} in guild {}", channel.getId(), event.getGuild().getId());
        } else {
            event.reply("❌ Could not set create channel!").setEphemeral(true).queue();
        }
    }
}


