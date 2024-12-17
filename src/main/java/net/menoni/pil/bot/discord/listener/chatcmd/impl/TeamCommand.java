package net.menoni.pil.bot.discord.listener.chatcmd.impl;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.emote.Emotable;
import net.menoni.pil.bot.discord.listener.chatcmd.ChatCommand;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcMember;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.jdbc.repository.TeamSignupRepository;
import net.menoni.pil.bot.service.MatchService;
import net.menoni.pil.bot.service.MemberService;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.util.Obj;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

public class TeamCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("team");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.MANAGE_CHANNEL);
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length == 0) {
			sendHelp(channel, null);
			return true;
		}

		if (args[0].equalsIgnoreCase("list")) {
			return this._execute_list(applicationContext, channel, member, message, alias, args);
		} else if (args[0].equalsIgnoreCase("div")) {
			return this._execute_div(applicationContext, channel, member, message, alias, args);
		} else if (args[0].equalsIgnoreCase("emote")) {
			return this._execute_emote(applicationContext, channel, member, message, alias, args);
		} else if (args[0].equalsIgnoreCase("delete")) {
			return this._execute_delete(applicationContext, channel, member, message, alias, args);
		}

		sendHelp(channel, "invalid action");
		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!team -- show help",
				"!team list -- list all teams",
				"!team div <division> <team1> [team2...] -- set one or more team divisions",
				"!team div remove <team1> [team2...] -- remove one or more team divisions",
				"!team emote <team-role> <emote> -- set team emote",
				"!team emote <team-role> delete -- remove team emote",
				"!team delete <team-role> -- delete a team fully from the discord and bot DB"
		);
	}

	private boolean _execute_list(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		TeamService teamService = applicationContext.getBean(TeamService.class);
		TeamSignupRepository signups = applicationContext.getBean(TeamSignupRepository.class);
		MemberService memberService = applicationContext.getBean(MemberService.class);

		Map<Integer, List<JdbcTeam>> divTeams = new TreeMap<>(teamService.getAllTeams().stream().collect(Collectors.groupingBy(p -> Obj.or(p.getDivision(), -1))));
		Map<Long, JdbcTeamSignup> teamIdMappedCaptains = signups.getAllSignups().stream().filter(JdbcTeamSignup::isTeamLead).collect(Collectors.toMap(JdbcTeamSignup::getTeamId, s -> s));

		List<String> captainDiscordNames = teamIdMappedCaptains.values().stream().map(JdbcTeamSignup::getDiscordName).toList();
		List<JdbcMember> allServerMembers = memberService.getAll();
		Map<String, JdbcMember> nameMappedDiscordMembers = allServerMembers.stream().filter(m -> captainDiscordNames.contains(m.getDiscordName())).collect(Collectors.toMap(JdbcMember::getDiscordName, m -> m));

		StringBuilder sb = new StringBuilder("# Teams\n");

		for (Map.Entry<Integer, List<JdbcTeam>> e : divTeams.entrySet()) {
			Integer div = e.getKey();
			TreeSet<JdbcTeam> sortedTeams = new TreeSet<>(Comparator.comparing(JdbcTeam::getName));
			sortedTeams.addAll(e.getValue());

			if (div != null && div > 0) {
				sb.append("## Division %d:\n".formatted(div));
			} else {
				sb.append("## Division Not Assigned\n");
			}
			for (JdbcTeam sortedTeam : sortedTeams) {
				JdbcTeamSignup captainSignup = teamIdMappedCaptains.get(sortedTeam.getId());
				JdbcMember captainMember = null;
				if (captainSignup != null) {
					captainMember = nameMappedDiscordMembers.get(captainSignup.getDiscordName());
				}
				String captainNameDisplay = "(unknown?)";
				String captainNotInServerDisplay = "";
				if (captainMember != null) {
					captainNameDisplay = DiscordFormattingUtil.memberAsString(captainMember.getDiscordId());
				} else if (captainSignup != null) {
					captainNameDisplay = captainSignup.getDiscordName();
					captainNotInServerDisplay = " **(NOT IN SERVER)**";
				}
				String emote = "";
				if (sortedTeam.getEmoteName() != null && sortedTeam.getEmoteId() != null) {
					emote = "%s ".formatted(Emotable.printById(sortedTeam.getEmoteName(), sortedTeam.getEmoteId(), false));
				}
				sb.append("- **Team:** ")
						.append(emote)
						.append(DiscordFormattingUtil.roleAsString(sortedTeam.getDiscordRoleId()))
						.append(" -> **Captain:** ")
						.append(captainNameDisplay)
						.append(captainNotInServerDisplay)
						.append("\n");

				if (sortedTeam.getEmoteId() == null || sortedTeam.getEmoteName() == null) {
					if (sortedTeam.getImageUrl() != null && !sortedTeam.getImageUrl().isBlank()) {
						sb.append("_emote not set - signup image:_ <").append(sortedTeam.getImageUrl()).append(">\n");
					} else {
						sb.append("_emote not set - no image provided_\n");
					}
				}
			}
		}
		JDAUtil.queueAndWait(channel.sendMessage(sb.toString()));
		return true;
	}

	private boolean _execute_div(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 3) {
			sendHelp(channel, "Not enough arguments");
			return true;
		}
		String divArgStr = args[1];
		String[] teamArgsStr = Arrays.copyOfRange(args, 2, args.length);

		int division = -1;

		if (!divArgStr.equalsIgnoreCase("remove")) {
			try {
				division = Integer.parseInt(divArgStr);

				if (division < 1 || division > 10) {
					reply(channel, "team", "Division needs to be between 1-10");
					return true;
				}
			} catch (NumberFormatException e) {
				reply(channel, "team", "Invalid division number input: " + divArgStr + " -- expected (positive) number");
				return true;
			}
		}

		TeamService teamService = applicationContext.getBean(TeamService.class);

		StringBuilder sb = new StringBuilder("**Updating Team Divisions:**\n");
		for (String s : teamArgsStr) {
			String roleId = DiscordArgUtil.getRoleId(s);
			sb.append("- ");
			if (roleId == null) {
				sb.append(s).append(" (error: not a discord role)\n");
				continue;
			}
			JdbcTeam team = teamService.getTeamByRoleId(roleId);
			if (team == null) {
				sb.append(s).append(" (error: not a team discord role)\n");
				continue;
			}
			if (division > 0) {
				sb.append(s).append(" div updated to **").append(division).append("**\n");
			} else {
				sb.append(s).append(" div removed\n");
			}
			teamService.updateTeamDiv(team, division);
		}

		reply(channel, "team", sb.toString());
		teamService.updateTeamsMessage();
		return true;
	}

	private boolean _execute_emote(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 3) {
			sendHelp(channel, null);
			return true;
		}

		String roleArg = args[1];
		String emoteArg = args[2];

		if (!DiscordArgUtil.isRole(roleArg)) {
			sendHelp(channel, "Second argument needs to be a @ role");
			return true;
		}


		String teamRoleId = DiscordArgUtil.getRoleId(roleArg);

		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		TeamService teamService = applicationContext.getBean(TeamService.class);
		Role teamRole = bot.getRoleById(teamRoleId);

		if (teamRole == null) {
			sendHelp(channel, "Role does not exist");
			return true;
		}

		DiscordArgUtil.ParsedEmote parsedEmote = DiscordArgUtil.parseEmoteArg(emoteArg);
		boolean deleteEmote = false;
		if (emoteArg.equalsIgnoreCase("delete") || emoteArg.equalsIgnoreCase("remove")) {
			deleteEmote = true;
		} else if (parsedEmote == null) {
			sendHelp(channel, "Emote could not be found");
			return true;
		}

		JdbcTeam team = teamService.getTeamByRoleId(teamRoleId);
		if (team == null) {
			sendHelp(channel, "Team does not exist");
			return true;
		}

		if (deleteEmote) {
			teamService.updateTeamEmote(team, null);
			reply(channel, "team", "Removed emote for %s".formatted(DiscordFormattingUtil.roleAsString(teamRoleId)));
		} else {
			RichCustomEmoji emoji = bot.applyGuild(g -> g.getEmojiById(parsedEmote.emoteId()), null);
			if (emoji == null) {
				sendHelp(channel, "Emoji could not be found in this server");
				return true;
			}
			team = teamService.updateTeamEmote(team, parsedEmote);
			reply(channel, "team", "Changed emote for %s to %s".formatted(
					DiscordFormattingUtil.roleAsString(teamRoleId),
					DiscordFormattingUtil.teamEmoteAsString(team)
			));
		}

		teamService.updateTeamsMessage();
		return true;
	}

	private boolean _execute_delete(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 2) {
			sendHelp(channel, "Not enough arguments");
			return true;
		}

		String roleArg = args[1];
		String roleId = DiscordArgUtil.getRoleId(roleArg);
		if (roleId == null) {
			reply(channel, "team", "Argument is not a role");
			return true;
		}

		TeamService teamService = applicationContext.getBean(TeamService.class);
		JdbcTeam team = teamService.getTeamByRoleId(roleId);

		if (team == null) {
			reply(channel, "team", "Team not found by requested role: " + roleArg);
			return true;
		}

		MatchService matchService = applicationContext.getBean(MatchService.class);
		List<JdbcMatch> matches = matchService.getMatchesForTeam(team.getId());
		if (!matches.isEmpty()) {
			reply(channel, "team", "Team is already linked to match history - ask dev for help");
			return true;
		}

		// unlink from members
		MemberService memberService = applicationContext.getBean(MemberService.class);
		memberService.removeTeam(team.getId());

		// remove team & signups
		teamService.deleteTeam(team);

		teamService.updateTeamsMessage();

		reply(channel, "team", "**Deleted team:** " + team.getName());
		return true;
	}

}