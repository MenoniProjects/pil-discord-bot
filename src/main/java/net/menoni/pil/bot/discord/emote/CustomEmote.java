package net.menoni.pil.bot.discord.emote;

import lombok.Getter;
import net.menoni.jda.commons.discord.emote.Emotable;

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
    TEAM_PLACEHOLDER("1317331273760378930"),
	// echelons
	ECHELON_0("1474891703587442708"),
	ECHELON_1("1474891704602591353"),
	ECHELON_2("1474891705764286474"),
	ECHELON_3("1474891706796085279"),
	ECHELON_4("1474891708108898395"),
	ECHELON_5("1474891709598138540"),
	ECHELON_6("1474891710671880273"),
	ECHELON_7("1474891711674060891"),
	ECHELON_8("1474891713108775123"),
	ECHELON_9("1474891714719383642"),
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
