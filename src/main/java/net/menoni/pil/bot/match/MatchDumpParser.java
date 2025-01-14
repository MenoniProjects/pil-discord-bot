package net.menoni.pil.bot.match;

import lombok.extern.slf4j.Slf4j;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.service.TeamService;

import java.io.IOException;
import java.util.*;

@Slf4j
public class MatchDumpParser {

	public static Match parse(TeamService teamService, List<String[]> lines) throws IOException {
		List<String> problems = new ArrayList<>();

		List<MatchRoundPlayerFinish> parsedFinishes = new ArrayList<>();
		Map<String, MatchPlayer> playerMap = new HashMap<>();

		for (String[] line : lines) {
			parsedFinishes.add(parseFinish(teamService, playerMap, line));
		}


		// figure out point repartition from round 1, and repair/fill based on next rounds of first map
		List<Integer> pointRepartition = new ArrayList<>();
		boolean roundZeroPassed = false;
		int checkIndex = 0;
		int checkRound = 0;
		for (MatchRoundPlayerFinish parsedFinish : parsedFinishes) {
			// use first map as point repartition capture
			if (roundZeroPassed) {
				if (parsedFinish.getRoundNumber() == 0) {
					break;
				}
			}

			if (parsedFinish.getRoundNumber() == 0) {
				pointRepartition.add(parsedFinish.getScore());
			}
			if (parsedFinish.getRoundNumber() > 0) {
				roundZeroPassed = true;

				if (parsedFinish.getRoundNumber() != checkRound) {
					checkRound = parsedFinish.getRoundNumber();
					checkIndex = 0;
				}

				// make sure no values are missing from point repartition
				if (pointRepartition.size() < checkIndex) {
					pointRepartition.add(parsedFinish.getScore());
				}

				// overwrite 0 values with higher value if present
				Integer scoreForIndex = pointRepartition.get(checkIndex);
				if (scoreForIndex == 0 && parsedFinish.getScore() > 0) {
					pointRepartition.set(checkIndex, parsedFinish.getScore());
				}

				checkIndex++;
			}
		}

		log.info("Found point repartition: " + String.join(",", pointRepartition.stream().map(Object::toString).toList()));

		// validate point repartitions against every round
		int finisherIndex = 0;
		int curRoundNum = 0;
		int lineNumber = 2;
		for (MatchRoundPlayerFinish parsedFinish : parsedFinishes) {
			if (parsedFinish.getRoundNumber() != curRoundNum) {
				curRoundNum = parsedFinish.getRoundNumber();
				finisherIndex = 0;
			}
			int expectedScore = pointRepartition.size() < finisherIndex ? -1 : pointRepartition.get(finisherIndex);
			if (parsedFinish.getScore() == 0 && expectedScore != 0) {
				problems.add("Found unexpected 0 score (expected " + expectedScore + ") around line " + lineNumber);
			}

			finisherIndex++;
			lineNumber++;
		}

		// continue parse
		Set<Integer> roundNumbers = new TreeSet<>();
		for (MatchRoundPlayerFinish round : parsedFinishes) {
			roundNumbers.add(round.getRoundNumber());
		}

		int mapServerId = -1;
		Set<MatchRound> rounds = new TreeSet<>();
		Set<MatchRoundPlayerFinish> finishesForCurrentRound = new TreeSet<>();
		int currentRound = -1;
		for (MatchRoundPlayerFinish parsedFinish : parsedFinishes) {
			if (parsedFinish.getRoundNumber() != currentRound) {
				currentRound = parsedFinish.getRoundNumber();
				if (currentRound == 0) {
					mapServerId++;
				}
				if (!finishesForCurrentRound.isEmpty()) {
					rounds.add(new MatchRound(mapServerId, finishesForCurrentRound.stream().findFirst().get().getRoundNumber(), finishesForCurrentRound));
				}
			}

			finishesForCurrentRound.add(parsedFinish);
		}
		if (!finishesForCurrentRound.isEmpty()) {
			rounds.add(new MatchRound(mapServerId, finishesForCurrentRound.stream().findFirst().get().getRoundNumber(), finishesForCurrentRound));
		}

		problems.addAll(findProblems(playerMap, rounds));

		return new Match(new TreeSet<>(playerMap.values()), rounds, problems);
	}

	private static List<String> findProblems(Map<String, MatchPlayer> playerMap, Set<MatchRound> rounds) {
		// result
		List<String> problems = new ArrayList<>();

		// detect map count per player
		Map<String, Set<String>> playerIdToMapNameSet = new HashMap<>();
		for (MatchRound round : rounds) {
			for (MatchRoundPlayerFinish finisher : round.getFinishers()) {
				playerIdToMapNameSet.putIfAbsent(finisher.getPlayerId(), new HashSet<>());
				playerIdToMapNameSet.get(finisher.getPlayerId()).add(finisher.getTrackName());
			}
		}

		for (Map.Entry<String, Set<String>> e : playerIdToMapNameSet.entrySet()) {
			String playerId = e.getKey();
			Set<String> mapNames = e.getValue();
			if (mapNames.size() > 2) {
				String playerName = playerMap.get(playerId).getPlayerName();
				List<String> scoresPerMap = mapNames.stream().map(map -> "%s: %d".formatted(map, getPlayerScoreForMap(rounds, playerId, map))).toList();
				problems.add(playerName + " has finishes on " + mapNames.size() + " maps (%s)".formatted(String.join(", ", scoresPerMap)));
			}
		}

		// detect every team occurs once per round
		Set<PlayerMapOccurrence> reportedPlayers = new HashSet<>();
		for (MatchRound round : rounds) {
			Set<String> foundTeamsForRound = new HashSet<>();
			for (MatchRoundPlayerFinish finisher : round.getFinishers()) {
				String teamName = playerTeam(playerMap, finisher.getPlayerId());
				PlayerMapOccurrence occurrence = new PlayerMapOccurrence(finisher.getPlayerId(), finisher.getTrackName());
				if (teamName == null) {
					if (reportedPlayers.contains(occurrence)) {
						continue;
					}
					reportedPlayers.add(occurrence);
					problems.add(playerName(playerMap, finisher.getPlayerId()) + " has finished rounds on " + finisher.getTrackName() + " but could not resolve to team");
				} else {
					if (foundTeamsForRound.contains(teamName)) {
						if (reportedPlayers.contains(occurrence)) {
							continue;
						}
						reportedPlayers.add(occurrence);
						problems.add(playerName(playerMap, finisher.getPlayerId()) + " has finish on " + finisher.getTrackName() + " but another team member was already found in the same server/map");
					}
					foundTeamsForRound.add(teamName);
				}
			}
		}

		// detect every team is on every server/map

		Set<String> teamNames = new HashSet<>();
		for (MatchPlayer value : playerMap.values()) {
			teamNames.add(value.getTeamName());
		}
		Integer currentMapServerId = -1;
		Set<String> foundTeamsForMapServerId = new HashSet<>();
		for (MatchRound round : rounds) {
			if (!Objects.equals(round.getMapServerId(), currentMapServerId)) {
				if (!foundTeamsForMapServerId.isEmpty()) {
					checkMissingTeamsForMapServer(currentMapServerId, problems, teamNames, foundTeamsForMapServerId);
				}
				currentMapServerId = round.getMapServerId();
				foundTeamsForMapServerId.clear();
			}

			for (MatchRoundPlayerFinish finisher : round.getFinishers()) {
				String teamName = playerTeam(playerMap, finisher.getPlayerId());
				if (teamName != null) {
					foundTeamsForMapServerId.add(teamName);
				}
			}
		}
		if (!foundTeamsForMapServerId.isEmpty()) {
			checkMissingTeamsForMapServer(currentMapServerId, problems, teamNames, foundTeamsForMapServerId);
		}


		return problems;
	}

	private static void checkMissingTeamsForMapServer(Integer mapServerId, List<String> problems, Set<String> expectedTeams, Set<String> mapServerTeams) {
		if (expectedTeams.size() <= mapServerTeams.size()) {
			return;
		}

		Set<String> teamsNotFound = new HashSet<>(expectedTeams);
		for (String mapServerTeam : mapServerTeams) {
			teamsNotFound.remove(mapServerTeam);
		}

		if (!teamsNotFound.isEmpty()) {
			problems.add("Missing teams for map-server-id %d: %s".formatted(
					mapServerId,
					String.join(", ", teamsNotFound)
			));
		}
	}

	private static String playerName(Map<String, MatchPlayer> playerMap, String playerId) {
		MatchPlayer matchPlayer = playerMap.get(playerId);
		if (matchPlayer == null) {
			return playerId;
		}
		return matchPlayer.getPlayerName();
	}

	private static String playerTeam(Map<String, MatchPlayer> playerMap, String playerId) {
		MatchPlayer matchPlayer = playerMap.get(playerId);
		if (matchPlayer == null) {
			return null;
		}
		return matchPlayer.getTeamName();
	}

	private static int getPlayerScoreForMap(Set<MatchRound> rounds, String mapName, String playerId) {
		int s = 0;
		for (MatchRound round : rounds) {
			for (MatchRoundPlayerFinish finisher : round.getFinishers()) {
				if (finisher.getPlayerId().equals(playerId) &&
					finisher.getTrackName().equals(mapName)) {
					s += finisher.getScore();
				}
			}
		}
		return s;
	}

	private static MatchRoundPlayerFinish parseFinish(TeamService teamService, Map<String, MatchPlayer> playerMap, String[] line) {
		String time = line[0];
		String track = line[1];
		String playerId = line[2];
		String playerName = line[3];
		String record = line[4];
		String roundNumber = line[5];
		String points = line[6];

		int pointsParsed = Integer.parseInt(points);

		String teamName = getPlayerTeam(teamService, playerId);

		MatchPlayer mp = playerMap.computeIfAbsent(playerId, pid -> new MatchPlayer(pid, playerName, teamName, 0));
		mp.setPoints(mp.getPoints() + pointsParsed);

		return new MatchRoundPlayerFinish(
				Long.parseLong(time),
				track,
				playerId,
				Long.parseLong(record),
				Integer.parseInt(roundNumber),
				Integer.parseInt(points)
		);
	}

	private static String getPlayerTeam(TeamService teamService, String playerId) {
		JdbcTeam team = teamService.getPlayerTeam(playerId);
		if (team == null) {
			return "";
		}
		return team.getName();
	}

	private record PlayerMapOccurrence(String playerId, String mapName) {
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			PlayerMapOccurrence that = (PlayerMapOccurrence) o;
			return Objects.equals(mapName, that.mapName) && Objects.equals(playerId, that.playerId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(playerId, mapName);
		}
	}

}
