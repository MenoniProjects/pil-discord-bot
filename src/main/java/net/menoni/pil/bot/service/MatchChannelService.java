package net.menoni.pil.bot.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.managers.channel.attribute.IPermissionContainerManager;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.util.RoundType;
import net.menoni.pil.bot.util.TemporaryValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class MatchChannelService {

	@Autowired
	private DiscordBot bot;

	@Autowired
	private TeamService teamService;
	@Autowired
	private MatchService matchService;

	@Getter
	private final TemporaryValue<String> pinMessageContent = TemporaryValue.empty(Duration.ofHours(1L));

	public String endRoundArchiveChannels(int round, List<JdbcMatch> matches) {
		List<JdbcTeam> allTeams = teamService.getAllTeams();
		if (matches == null || matches.isEmpty()) {
			return "No matches found for round " + round;
		}
		int channelsWithPermUpdateFails = 0;

		Role casterRole = bot.getRoleById(bot.getConfig().getCasterRoleId());
		List<TextChannel> channelsToProcess = new ArrayList<>();
		for (JdbcMatch match : matches) {
			if (match.getMatchChannelId() == null) {
				continue;
			}
			TextChannel matchChannel = bot.applyGuild(g -> g.getTextChannelById(match.getMatchChannelId()), null);
			if (matchChannel == null) {
				continue;
			}

			channelsToProcess.add(matchChannel);

			JdbcTeam team1 = allTeams.stream().filter(t -> t.getId().equals(match.getFirstTeamId())).findFirst().orElse(null);
			JdbcTeam team2 = allTeams.stream().filter(t -> t.getId().equals(match.getSecondTeamId())).findFirst().orElse(null);

			Set<String> roleIds = new HashSet<>();
			if (team1 != null) {
				roleIds.add(team1.getDiscordRoleId());
			}
			if (team2 != null) {
				roleIds.add(team2.getDiscordRoleId());
			}
			if (casterRole != null) {
				roleIds.add(casterRole.getId());
			}

			IPermissionContainerManager<?, ?> manager = matchChannel.getPermissionContainer().getManager();
			boolean changes = false;
			for (PermissionOverride perm : matchChannel.getPermissionOverrides()) {
				if (!perm.isRoleOverride() || perm.getRole() == null) {
					continue;
				}
				if (!perm.getAllowed().contains(Permission.VIEW_CHANNEL)) {
					continue;
				}
				if (!roleIds.contains(perm.getRole().getId())) {
					continue;
				}
				manager.removePermissionOverride(perm.getRole());
				changes = true;
			}
			if (changes) {
				try {
					JDAUtil.queueAndWait(manager, (err) -> log.error("failed to edit permissions for channel #" + matchChannel.getName(), err));
				} catch (Throwable e) {
					channelsWithPermUpdateFails++;
				}
			}
		}

		int archivedChannelFailCount = 0;

		List<TextChannel> channelsToMoveCategory = new ArrayList<>(channelsToProcess);
		channelsToMoveCategory = channelsToMoveCategory.stream().filter(c -> {
			JdbcMatch match = matches.stream().filter(m -> Objects.equals(m.getMatchChannelId(), c.getId())).findAny().orElse(null);
			if (match != null) {
				String categoryId = bot.getConfig().getMatchesCategoryId(match.getDivision());
				if (categoryId != null) {
					if (!Objects.equals(c.getParentCategoryId(), categoryId)) {
						return false;
					}
				}
			}
			return true;
		}).toList();

		if (!channelsToMoveCategory.isEmpty()) {
			Category category = bot.applyGuild(g -> JDAUtil.queueAndWait(g.createCategory("archive r%d".formatted(round))), null);
			if (category != null) {
				for (TextChannel channel : channelsToMoveCategory) {
					try {
						JDAUtil.queueAndWait(channel.getManager().setParent(category), err ->
								log.error("failed to move channel #" + channel.getName() + " to category '" + category.getName() + "'", err)
						);
					} catch (Throwable e) {
						archivedChannelFailCount++;
					}
				}
			}
		}

		int matchCount = matches.size();
		int channelCount = channelsToProcess.size();

		StringBuilder sb = new StringBuilder();
		sb.append("## Channel updates\n");
		sb.append("- **matches found:** ").append(matchCount);
		if (channelCount < matchCount) {
			sb.append(" (").append(matchCount - channelCount).append(" without channels").append(")");
		}
		sb.append("\n");

		if (channelsWithPermUpdateFails > 0) {
			sb.append("- **Amount of channels with failed permission updates:** ").append(channelsWithPermUpdateFails).append("\n");
		}
		if (archivedChannelFailCount > 0) {
			sb.append("- **Amount of channels with archive action failed:** ").append(archivedChannelFailCount).append("\n");
		}
		return sb.toString();
	}

	public CompletableFuture<MatchChannelCreateResult> createMatchChannel(int division, int roundNum, Role teamRole1, Role teamRole2, String pinMessageContent) {
		CompletableFuture<MatchChannelCreateResult> future = new CompletableFuture<>();
		String divMatchCategoryId = bot.getConfig().getMatchesCategoryId(division);
		if (divMatchCategoryId == null) {
			future.completeExceptionally(new Exception("Match channel category not found for div %d (not configured)".formatted(division)));
			return future;
		}
		Category category = bot.applyGuild(g -> g.getCategoryById(divMatchCategoryId), null);
		if (category == null) {
			future.completeExceptionally(new Exception("Match channel category not found for div %d (configured but not found)".formatted(division)));
			return future;
		}

		RoundType roundType = RoundType.forRoundNumber(roundNum);
		if (roundType == null) {
			future.completeExceptionally(new Exception("Invalid round number: " + roundNum));
			return future;
		}

		ChannelAction<TextChannel> createAction = category
				.createTextChannel(roundType.matchChannelName(division, roundNum, teamRole1.getName(), teamRole2.getName()))
				.addRolePermissionOverride(category.getGuild().getIdLong(), null, List.of(Permission.VIEW_CHANNEL)) // not public
				.addPermissionOverride(bot.getBotRole(), List.of(Permission.VIEW_CHANNEL), null)
				.addPermissionOverride(teamRole1, List.of(Permission.VIEW_CHANNEL), null)
				.addPermissionOverride(teamRole2, List.of(Permission.VIEW_CHANNEL), null);

		Role adminRole = bot.getRoleById(bot.getConfig().getAdminRoleId());
		Role staffRole = bot.getRoleById(bot.getConfig().getStaffRoleId());
		Role casterRole = bot.getRoleById(bot.getConfig().getCasterRoleId());

		createAction = applyRoleViewAccess(createAction, adminRole);
		createAction = applyRoleViewAccess(createAction, staffRole);
		createAction = applyRoleViewAccess(createAction, casterRole);

		JdbcTeam team1 = teamService.getTeamByRoleId(teamRole1.getId());
		JdbcTeam team2 = teamService.getTeamByRoleId(teamRole2.getId());

		createAction.queue(c -> {
			matchService.setMatchChannel(division, roundNum, team1.getId(), team2.getId(), c.getId());

			c.sendMessage(pinMessageContent).queue(m ->
							m.pin().queue(
									(_void) -> future.complete(new MatchChannelCreateResult(c, null)),
									(pinErr) -> future.complete(new MatchChannelCreateResult(c, "Message sent but failed to pin the message"))
							), err -> {
						future.complete(new MatchChannelCreateResult(c, "Failed to send default pinned message: " + err.getMessage()));
						log.error("Failed to send default pinned message", err);
					}
			);
		}, future::completeExceptionally);
		return future;
	}

	private ChannelAction<TextChannel> applyRoleViewAccess(ChannelAction<TextChannel> action, Role role) {
		if (role != null) {
			return action.addPermissionOverride(role, List.of(Permission.VIEW_CHANNEL), null);
		}
		return action;
	}

	public record MatchChannelCreateResult(
			TextChannel channel,
			String additionalMessage
	) {
	}

}
