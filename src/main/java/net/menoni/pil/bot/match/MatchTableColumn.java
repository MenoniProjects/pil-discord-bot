package net.menoni.pil.bot.match;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.menoni.pil.bot.util.TrackmaniaUtil;

import java.util.ArrayList;
import java.util.function.BiFunction;

@Getter
@AllArgsConstructor
public enum MatchTableColumn {

	RANK("Rank", (m, team) -> new ArrayList<>(m.getTeamScoresRanked().keySet()).indexOf(team) + 1),
	TEAM("Team", (m, team) -> team),
	POINTS("Points", (m, team) -> m.getTeamScoresRanked().get(team)),
	;

	private final String header;
	private final BiFunction<Match, String, Object> extractor;

}
