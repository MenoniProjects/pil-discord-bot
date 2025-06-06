package net.menoni.pil.bot.jdbc.repository;

import net.dv8tion.jda.api.entities.Member;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.spring.commons.jdbc.AbstractTypeRepository;
import net.menoni.spring.commons.util.NullableMap;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class TeamSignupRepository extends AbstractTypeRepository<JdbcTeamSignup> {

	public JdbcTeamSignup getSignupForTrackmaniaId(String trackmaniaId) {
		if (trackmaniaId == null) {
			return null;
		}
		return this.queryOne(
				"SELECT id, team_id, discord_name, trackmania_name, trackmania_uuid, team_lead, hidden, archived FROM team_signup WHERE trackmania_uuid = ? AND archived = false",
				trackmaniaId
		);
	}

	public JdbcTeamSignup getSignupForMember(Member member) {
		if (member == null) {
			return null;
		}
		String name = member.getUser().getName();
		return this.queryOne(
				"SELECT id, team_id, discord_name, trackmania_name, trackmania_uuid, team_lead, hidden, archived FROM team_signup WHERE discord_name = ? AND archived = false",
				name
		);
	}

	public JdbcTeamSignup saveSignup(JdbcTeamSignup signup) {
		if (signup == null) {
			return null;
		}
		if (signup.isArchived()) {
			return null;
		}
		if (signup.getId() == null) {
			GeneratedKeyHolder key = this.insertOne(
					"INSERT INTO team_signup (team_id, discord_name, trackmania_name, trackmania_uuid, team_lead, hidden, archived) VALUES (:teamId, :discordName, :trackmaniaName, :trackmaniaUuid, :teamLead, :hidden, :archived)",
					signup
			);
			signup.setId(key.getKey().longValue());
		} else {
			this.update(
					"UPDATE team_signup SET team_id = :teamId, discord_name = :discordName, trackmania_name = :trackmaniaName, trackmania_uuid = :trackmaniaUuid, team_lead = :teamLead, hidden = :hidden WHERE id = :id",
					NullableMap.create()
							.add("teamId", signup.getTeamId())
							.add("discordName", signup.getDiscordName())
							.add("trackmaniaName", signup.getTrackmaniaName())
							.add("trackmaniaUuid", signup.getTrackmaniaUuid())
							.add("teamLead", signup.isTeamLead())
							.add("hidden", signup.isHidden())
							.add("archived", false)
							.add("id", signup.getId())
			);
		}
		return signup;
	}

	public void deleteSignup(JdbcTeamSignup signup) {
		if (signup == null) {
			return;
		}
		if (signup.getId() == null) {
			return;
		}
		this.update("DELETE FROM team_signup WHERE id = :id", Map.of("id", signup.getId()));
	}

	public List<JdbcTeamSignup> getAllSignups() {
		return this.queryMany("SELECT id, team_id, discord_name, trackmania_name, trackmania_uuid, team_lead, hidden, archived FROM team_signup WHERE archived = false");
	}

	public void deleteSignupsForTeam(Long teamId) {
		this.update("DELETE FROM team_signup WHERE team_id = :id", Map.of("id", teamId));
	}
}
