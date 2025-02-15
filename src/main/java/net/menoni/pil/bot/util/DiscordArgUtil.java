package net.menoni.pil.bot.util;

import java.util.regex.Pattern;

public class DiscordArgUtil {

	private static final char ZERO = '0';
	private static final char NINE = '9';
	private static final Pattern ID_PATTERN = Pattern.compile("([0-9]+)");
	private static final Pattern ROLE_PATTERN = Pattern.compile("<@&([0-9]+)>");
	private static final Pattern EMOTE_PATTERN = Pattern.compile("<:([a-zA-Z0-9_-]+):([0-9]+)>");

	public static boolean isNudeId(String arg) {
		return ID_PATTERN.matcher(arg).matches();
	}

	public static String getNudeId(String arg) {
		if (!isNudeId(arg)) {
			return null;
		}
		return getNumericCharacters(arg);
	}

	public static boolean isRole(String arg) {
		return ID_PATTERN.matcher(arg).matches() || ROLE_PATTERN.matcher(arg).matches();
	}

	public static String getRoleId(String arg) {
		if (!isRole(arg)) {
			return null;
		}
		return getNumericCharacters(arg);
	}

	private static String getNumericCharacters(String arg) {
		StringBuilder sb = new StringBuilder();
		for (char c : arg.toCharArray()) {
			if (c >= ZERO && c <= NINE) {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public static boolean isEmote(String arg) {
		return EMOTE_PATTERN.matcher(arg).matches();
	}

	public static ParsedEmote parseEmoteArg(String arg) {
		if (!isEmote(arg)) {
			return null;
		}
		arg = arg.substring(2);
		arg = arg.substring(0, arg.length() - 1);
		return new ParsedEmote(
				arg.split(":")[0],
				arg.split(":")[1]
		);
	}

	public static ParsedMatchScore parseMatchScoreArg(String arg) {
		if (arg.trim().equalsIgnoreCase("ff")) {
			return new ParsedMatchScore(0, 0);
		}
		String[] scoreParts = arg.split("-");
		if (scoreParts.length == 2) {
			try {
				int score1 = Integer.parseInt(scoreParts[0]);
				int score2 = Integer.parseInt(scoreParts[1]);

				if (score1 != score2 &&
						isWithinScoreBounds(score1, 0, 2) &&
						isWithinScoreBounds(score2, 0, 2)) {
					int high = Math.max(score1, score2);
					int low = Math.min(score1, score2);

					return new ParsedMatchScore(high, low);
				}
			} catch (NumberFormatException e) { }
		}
		return null;
	}

	private static boolean isWithinScoreBounds(int score, int low, int high) {
		return score >= low && score <= high;
	}

	public record ParsedEmote(
			String emoteName,
			String emoteId
	) { }

	public record ParsedMatchScore(
			int winTeamScore,
			int loseTeamScore
	) {
		public boolean isForfeit() {
			return winTeamScore == 0 && loseTeamScore == 0;
		}

		public String print() {
			if (isForfeit()) {
				return "ff by opponent";
			}
			return winTeamScore + "-" + loseTeamScore;
		}
	}

}
