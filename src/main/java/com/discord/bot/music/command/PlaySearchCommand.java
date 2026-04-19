package com.discord.bot.music.command;

import com.discord.bot.music.listener.PlaySearchInteractionListener;
import com.discord.bot.music.service.MusicPlaySearchService;
import com.discord.bot.music.service.PlaySearchSessionStore;
import com.discord.bot.music.service.PlaySearchTrack;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * /playsearch &lt;context&gt; — AI suggests several tracks; user picks one from a menu, then playback starts.
 */
@Component
public class PlaySearchCommand implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(PlaySearchCommand.class);

    private final MusicPlaySearchService musicPlaySearchService;
    private final PlaySearchSessionStore sessionStore;

    public PlaySearchCommand(MusicPlaySearchService musicPlaySearchService, PlaySearchSessionStore sessionStore) {
        this.musicPlaySearchService = musicPlaySearchService;
        this.sessionStore = sessionStore;
    }

    @Override
    public String getName() {
        return "playsearch";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("playsearch", "AI gợi ý vài bài — chọn trong menu để phát")
                .addOption(OptionType.STRING, "context", "Mô tả / mood / tên bài / cảnh…", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String context = event.getOption("context").getAsString();
        event.deferReply().queue(
                hook -> musicPlaySearchService.resolveTrackChoices(context)
                        .subscribe(
                                tracks -> {
                                    String sessionId = sessionStore.create(event.getUser().getId(), tracks);

                                    StringSelectMenu.Builder menuBuilder = StringSelectMenu
                                            .create(PlaySearchInteractionListener.CUSTOM_ID_PREFIX + sessionId)
                                            .setPlaceholder("Chọn một bài để phát…");
                                    for (int i = 0; i < tracks.size(); i++) {
                                        PlaySearchTrack t = tracks.get(i);
                                        String desc = t.menuDescription();
                                        if (desc == null || desc.isBlank()) {
                                            menuBuilder.addOption(t.menuLabel(), String.valueOf(i));
                                        } else {
                                            menuBuilder.addOption(t.menuLabel(), String.valueOf(i), desc);
                                        }
                                    }

                                    hook.editOriginal("**Gợi ý nhạc** — chọn một dòng trong menu:\n"
                                                    + "*(chỉ người gọi lệnh mới chọn được)*")
                                            .setComponents(ActionRow.of(menuBuilder.build()))
                                            .queue();
                                },
                                error -> {
                                    log.error("playsearch failed: {}", error.getMessage(), error);
                                    hook.editOriginal(userMessage(error)).queue();
                                }
                        ),
                error -> log.error("Failed to acknowledge /playsearch interaction {}: {}",
                        event.getId(), error.getMessage(), error));
    }

    private static String userMessage(Throwable error) {
        if (error instanceof IllegalStateException) {
            return "❌ " + error.getMessage();
        }
        if (error instanceof IllegalArgumentException) {
            return "❌ " + error.getMessage();
        }
        return "❌ Không chạy được AI. Kiểm tra cấu hình AI và thử lại.";
    }
}
