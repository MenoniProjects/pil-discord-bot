package net.menoni.pil.bot.discord.listener.chatcmd.impl;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.listener.chatcmd.ChatCommand;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.service.BotLogsService;
import net.menoni.pil.bot.service.MatchChannelService;
import net.menoni.pil.bot.service.MatchService;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class WinCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("win");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of();
	}

	@Override
	public String shortHelpText() {
		return "Submit score for this match as winner, check if `/win` is available instead";
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, boolean silent) {
		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		MatchService matchService = applicationContext.getBean(MatchService.class);
		// team lead requirement
		if (!matchService.isMatchChannel(channel.getId())) {
			if (!silent) {
				reply(channel, "win", "Command needs to be executed in a known match channel");
			}
			return false;
		}
		if (member.getRoles().stream().map(Role::getId).noneMatch(id -> Objects.equals(id, bot.getConfig().getTeamLeadRoleId()))) {
			if (!silent) {
				reply(channel, "win", "Only captains of a team can run this command");
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		TeamService teamService = applicationContext.getBean(TeamService.class);
		MatchService matchService = applicationContext.getBean(MatchService.class);

		JdbcMatch match = matchService.getMatchForChannel(channel.getId());
		if (match == null) {
			reply(channel, alias, "Match not found - <@%s>".formatted(bot.getConfig().getStaffRoleId()));
			return true;
		}
		// mark win
		JdbcTeamSignup teamMemberSignup = teamService.getSignupForMember(member);
		if (teamMemberSignup == null) {
			reply(channel, alias, "Could not find your sign-up entry - <@%s>".formatted(bot.getConfig().getStaffRoleId()));
			return true;
		}
		if (match.getWinTeamId() != null) {
			reply(channel, alias, "This match has already been reported as complete - ask staff to override results if needed.");
			return true;
		}

		String joinedArgs = Arrays.stream(args).map(String::trim).collect(Collectors.joining(""));
		DiscordArgUtil.ParsedMatchScore parsedMatchScore = DiscordArgUtil.parseMatchScoreArg(joinedArgs);
		if (parsedMatchScore == null) {
			sendHelp(channel, "Invalid score result argument");
			return true;
		}

		match.setWinTeamId(teamMemberSignup.getTeamId());
		match.setWinTeamScore(parsedMatchScore.winTeamScore());
		match.setLoseTeamScore(parsedMatchScore.loseTeamScore());
		matchService.updateMatch(match);

		JdbcTeam winTeam = teamService.getTeamById(teamMemberSignup.getTeamId());
		JdbcTeam loseTeam = teamService.getTeamById(Objects.equals(teamMemberSignup.getTeamId(), match.getFirstTeamId()) ? match.getSecondTeamId() : match.getFirstTeamId());

		reply(channel, alias, "Marked match as won by %s with result: %s".formatted(
				DiscordFormattingUtil.roleAsString(winTeam.getDiscordRoleId()),
				parsedMatchScore.print()
		));

		applicationContext.getBean(BotLogsService.class).reportMatchWin(member, match.getDivision(), match.getRoundNumber(), winTeam, loseTeam, parsedMatchScore);
		applicationContext.getBean(MatchChannelService.class).onMatchMarkWin(member, match, parsedMatchScore);
		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!win <score> -- marks match as won",
				"score can be `2-1` format or `1-2`, order does not matter, highest score will be counted to team whose captain executes command",
				"`ff` can be used if opponents surrender"
		);
	}

}
