package com.discord.bot.music.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BanCommand implements SlashCommand {

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("ban", "Ban a user from the server")
                .addOption(OptionType.USER, "user", "The user to ban", true)
                .addOption(OptionType.STRING, "reason", "Reason for the ban", false)
                .addOption(OptionType.INTEGER, "del_days", "Delete messages from the last X days (0-7)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("❌ You do not have permission to ban members!").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        if (targetUser == null) {
            event.reply("❌ Please provide a valid user.").setEphemeral(true).queue();
            return;
        }

        Member botMember = event.getGuild().getSelfMember();
        if (!botMember.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("❌ I do not have permission to ban members!").setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user", OptionMapping::getAsMember);
        if (targetMember != null && !botMember.canInteract(targetMember)) {
            event.reply("❌ I cannot ban this user because their role is higher or equal to mine.").setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason", "No reason provided", OptionMapping::getAsString);
        int delDays = event.getOption("del_days", 0, OptionMapping::getAsInt);

        if (delDays < 0 || delDays > 7) {
            event.reply("❌ Invalid delete days limit. Please select between 0 and 7.").setEphemeral(true).queue();
            return;
        }

        event.getGuild().ban(targetUser, delDays, TimeUnit.DAYS).reason(reason).queue(
                success -> event.reply("✅ Successfully banned **" + targetUser.getAsTag() + "**! Reason: " + reason).queue(),
                error -> event.reply("❌ Failed to ban the user: " + error.getMessage()).queue()
        );
    }
}
