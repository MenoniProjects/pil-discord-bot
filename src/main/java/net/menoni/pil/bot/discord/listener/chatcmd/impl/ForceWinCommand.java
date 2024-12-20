package net.menoni.pil.bot.discord.listener.chatcmd.impl;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.menoni.pil.bot.discord.listener.ChatCommandListener;
import net.menoni.pil.bot.discord.listener.chatcmd.ChatCommand;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.service.BotLogsService;
import net.menoni.pil.bot.service.MatchService;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.List;

public class ForceWinCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("forcewin");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.MANAGE_CHANNEL);
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		return ChatCommandListener.requireBotCmdChannelOrMatchChannel(applicationContext, channel);
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 5) {
			sendHelp(channel, "Not enough arguments");
			return true;
		}

		String divNumberArg = args[0];
		String roundNumberArg = args[1];
		String winTeamArg = args[2];
		String loseTeamArg = args[3];
		String scoreArg = args[4];

		boolean hasConfirmArg = false;
		if (args.length > 5) {
			if (args[5].equalsIgnoreCase("confirm")) {
				hasConfirmArg = true;
			}
		}

		int divNumber = -1;
		try {
			divNumber = Integer.parseInt(divNumberArg);
		} catch (NumberFormatException ex) {
			reply(channel, alias, "Invalid division number argument, first argument needs to be a (positive) division number");
			return true;
		}

		int roundNumber = -1;
		try {
			roundNumber = Integer.parseInt(roundNumberArg);
		} catch (NumberFormatException ex) {
			reply(channel, alias, "Invalid round number argument, second argument needs to be a (positive) round number");
			return true;
		}

		if (!DiscordArgUtil.isRole(winTeamArg)) {
			reply(channel, alias, "Third argument needs to be a team @");
			return true;
		}

		if (!DiscordArgUtil.isRole(loseTeamArg)) {
			reply(channel, alias, "Fourth argument needs to be a team @");
			return true;
		}

		String winRoleId = DiscordArgUtil.getRoleId(winTeamArg);
		String loseRoleId = DiscordArgUtil.getRoleId(loseTeamArg);

		TeamService teamService = applicationContext.getBean(TeamService.class);
		JdbcTeam winTeam = teamService.getTeamByRoleId(winRoleId);
		JdbcTeam loseTeam = teamService.getTeamByRoleId(loseRoleId);

		if (winTeam == null) {
			reply(channel, alias, "Team not found for win-team argument");
			return true;
		}
		if (loseTeam == null) {
			reply(channel, alias, "Team not found for lose-team argument");
			return true;
		}

		DiscordArgUtil.ParsedMatchScore parsedMatchScore = DiscordArgUtil.parseMatchScoreArg(scoreArg);
		if (parsedMatchScore == null) {
			sendHelp(channel, "Invalid score result argument");
			return true;
		}

		MatchService matchService = applicationContext.getBean(MatchService.class);
		JdbcMatch match = matchService.getMatchExact(divNumber, roundNumber, winTeam.getId(), loseTeam.getId());

		if (match == null) {
			if (!hasConfirmArg) {
				reply(channel, alias, "No match found for requested division, round and teams - use `confirm` argument to force create it and report match results");
				return true;
			}
			match = new JdbcMatch(null, divNumber, roundNumber, winTeam.getId(), loseTeam.getId(), "", winTeam.getId(), parsedMatchScore.winTeamScore(), parsedMatchScore.loseTeamScore());
		} else {
			match.setWinTeamId(winTeam.getId());
			match.setWinTeamScore(parsedMatchScore.winTeamScore());
			match.setLoseTeamScore(parsedMatchScore.loseTeamScore());
		}
		matchService.updateMatch(match);

		BotLogsService logsService = applicationContext.getBean(BotLogsService.class);
		logsService.reportMatchForceWin(member, divNumber, roundNumber, winTeam, loseTeam, parsedMatchScore);

		reply(channel, alias, "Marked match (div: %d, round: %d, vs: %s) as won by %s with result: %s".formatted(
				divNumber,
				roundNumber,
				DiscordFormattingUtil.roleAsString(loseTeam.getDiscordRoleId()),
				DiscordFormattingUtil.roleAsString(winTeam.getDiscordRoleId()),
				parsedMatchScore.print()
		));
		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!forcewin <division> <round-number> <@win-team> <@lose-team> <score> [confirm] -- forcibly reports a result",
				"score can be `2-1` format or `1-2`, order does not matter, highest score will be counted to win team argument",
				"`ff` can be used if losing team surrenders",
				"specify `confirm` as final argument if you want to create the match if the bot can't find it"
		);
	}
}
