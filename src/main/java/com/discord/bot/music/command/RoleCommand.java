package com.discord.bot.music.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import org.springframework.stereotype.Component;

@Component
public class RoleCommand implements SlashCommand {

    @Override
    public String getName() {
        return "role";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("role", "Assign a role to a user")
                .addOption(OptionType.USER, "user", "The user to assign the role to", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("❌ You do not have permission to manage roles.").setEphemeral(true).queue();
            return;
        }

        Member botMember = event.getGuild().getSelfMember();
        if (!botMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("❌ I do not have permission to manage roles!").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user", OptionMapping::getAsUser);
        if (targetUser == null) {
            event.reply("❌ Please provide a valid user.").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu menu = StringSelectMenu.create("role-assign:" + targetUser.getId())
                .setPlaceholder("Select a role to assign")
                .addOption("Admin", "admin", "Assign Admin Role")
                .addOption("Member", "member", "Assign Member Role")
                .build();

        event.reply("Please select a role to assign to **" + targetUser.getAsTag() + "**: ")
                .addComponents(ActionRow.of(menu))
                .setEphemeral(true)
                .queue();
    }
}
