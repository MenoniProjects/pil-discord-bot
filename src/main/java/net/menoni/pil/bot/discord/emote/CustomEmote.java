package net.menoni.pil.bot.discord.emote;

import lombok.Getter;

/**
 * To get an emote ID send:
 * \:emote:
 * in a channel (like a normal emote with a backslash before)
 * it will send something like:
 * <:ozzy:1139727471244226580>
 * instead
 */
@Getter
public enum CustomEmote implements Emotable<CustomEmote> {
    TEAM_PLACEHOLDER("1317331273760378930")
    ;

    private final String id;
    private final boolean animated;

    CustomEmote(String id, boolean animated) {
        this.id = id;
        this.animated = animated;
    }

    CustomEmote(String id) {
        this(id, false);
    }

    @Override
    public String print() {
        return Emotable.printById(this.name().toLowerCase(), this.id, this.animated);
    }

}
