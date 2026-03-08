package com.discord.bot.music.command;

import com.discord.bot.music.audio.BotInstance;
import com.discord.bot.music.audio.BotInstancePool;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;

/**
 * /invite — Show invite links for all bot instances.
 */
@Component
public class InviteCommand implements SlashCommand {

    private final BotInstancePool botInstancePool;

    public InviteCommand(BotInstancePool botInstancePool) {
        this.botInstancePool = botInstancePool;
    }

    @Override
    public String getName() {
        return "invite";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("invite", "Get invite links for all music bot instances");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<BotInstance> bots = botInstancePool.getAllInstances();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎵 Invite Music Bots")
                .setDescription("Invite thêm bot để phát nhạc đồng thời ở nhiều voice channel!\n"
                        + "Mỗi bot = 1 voice channel trong cùng server.")
                .setColor(new Color(88, 101, 242)); // Discord blurple

        for (BotInstance bot : bots) {
            String status = bot.isPrimary() ? "⭐ Primary" : "🎵 Secondary";
            String name = bot.getBotName() + " " + status;
            String inviteLink = "[Invite " + bot.getBotName() + "](" + bot.getInviteUrl() + ")";
            embed.addField(name, inviteLink, false);
        }

        embed.setFooter("Hiện có " + bots.size() + " bot instance(s) | Thêm token vào config để có thêm bot");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
