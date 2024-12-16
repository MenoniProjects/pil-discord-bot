package net.menoni.pil.bot.discord.emote;

public interface Emotable<E extends Enum<E>> {

    String print();

    static String printById(String name, String id, boolean animated) {
        if (animated) {
            return String.format("<a:%s:%s>", name, id);
        }
        return String.format("<:%s:%s>", name, id);
    }

}
