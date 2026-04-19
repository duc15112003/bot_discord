package com.discord.bot.music.listener;

import com.discord.bot.music.service.MusicService;
import com.discord.bot.music.service.PlaySearchSessionStore;
import com.discord.bot.music.service.PlaySearchTrack;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Handles the string select menu shown after /playsearch.
 */
@Component
public class PlaySearchInteractionListener extends ListenerAdapter {

    public static final String CUSTOM_ID_PREFIX = "playsearch:";

    private static final Logger log = LoggerFactory.getLogger(PlaySearchInteractionListener.class);

    private final MusicService musicService;
    private final PlaySearchSessionStore sessionStore;

    public PlaySearchInteractionListener(MusicService musicService, PlaySearchSessionStore sessionStore) {
        this.musicService = musicService;
        this.sessionStore = sessionStore;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith(CUSTOM_ID_PREFIX)) {
            return;
        }

        String sessionId = componentId.substring(CUSTOM_ID_PREFIX.length());
        int index;
        try {
            index = Integer.parseInt(event.getValues().get(0));
        } catch (NumberFormatException e) {
            event.reply("❌ Lựa chọn không hợp lệ.").setEphemeral(true).queue();
            return;
        }

        if (!sessionStore.canUse(sessionId, event.getUser().getId())) {
            event.reply("❌ Chỉ người đã gọi `/playsearch` mới chọn được từ menu này.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferEdit().queue(hook -> {
            try {
                PlaySearchTrack track = sessionStore.takeTrack(sessionId, index);
                musicService.playAsyncWithYoutubeSearchFallback(
                                event.getGuild(), event.getMember(), track.playbackQuery(), track.searchFallback())
                        .subscribe(
                                message -> hook.editOriginal(message)
                                        .setComponents(Collections.emptyList())
                                        .queue(),
                                error -> {
                                    log.error("playsearch select play failed: {}", error.getMessage(), error);
                                    hook.editOriginal("❌ Không phát được: " + error.getMessage())
                                            .setComponents(Collections.emptyList())
                                            .queue();
                                }
                        );
            } catch (IllegalStateException e) {
                hook.editOriginal("❌ " + e.getMessage())
                        .setComponents(Collections.emptyList())
                        .queue();
            }
        }, failure -> log.error("deferEdit failed for playsearch: {}", failure.getMessage(), failure));
    }
}
