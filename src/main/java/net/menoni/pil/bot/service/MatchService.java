package net.menoni.pil.bot.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.utils.FileUpload;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.repository.MatchRepository;
import net.menoni.pil.bot.util.RoundType;
import net.menoni.spring.commons.service.CsvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchService {

	@Autowired
	private MatchRepository matchRepository;
	@Autowired
	private TeamService teamService;
	@Autowired
	private CsvService csvService;

	public void setMatchChannel(int division, int roundNumber, Long firstTeamId, Long secondTeamId, String matchChannelId) {
		JdbcMatch match = matchRepository.find(division, roundNumber, firstTeamId, secondTeamId);
		if (match != null) {
			match.setMatchChannelId(matchChannelId);
			match.setWinTeamId(null);
			matchRepository.save(match);
		} else {
			match = new JdbcMatch(null, division, roundNumber, firstTeamId, secondTeamId, matchChannelId, null, null, null);
			matchRepository.save(match);
		}
	}

	public boolean isMatchChannel(String channelId) {
		return getMatchForChannel(channelId) != null;
	}

	public JdbcMatch getMatchForChannel(String channelId) {
		return matchRepository.findByChannel(channelId);
	}

	public JdbcMatch getMatchExact(int division, int roundNumber, Long winTeamId, Long loseRoleId) {
		return matchRepository.find(division, roundNumber, winTeamId, loseRoleId);
	}

	public JdbcMatch updateMatch(JdbcMatch match) {
		return matchRepository.save(match);
	}

	public List<JdbcMatch> getMatchesForRound(int round) {
		return this.matchRepository.findAllForRound(round);
	}

	public FileUpload createEndRoundCsv(RoundType roundType, int roundNumber, List<JdbcMatch> matches) {
		Map<Long, JdbcTeam> idTeams = teamService.getAllTeams().stream().collect(Collectors.toMap(JdbcTeam::getId, t -> t));

		try {
			String[] headers = new String[] { "Type", "Round", "Division", "WinTeam", "LoseTeam", "WinTeamScore", "LoseTeamScore", "FF-Flag"};
			List<Object[]> lines = new ArrayList<>();
			matches.forEach(match -> {
				JdbcTeam teamWin = idTeams.get(match.getWinTeamId());
				JdbcTeam teamLose = idTeams.get(Objects.equals(match.getWinTeamId(), match.getFirstTeamId()) ? match.getSecondTeamId() : match.getFirstTeamId());

				int adjustedRoundNumber = roundType.adjustRoundNumber(roundNumber);

				int winscore = match.getWinTeamScore();
				if (match.getWinTeamScore() == 0) {
					winscore = 2;
				}

				lines.add(new Object[] {
						roundType.name(),
						Integer.toString(adjustedRoundNumber),
						Integer.toString(match.getDivision()),
						teamWin.getName(),
						teamLose.getName(),
						Integer.toString(winscore),
						Integer.toString(match.getLoseTeamScore()),
						(match.getWinTeamScore() == 0 ? "FF" : ""),
				});
			});
			return FileUpload.fromData(csvService.create(headers, lines), "round_%d_export_%d.csv".formatted(roundNumber, System.currentTimeMillis()));
		} catch (IOException e) {
			log.error("Failed to write round-end CSV", e);
			return null;
		}
	}

	public List<JdbcMatch> getMatchesForTeam(Long teamId) {
		return this.matchRepository.findMatchesForTeam(teamId);
	}
}
