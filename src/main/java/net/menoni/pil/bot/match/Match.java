package net.menoni.pil.bot.match;

import lombok.*;

import java.util.*;

@RequiredArgsConstructor
@Getter
@Setter
public class Match {

	private final Set<MatchPlayer> players;
	private final Set<MatchRound> rounds;
	private final List<String> problems;
	private Map<String, Integer> teamScoresRanked;

	public Map<String, Integer> getTeamScoresRanked() {
		if (teamScoresRanked != null) {
			return teamScoresRanked;
		}
		List<TeamScoreRecord> records = new ArrayList<>();

		for (MatchRound round : rounds) {
			for (MatchRoundPlayerFinish finisher : round.getFinishers()) {
				String teamName = players.stream().filter(p -> Objects.equals(p.getPlayerId(), finisher.getPlayerId())).findAny().map(MatchPlayer::getTeamName).orElse(null);
				if (teamName == null) {
					continue;
				}

				TeamScoreRecord record = records.stream().filter(r -> Objects.equals(r.getTeamName(), teamName)).findAny().orElse(null);
				if (record == null) {
					record = new TeamScoreRecord(teamName);
					records.add(record);
				}
				record.setScore(record.getScore() + finisher.getScore());
			}
		}

		records.sort(Comparator.comparing(TeamScoreRecord::getScore).reversed().thenComparing(TeamScoreRecord::getTeamName));
		Map<String, Integer> scores = new LinkedHashMap<>();
		for (TeamScoreRecord record : records) {
			scores.put(record.getTeamName(), record.getScore());
		}
		teamScoresRanked = scores;
		return scores;
	}

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

	@RequiredArgsConstructor
	@Getter
	@Setter
	private static class TeamScoreRecord {
		private final String teamName;
		private int score;
	}

}
