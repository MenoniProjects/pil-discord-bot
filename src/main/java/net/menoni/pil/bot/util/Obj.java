package net.menoni.pil.bot.util;

public class Obj {

	public static <T> T or(T value, T otherwise) {
		return value == null ? otherwise : value;
	}

}
