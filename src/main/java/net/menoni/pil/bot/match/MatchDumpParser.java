package net.menoni.pil.bot.match;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.service.TeamService;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

@Slf4j
public class MatchDumpParser {

	public static Match parse(TeamService teamService, InputStream stream) throws IOException, CsvException {
		CSVReader reader = new CSVReader(new InputStreamReader(stream));

		reader.skip(1); // header
		List<String[]> lines = reader.readAll();

		Set<MatchRoundPlayerFinish> parsedFinishes = new TreeSet<>();
		Map<String, MatchPlayer> playerMap = new HashMap<>();
		for (String[] line : lines) {
			parsedFinishes.add(parseFinish(teamService, playerMap, line));
		}

		Long earliestFinish = Long.MAX_VALUE;
		Long latestFinish = Long.MIN_VALUE;
		Set<Integer> roundNumbers = new TreeSet<>();
		for (MatchRoundPlayerFinish round : parsedFinishes) {
			roundNumbers.add(round.getRoundNumber());

			if (round.getTime() > latestFinish) {
				latestFinish = round.getTime();
			}
			if (round.getTime() < earliestFinish) {
				earliestFinish = round.getTime();
			}
		}

		Set<MatchRound> rounds = new TreeSet<>();
		for (Integer roundNumber : roundNumbers) {
			Set<MatchRoundPlayerFinish> finishedRounds = new TreeSet<>(parsedFinishes.stream().filter(r -> Objects.equals(r.getRoundNumber(), roundNumber)).toList());

			rounds.add(new MatchRound(roundNumber, finishedRounds));
		}

		return new Match(earliestFinish, latestFinish, new TreeSet<>(playerMap.values()), rounds);
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

}
