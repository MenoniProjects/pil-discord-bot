package net.menoni.pil.bot.discord.emote;

import net.menoni.jda.commons.discord.emote.Emotable;

public enum StandardEmoji implements Emotable<StandardEmoji> {

    WHITE_CHECK_MARK("\u2705"),
    WARNING("\u26a0\ufe0f️"),
    ;

    private final String value;

    StandardEmoji(String value) {
        this.value = value;
    }

    @Override
    public String print() {
        return this.value;
    }
}
