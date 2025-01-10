package net.menoni.pil.bot.util;

import lombok.Getter;

import java.util.regex.Pattern;

@Getter
public enum RoundType {

	TEST(-99, "Test", (division, roundNum, teamName1, teamName2) -> "test-%s-%s".formatted(teamName1, teamName2), false),
	SPECIAL(-1, "Special", (division, roundNum, teamName1, teamName2) -> "special-%s-%s".formatted(teamName1, teamName2), false),
	LEAGUE(0, "League", (division, roundNum, teamName1, teamName2) -> "d%dr%d-league-%s-%s".formatted(division, roundNum, teamName1, teamName2)),
	PLAYOFFS(10, "Play-offs", (division, roundNum, teamName1, teamName2) -> "d%dr%d-playoff-%s-%s".formatted(division, roundNum, teamName1, teamName2)),
	GRAND_FINAL(20, "Grand Final", (division, roundNum, teamName1, teamName2) -> "d%d-grandfinal-%s-%s".formatted(division, teamName1, teamName2)),
	;

	private final int start;
	private final String displayName;
	private final MatchChannelNameFormatter formatter;
	private final boolean requireDivision;

	RoundType(int start, String displayName, MatchChannelNameFormatter formatter) {
		this(start, displayName, formatter, true);
	}

	RoundType(int start, String displayName, MatchChannelNameFormatter formatter, boolean requireDivision) {
		this.start = start;
		this.displayName = displayName;
		this.formatter = formatter;
		this.requireDivision = requireDivision;
	}

	public static RoundType forRoundNumber(int roundNumber) {
		// reverse order loop
		for (int i = RoundType.values().length - 1; i >= 0; i--) {
			RoundType type = RoundType.values()[i];
			if (roundNumber > type.getStart()) {
				return type;
			}
		}
		return null;
	}

	public int adjustRoundNumber(int roundNumber) {
		if (roundNumber > this.getStart()) {
			return roundNumber - this.getStart();
		}
		return roundNumber;
	}

	public String matchChannelName(int division, int roundNum, String teamName1, String teamName2) {
		teamName1 = teamName1.toLowerCase().replaceAll(Pattern.quote(" "), "-");
		teamName2 = teamName2.toLowerCase().replaceAll(Pattern.quote(" "), "-");
		roundNum = adjustRoundNumber(roundNum);

		return this.getFormatter().format(division, roundNum, teamName1, teamName2);
	}

	@FunctionalInterface
	private interface MatchChannelNameFormatter {
		String format(int division, int roundNum, String teamName1, String teamName2);
	}

}
