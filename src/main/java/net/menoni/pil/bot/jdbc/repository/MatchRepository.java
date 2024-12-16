package net.menoni.pil.bot.jdbc.repository;

import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.spring.commons.jdbc.AbstractTypeRepository;
import net.menoni.spring.commons.util.NullableMap;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MatchRepository extends AbstractTypeRepository<JdbcMatch> {

	public JdbcMatch find(int division, int roundNumber, Long firstTeamId, Long secondTeamId) {
		return this.queryOne(
				"SELECT id, division, round_number, first_team_id, second_team_id, match_channel_id, win_team_id, win_team_score, lose_team_score FROM `match` WHERE division = ? AND round_number = ? AND " +
				"(first_team_id = ? AND second_team_id = ?) OR (second_team_id = ? AND first_team_id = ?)",
				division, roundNumber, firstTeamId, secondTeamId, firstTeamId, secondTeamId
		);
	}

	public JdbcMatch save(JdbcMatch match) {
		if (match.getId() == null) {
			GeneratedKeyHolder key = this.insertOne(
					"INSERT INTO `match` (division, round_number, first_team_id, second_team_id, match_channel_id, win_team_id, win_team_score, lose_team_score) VALUE " +
							"(:division, :roundNumber, :firstTeamId, :secondTeamId, :matchChannelId, :winTeamId, :winTeamScore, :loseTeamScore)",
					match
			);
			if (key != null) {
				match.setId(key.getKey().longValue());
			}
		} else {
			this.update("UPDATE `match` SET first_team_id = :firstTeamId, second_team_id = :secondTeamId, match_channel_id = :matchChannelId, " +
					"win_team_id = :winTeamId, win_team_score = :winTeamScore, lose_team_score = :loseTeamScore WHERE id = :id", NullableMap.create()
							.add("firstTeamId", match.getFirstTeamId())
					.add("secondTeamId", match.getSecondTeamId())
					.add("matchChannelId", match.getMatchChannelId())
					.add("winTeamId", match.getWinTeamId())
					.add("winTeamScore", match.getWinTeamScore())
					.add("loseTeamScore", match.getLoseTeamScore())
					.add("id", match.getId())
			);
		}
		return match;
	}

	public JdbcMatch findByChannel(String channelId) {
		return this.queryOne(
				"SELECT id, division, round_number, first_team_id, second_team_id, match_channel_id, win_team_id, win_team_score, lose_team_score FROM `match` WHERE match_channel_id = ?",
				channelId
		);
	}

	public List<JdbcMatch> findAllForRound(int round) {
		return this.queryMany(
				"SELECT id, division, round_number, first_team_id, second_team_id, match_channel_id, win_team_id, win_team_score, lose_team_score FROM `match` WHERE round_number = ?",
				round
		);
	}
}
