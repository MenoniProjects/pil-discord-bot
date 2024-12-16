package net.menoni.pil.bot.match;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Match {

	private Long earliestFinish;
	private Long latestFinish;
	private Set<MatchPlayer> players;
	private Set<MatchRound> rounds;

	public int getPlayerCount() {
		return this.rounds.stream().mapToInt(r -> r.getFinishers().size()).max().orElse(0);
	}

	public List<MatchRoundPlayerFinish> getPlayerRounds(String playerId) {
		if (this.players.stream().noneMatch(p -> p.getPlayerId().equals(playerId))) {
			return Collections.emptyList();
		}
		List<MatchRoundPlayerFinish> playerRounds = new ArrayList<>();
		for (MatchRound round : this.rounds) {
			for (MatchRoundPlayerFinish finisher : round.getFinishers()) {
				if (finisher.getPlayerId().equalsIgnoreCase(playerId)) {
					playerRounds.add(finisher);
				}
			}
		}
		return playerRounds;
	}

	public long getPlayerBestTime(String playerId) {
		return getPlayerRounds(playerId).stream().mapToLong(MatchRoundPlayerFinish::getRecordTime).min().orElse(-1L);
	}

	public long getPlayerAverageTime(String playerId) {
		return Double.valueOf(getPlayerRounds(playerId)
				.stream()
				.mapToLong(MatchRoundPlayerFinish::getRecordTime)
				.average()
				.orElse(-1d))
				.longValue();
	}

}
