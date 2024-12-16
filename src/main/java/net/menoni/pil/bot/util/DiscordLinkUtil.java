package net.menoni.pil.bot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordLinkUtil {
	private static final String CHANNEL_LINK_PREFIX = "https://discord.com/channels/";
	private static final Pattern GUILD_CHANNEL_FORMAT = Pattern.compile("([0-9]+)/([0-9]+)");
	private static final Pattern GUILD_CHANNEL_MESSAGE_FORMAT = Pattern.compile("([0-9]+)/([0-9]+)/([0-9]+)");

	public static boolean isDiscordMessageLink(String link) {
		if (!link.startsWith(CHANNEL_LINK_PREFIX)) {
			return false;
		}
		String idsPart = link.substring(CHANNEL_LINK_PREFIX.length());
		Matcher matcher = GUILD_CHANNEL_MESSAGE_FORMAT.matcher(idsPart);
		if (!matcher.matches()) {
			return false;
		}
		return true;
	}
	public static String getDiscordMessageLinkChannelId(String link) {
		if (!isDiscordMessageLink(link)) {
			return null;
		}
		String idsPart = link.substring(CHANNEL_LINK_PREFIX.length());
		Matcher matcher = GUILD_CHANNEL_MESSAGE_FORMAT.matcher(idsPart);
		if (!matcher.matches()) {
			return null;
		}
		return matcher.group(2);
	}

	public static String getDiscordMessageLinkMessageId(String link) {
		if (!isDiscordMessageLink(link)) {
			return null;
		}
		String idsPart = link.substring(CHANNEL_LINK_PREFIX.length());
		Matcher matcher = GUILD_CHANNEL_MESSAGE_FORMAT.matcher(idsPart);
		if (!matcher.matches()) {
			return null;
		}
		return matcher.group(3);
	}

}
