package com.discord.bot.music.listener;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import java.awt.Color;
import java.time.Instant;

@Component
public class MemberJoinListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MemberJoinListener.class);

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        Member botMember = guild.getSelfMember();

        // 1. Send Welcome Message
        sendWelcomeMessage(guild, member);

        // 2. Auto Assign Role
        // Check if the bot has permission to manage roles
        if (!botMember.hasPermission(Permission.MANAGE_ROLES)) {
            log.warn("Cannot auto-assign member role in guild {}: Missing MANAGE_ROLES permission.", guild.getName());
            return;
        }

        // Find the "member" role (case-insensitive)
        List<Role> roles = guild.getRolesByName("member", true);
        if (roles.isEmpty()) {
            log.warn("Cannot auto-assign member role in guild {}: Role 'member' not found.", guild.getName());
            return;
        }

        Role memberRole = roles.get(0);

        // Check if bot can interact with the role
        if (!botMember.canInteract(memberRole)) {
            log.warn("Cannot auto-assign member role in guild {}: Bot's highest role is lower than or equal to 'member' role.", guild.getName());
            return;
        }

        guild.addRoleToMember(member, memberRole).queue(
                success -> log.info("Auto-assigned 'member' role to {} in guild {}.", member.getUser().getAsTag(), guild.getName()),
                error -> log.error("Failed to auto-assign 'member' role to {} in guild {}: {}", member.getUser().getAsTag(), guild.getName(), error.getMessage())
        );
    }

    private void sendWelcomeMessage(Guild guild, Member member) {
        // Try to find a channel named 'welcome' or 'chào-mừng'
        TextChannel welcomeChannel = guild.getTextChannelsByName("welcome", true).stream().findFirst().orElse(null);
        if (welcomeChannel == null) {
            welcomeChannel = guild.getTextChannelsByName("chào-mừng", true).stream().findFirst().orElse(null);
        }
        if (welcomeChannel == null) {
            // Fallback to system channel
            welcomeChannel = guild.getSystemChannel();
        }

        if (welcomeChannel != null && guild.getSelfMember().hasPermission(welcomeChannel, Permission.MESSAGE_SEND)) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("🎉 CHÀO MỪNG THÀNH VIÊN MỚI! 🎉");
            embed.setDescription("Xin chào " + member.getAsMention() + " đã đến với **" + guild.getName() + "**!\n\n"
                    + "✨ Chúc bạn có những phút giây vui vẻ và thư giãn tại đây.\n"
                    + "👥 Hiện tại server chúng ta đang có **" + guild.getMemberCount() + "** thành viên!");
            
            // Set an aesthetic color
            embed.setColor(new Color(88, 101, 242)); // Discord Blurple
            
            // Set user's avatar as thumbnail
            embed.setThumbnail(member.getUser().getEffectiveAvatarUrl());
            
            // Set a nice banner GIF (Placeholder aesthetic anime scenery)
            embed.setImage("https://i.pinimg.com/originals/df/cb/3a/dfcb3a1c86ca22de26ad7b824a737f09.gif");
            
            embed.setFooter("Đã tham gia", guild.getIconUrl());
            embed.setTimestamp(Instant.now());

            welcomeChannel.sendMessageEmbeds(embed.build()).queue();
        }
    }
}
