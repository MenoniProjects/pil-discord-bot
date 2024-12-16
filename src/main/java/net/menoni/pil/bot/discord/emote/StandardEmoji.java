package net.menoni.pil.bot.discord.emote;

public enum StandardEmoji implements Emotable<StandardEmoji>{

    WHITE_CHECK_MARK("\u2705"),
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
