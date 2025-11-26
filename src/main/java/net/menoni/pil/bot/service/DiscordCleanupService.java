package net.menoni.pil.bot.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IGuildChannelContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.jda.commons.util.DiscordRoleUtil;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.repository.MatchRepository;
import net.menoni.pil.bot.jdbc.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class DiscordCleanupService {

	@Autowired private ApplicationContext applicationContext;
	@Autowired private DiscordBot bot;

	@PostConstruct
	public void init() {
//		this.cleanupNonFinalsMatchChannels();
//		this.deleteTeamRolesAndUnassignOtherRoles().join();

//		this.exit();
	}

	private void exit() {
		SpringApplication.exit(applicationContext, () -> 0);
		System.exit(0);
	}

	private CompletableFuture<?> deleteTeamRolesAndUnassignOtherRoles() {
		TeamRepository teamRepository = applicationContext.getBean(TeamRepository.class);
		List<JdbcTeam> teams = teamRepository.getAll();

		for (JdbcTeam team : teams) {
			Role role = bot.getRoleById(team.getDiscordRoleId());
			if (role == null) {
				log.info("Role not found for team {}", team.getName());
				continue;
			}
			log.info("Deleting role for team {}", team.getName());
			JDAUtil.queueAndWait(role.delete());
		}

		log.info("Finding all roles that need to be removed from members");
		List<Role> rolesToRemoveFromMembers = new ArrayList<>();
		Role playerRole = bot.getPlayerRole();
		Role captainRole = bot.getTeamLeadRole();
		if (playerRole != null) {
			rolesToRemoveFromMembers.add(playerRole);
		}
		if (captainRole != null) {
			rolesToRemoveFromMembers.add(captainRole);
		}

		for (int i = 1; i < 10; i++) {
			String divRoleId = bot.getConfig().getTeamMemberDivRole(i);
			String captainDivRoleId = bot.getConfig().getTeamCaptainDivRole(i);

			Role divRole = bot.getRoleById(divRoleId);
			Role captainDivRole = bot.getRoleById(captainDivRoleId);

			if (divRole != null) {
				rolesToRemoveFromMembers.add(divRole);
			}
			if (captainDivRole != null) {
				rolesToRemoveFromMembers.add(captainDivRole);
			}
		}

		CompletableFuture<?> future = new CompletableFuture<>();

		log.info("Loading all members");
		this.bot.getGuild(this.bot.getGuildId()).loadMembers()
				.onSuccess(members -> {
					for (Member member : members) {
						DiscordRoleUtil.RoleUpdateAction u = DiscordRoleUtil.updater(member);
						for (Role r : rolesToRemoveFromMembers) {
							u.conditional(r, false);
						}
						log.info("Removing roles for {}", member.getEffectiveName());
						u.modifyMemberRoles(JDAUtil::queueAndWait);
					}
					log.info("Finished!");
					future.complete(null);
				}).onError(err -> {
					log.error("Failed to load discord members", err);
					future.completeExceptionally(err);
				});

		return future;
	}

	private void cleanupNonFinalsMatchChannels() {
		MatchRepository matchRepository = applicationContext.getBean(MatchRepository.class);
		List<JdbcMatch> matches = matchRepository.all();
		matches = new ArrayList<>(matches.stream().filter(m -> m.getRoundNumber() < 20).sorted(Comparator.comparingLong(JdbcMatch::getId)).toList());

		List<TextChannel> channels = bot.applyGuild(IGuildChannelContainer::getTextChannels, List.of());

		for (JdbcMatch match : matches) {
			TextChannel matchChannel = channels.stream().filter(c -> Objects.equals(c.getId(), match.getMatchChannelId())).findAny().orElse(null);
			String matchChannelDisplay = "<not found>";
			if (matchChannel != null) {
				matchChannelDisplay = "#" + matchChannel.getName();
			}
			log.info("Match {} channel: {}", match.getId(), matchChannelDisplay);
			if (matchChannel != null) {
				JDAUtil.queueAndWait(matchChannel.delete());
			}
		}
	}

}
