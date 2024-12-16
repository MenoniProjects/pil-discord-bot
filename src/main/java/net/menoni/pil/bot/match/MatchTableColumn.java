package net.menoni.pil.bot.match;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.menoni.pil.bot.util.TrackmaniaUtil;

import java.util.ArrayList;
import java.util.function.BiFunction;

@Getter
@AllArgsConstructor
public enum MatchTableColumn {

	RANK("Rank", (m, mp) -> new ArrayList<>(m.getPlayers()).indexOf(mp) + 1),
	TEAM("Team", (m, mp) -> mp.getTeamName()),
	PLAYER_NAME("Name", (m, mp) -> mp.getPlayerName()),
	PLAYER_ID("ID", (m, mp) -> mp.getPlayerId()),
	POINTS("Points", (m, mp) -> mp.getPoints()),
	AVERAGE_TIME("Average Time", (m, mp) -> TrackmaniaUtil.formatRecordTime(m.getPlayerAverageTime(mp.getPlayerId()))),
	BEST_TIME("Best Time", (m, mp) -> TrackmaniaUtil.formatRecordTime(m.getPlayerBestTime(mp.getPlayerId()))),
	MISSED_ROUNDS("Missed Rounds", (m, mp) -> {
		int missedRounds = m.getRounds().size() - m.getPlayerRounds(mp.getPlayerId()).size();
		return missedRounds > 0 ? Integer.toString(missedRounds) : "-";
	})

	;

	private final String header;
	private final BiFunction<Match, MatchPlayer, Object> extractor;

}
