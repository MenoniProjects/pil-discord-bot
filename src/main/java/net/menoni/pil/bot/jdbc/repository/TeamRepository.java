package net.menoni.pil.bot.jdbc.repository;

import net.dv8tion.jda.api.entities.Role;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.spring.commons.jdbc.AbstractTypeRepository;
import net.menoni.spring.commons.util.NullableMap;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Repository
public class TeamRepository extends AbstractTypeRepository<JdbcTeam> {

	public JdbcTeam getById(Long id) {
		return this.queryOne("SELECT id, name, color, image_url, discord_role_id, emote_name, emote_id, division FROM team WHERE id = ?", id);
	}

	public JdbcTeam getByRoleId(String roleId) {
		return this.queryOne("SELECT id, name, color, image_url, discord_role_id, emote_name, emote_id, division FROM team WHERE discord_role_id = ?", roleId);
	}

	public List<JdbcTeam> getAll() {
		return this.queryMany("SELECT id, name, color, image_url, discord_role_id, emote_name, emote_id, division FROM team");
	}

	public CompletableFuture<JdbcTeam> ensureTeam(DiscordBot bot, String name, String color, String imageUrl) {
		CompletableFuture<JdbcTeam> future = new CompletableFuture<>();
		JdbcTeam team = this.queryOne("SELECT id, name, color, image_url, discord_role_id, emote_name, emote_id, division FROM team WHERE name = ?", name);
		if (team == null) {
			bot.withGuild(g ->
					g.createRole()
							.setName(name)
							.setColor(Color.decode(color))
							.setHoisted(true)
							.setPermissions(List.of())
							.queue(r -> {
								JdbcTeam newTeam = new JdbcTeam(null, name, color, imageUrl, r.getId(), null, null, null);
								GeneratedKeyHolder key = this.insertOne(
										"INSERT INTO team " +
										"(name, color, image_url, discord_role_id) VALUE " +
										"(:name, :color, :imageUrl, :discordRoleId)",
										newTeam
								);
								if (key != null) {
									newTeam.setId(key.getKey().longValue());
								}

								// attempt to move role to the bottom of the "-- teams" section
								String hoistDividerRoleId = bot.getConfig().getBotHoistDividerRoleId();
								if (hoistDividerRoleId != null) {
									Role dividerRole = g.getRoleById(hoistDividerRoleId);
									if (dividerRole != null) {
										JDAUtil.queueAndWaitConsume(
												g.modifyRolePositions().selectPosition(r).moveAbove(dividerRole),
												(_v) -> future.complete(newTeam),
												future::completeExceptionally
										);
										future.complete(newTeam);
										return;
									}
								}

								future.complete(newTeam);
							}, future::completeExceptionally),
					() -> future.completeExceptionally(new Exception("Guild not found"))
			);
		} else {
			future.complete(team);
		}
		return future;
	}

	public void deleteTeam(DiscordBot bot, JdbcTeam e) {
		if (e == null) {
			return;
		}
		if (e.getId() == null) {
			return;
		}
		this.update("DELETE FROM team WHERE id = :id", Map.of("id", e.getId()));
		if (e.getDiscordRoleId() != null) {
			bot.withGuild(g -> {
				Role role = g.getRoleById(e.getDiscordRoleId());
				if (role != null) {
					role.delete().queue();
				}
			});
		}
	}

	public JdbcTeam updateTeamEmote(JdbcTeam team) {
		this.update("UPDATE team SET emote_id = :emoteId, emote_name = :emoteName WHERE id = :id", NullableMap.of("emoteId", team.getEmoteId(), "emoteName", team.getEmoteName(), "id", team.getId()));
		return team;
	}

	public void updateDivision(JdbcTeam team) {
		this.update("UPDATE team SET division = :division WHERE id = :id", NullableMap.of("division", team.getDivision(), "id", team.getId()));
	}
}
