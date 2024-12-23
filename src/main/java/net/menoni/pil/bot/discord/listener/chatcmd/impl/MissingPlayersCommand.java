package net.menoni.pil.bot.discord.listener.chatcmd.impl;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.listener.ChatCommandListener;
import net.menoni.pil.bot.discord.listener.chatcmd.ChatCommand;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.service.TeamService;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MissingPlayersCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("missingplayers");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.MANAGE_ROLES);
	}

	@Override
	public String shortHelpText() {
		return "Check if any team captains are not in the discord server";
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member) {
		return ChatCommandListener.requireBotCmdChannel(applicationContext, channel);
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		boolean includeNonTeamLeads = false;
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("all")) {
				includeNonTeamLeads = true;
			}
		}
		boolean includeNonTeamLeadsFinal = includeNonTeamLeads;
		TeamService teamService = applicationContext.getBean(TeamService.class);
		Map<Long, JdbcTeam> idTeams = teamService.getAllTeams().stream().collect(Collectors.toMap(JdbcTeam::getId, t -> t));
		List<JdbcTeamSignup> signups = teamService.getAllSignups();
		List<JdbcTeamSignup> teamLeads = signups.stream().filter(s -> s.isTeamLead()).toList();
		List<JdbcTeamSignup> teamNonLeads = signups.stream().filter(s -> !s.isTeamLead()).toList();

		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		bot.withGuild(g -> g.loadMembers().onSuccess(members -> {
			List<String> memberNames = members.stream().map(m -> m.getUser().getName().toLowerCase()).toList();

			List<JdbcTeamSignup> missingTeamLeads = findMissingMembers(teamLeads, memberNames);
			List<JdbcTeamSignup> missingTeamNonLeads = findMissingMembers(teamNonLeads, memberNames);

			StringBuilder sb = new StringBuilder();
			if (missingTeamLeads.isEmpty()) {
				sb.append("All team leads found\n");
			} else {
				sb.append("Missing team leads:\n");
				sb.append(factorMissingMembersString(missingTeamLeads, idTeams));
				sb.append("\n");
			}
			if (includeNonTeamLeadsFinal) {
				if (missingTeamNonLeads.isEmpty()) {
					sb.append("All team non-leads found");
				} else {
					sb.append("Missing other players:\n");
					sb.append(factorMissingMembersString(missingTeamNonLeads, idTeams));
				}
			}

			reply(channel, "missingplayers", sb.toString());
		}).onError(err -> {
			reply(channel, "missingplayers", "Failed to load discord members");
			log.error("failed to load all members", err);
		}));
		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of("!missingplayers -- checks which signed up players are not in the server");
	}

	private List<JdbcTeamSignup> findMissingMembers(List<JdbcTeamSignup> signups, List<String> serverMemberNames) {
		return signups.stream().filter(s -> !serverMemberNames.contains(s.getDiscordName().toLowerCase())).toList();
	}

	private String factorMissingMembersString(List<JdbcTeamSignup> signups, Map<Long, JdbcTeam> teams) {
		return "- " + signups.stream().map(s -> {
			JdbcTeam team = teams.get(s.getTeamId());
			return "%s: %s".formatted(
					team.getName(),
					s.getDiscordName()
			);
		}).collect(Collectors.joining("\n- "));
	}
}
