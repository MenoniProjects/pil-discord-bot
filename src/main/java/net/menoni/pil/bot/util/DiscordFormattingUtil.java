package net.menoni.pil.bot.util;

import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.menoni.pil.bot.discord.emote.CustomEmote;
import net.menoni.pil.bot.discord.emote.Emotable;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;

import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public class DiscordFormattingUtil {

	private static final String UNDERSCORE = Pattern.quote("_");
	private static final String ASTERISK = Pattern.quote("*");

	private static final String ARG_REPLACE = Pattern.quote("{}");
	private static final Set<ArgFormatter<?>> FORMATTERS = Set.of(
			ArgFormatter.of(Member.class, IMentionable::getAsMention, Member::getEffectiveName),
			ArgFormatter.of(JdbcTeam.class, t -> DiscordFormattingUtil.roleAsString(t.getDiscordRoleId()), JdbcTeam::getName),
			ArgFormatter.of(DiscordArgUtil.ParsedMatchScore.class, DiscordArgUtil.ParsedMatchScore::print)
	);

	public static String escapeFormatting(String input) {
		input = input.replaceAll(UNDERSCORE, "\\\\_");
		input = input.replaceAll(ASTERISK, "\\\\*");
		return input;
	}

	public static String roleAsString(String roleId) {
		return "<@&%s>".formatted(roleId);
	}

	public static String memberAsString(String memberId) {
		return "<@%s>".formatted(memberId);
	}

	public static String teamEmoteAsString(JdbcTeam team) {
		if (team == null) {
			return CustomEmote.TEAM_PLACEHOLDER.print();
		}
		if (team.getEmoteId() != null && team.getEmoteName() != null) {
			return Emotable.printById(team.getEmoteName(), team.getEmoteId(), false);
		}
		return CustomEmote.TEAM_PLACEHOLDER.print();
	}

	public static String teamName(JdbcTeam team) {
		if (team == null) {
			return "_missing-team_";
		}
		return team.getName();
	}

	public static String formatMessageForContext(boolean richDiscordContext, String message, Object... args) {
		for (Object arg : args) {
			String t = formatArg(arg, richDiscordContext);
			message = message.replaceFirst(ARG_REPLACE, t);
		}
		return message;
	}

	public static String formatArg(Object argument, boolean richDiscordContext) {
		if (argument == null) {
			return "null";
		}
		for (ArgFormatter<?> formatter : FORMATTERS) {
			if (formatter.is(argument)) {
				return formatter.format(argument, richDiscordContext);
			}
		}
		return argument.toString();
	}

	private record ArgFormatter<T>(
			Class<T> type,
			Function<T, String> richFormatter,
			Function<T, String> logFormatter
	) {
		public boolean is(Object object) {
			return this.type.isInstance(object);
		}

		public String format(Object object, boolean richDiscordContext) {
			if (!is(object)) {
				throw new IllegalArgumentException(object + " is not an instance of " + this.type.getName());
			}
			T o = type.cast(object);
			if (richDiscordContext) {
				return richFormatter.apply(o);
			}
			return logFormatter.apply(o);
		}

		public static <T> ArgFormatter<T> of(Class<T> type, Function<T, String> richFormatter, Function<T, String> logFormatter) {
			return new ArgFormatter<>(type, richFormatter, logFormatter);
		}

		public static <T> ArgFormatter<T> of(Class<T> type, Function<T, String> universalFormatter) {
			return new ArgFormatter<>(type, universalFormatter, universalFormatter);
		}
	}

}
