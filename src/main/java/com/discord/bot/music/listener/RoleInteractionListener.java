package com.discord.bot.music.listener;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoleInteractionListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RoleInteractionListener.class);

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("role-assign:")) {
            String targetUserId = componentId.substring("role-assign:".length());
            String selectedRoleStr = event.getValues().get(0);
            Guild guild = event.getGuild();
            Member executor = event.getMember();

            if (guild == null || executor == null) {
                event.reply("❌ Cannot perform this action outside of a server.").setEphemeral(true).queue();
                return;
            }

            // Defer reply to allow time for processing
            event.deferReply(true).queue();

            guild.retrieveMemberById(targetUserId).queue(
                    targetMember -> processRoleAssignment(event, guild, executor, targetMember, selectedRoleStr),
                    error -> event.getHook().sendMessage("❌ User is no longer in this server.").queue()
            );
        }
    }

    private void processRoleAssignment(StringSelectInteractionEvent event, Guild guild, Member executor, Member targetMember, String roleStr) {
        // Find existing role
        List<Role> roles = guild.getRolesByName(roleStr, true);
        if (roles.isEmpty()) {
            event.getHook().sendMessage("❌ The role `" + roleStr + "` does not exist in this server! Please create it first.").queue();
            return;
        }

        Role roleToAssign = roles.get(0);

        // Security Check: To assign 'admin', user MUST be the Server Owner
        if (roleStr.equalsIgnoreCase("admin")) {
            if (executor.getIdLong() != guild.getOwnerIdLong()) {
                event.getHook().sendMessage("❌ Only the **Server Owner** can assign the `admin` role.").queue();
                return;
            }
        } else {
            // For other roles, must have MANAGE_ROLES perm
            if (!executor.hasPermission(Permission.MANAGE_ROLES)) {
                event.getHook().sendMessage("❌ You do not have permission to assign roles.").queue();
                return;
            }
        }
        
        // Check if bot can interact with target and target role
        Member botMember = guild.getSelfMember();
        if (!botMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.getHook().sendMessage("❌ I lack the `Manage Roles` permission.").queue();
            return;
        }
        if (!botMember.canInteract(roleToAssign)) {
            event.getHook().sendMessage("❌ I cannot assign this role because my highest role is lower than or equal to `" + roleToAssign.getName() + "`.").queue();
            return;
        }

        if (targetMember.getRoles().contains(roleToAssign)) {
            event.getHook().sendMessage("⚠️ The user already has the `" + roleToAssign.getName() + "` role.").queue();
            return;
        }

        guild.addRoleToMember(targetMember, roleToAssign).queue(
                success -> event.getHook().sendMessage("✅ Successfully assigned the `" + roleToAssign.getName() + "` role to **" + targetMember.getUser().getAsTag() + "**!").queue(),
                error -> {
                    log.error("Failed to assign role {} to user {}: {}", roleToAssign.getName(), targetMember.getId(), error.getMessage());
                    event.getHook().sendMessage("❌ Failed to assign the role: " + error.getMessage()).queue();
                }
        );

    }
}
