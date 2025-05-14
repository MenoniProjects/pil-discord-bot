package net.menoni.pil.bot.discord.command.chat;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.utils.FileUpload;
import net.menoni.jda.commons.discord.chatcommand.ChatCommand;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.command.ChatCommandSupport;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.service.MatchService;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.pil.bot.util.RoundType;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

public class MatchCommand implements ChatCommand {

	@Override
	public Collection<String> names() {
		return List.of("match");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.MANAGE_ROLES);
	}

	@Override
	public String shortHelpText() {
		return "Match management";
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, boolean silent) {
		return ChatCommandSupport.requireBotCmdChannel(applicationContext, channel, silent);
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 2) {
			sendHelp(channel, null);
			return true;
		}

		if (args[0].equalsIgnoreCase("csv")) {
			this._exec_csv(applicationContext, channel, member, message, alias, args);
		} else if (args[0].equalsIgnoreCase("round")) {
			this._exec_round(applicationContext, channel, member, message, alias, args);
		} else if (args[0].equalsIgnoreCase("result")) {
			this._exec_result(applicationContext, channel, member, message, alias, args);
		} else {
			sendHelp(channel, "Invalid option '" + args[0].toLowerCase() + "'");
		}

		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!match -- show help",
				"!match round <round-number> -- show status of matches for specified round",
				"!match result <round-number> -- print results message in league results format",
				"!match csv <round-number> -- get round CSVs for divisions that did complete"
		);
	}

	private void _exec_csv(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 2) {
			sendHelp(channel, "Round number input required");
			return;
		}

		int roundNum = -1;
		try {
			roundNum = Integer.parseInt(args[1]);
		} catch (NumberFormatException ex) {
			reply(channel, alias, "Invalid round-num input - expected number");
			return;
		}

		RoundType roundType = RoundType.forRoundNumber(roundNum);
		if (roundType == null) {
			reply(channel, alias, "Invalid round number");
			return;
		}

		MatchService matchService = applicationContext.getBean(MatchService.class);

		List<JdbcMatch> matches = new ArrayList<>(matchService.getMatchesForRound(roundNum));
		if (matches.isEmpty()) {
			reply(channel, alias, "No matches found for round " + roundNum);
			return;
		}

		Set<Integer> divisionsWithUnfinishedMatches = new HashSet<>();
		List<JdbcMatch> unfinishedMatches = matches.stream().filter(m -> m.getWinTeamId() == null).toList();
		if (!unfinishedMatches.isEmpty()) {
			for (int i = 0; i < unfinishedMatches.size(); i++) {
				JdbcMatch m = unfinishedMatches.get(i);
				divisionsWithUnfinishedMatches.add(m.getDivision());
			}
		}

		matches.removeIf(m -> divisionsWithUnfinishedMatches.contains(m.getDivision()));

		if (matches.isEmpty()) {
			reply(channel, alias, "No divisions with all matches complete");
			return;
		}

		FileUpload csv = matchService.createEndRoundCsv(roundType, roundNum, matches);

		String missingDivisionsText = "";
		if (!divisionsWithUnfinishedMatches.isEmpty()) {
			missingDivisionsText = " (without divs: " + divisionsWithUnfinishedMatches.stream().map(d -> Integer.toString(d)).collect(Collectors.joining(", ")) + ")";
		}

		JDAUtil.queueAndWait(channel.sendMessage("Round data CSV" + missingDivisionsText).addFiles(csv));
	}

	private void _exec_result(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 2) {
			sendHelp(channel, "Round number input required");
			return;
		}

		int roundNum = -1;
		try {
			roundNum = Integer.parseInt(args[1]);
		} catch (NumberFormatException ex) {
			reply(channel, alias, "Invalid round-num input - expected number");
			return;
		}

		MatchService matchService = applicationContext.getBean(MatchService.class);
		TeamService teamService = applicationContext.getBean(TeamService.class);

		List<JdbcMatch> matches = matchService.getMatchesForRound(roundNum);
		List<JdbcTeam> teams = teamService.getAllTeams();

		channel.sendMessage(matchService.createLeagueMatchesResultsMessage(teams, matches, roundNum)).queue();
	}


	private void _exec_round(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 2) {
			sendHelp(channel, "Round number input required");
			return;
		}

		int roundNum = -1;
		try {
			roundNum = Integer.parseInt(args[1]);
		} catch (NumberFormatException ex) {
			reply(channel, alias, "Invalid round-num input - expected number");
			return;
		}

		MatchService matchService = applicationContext.getBean(MatchService.class);
		List<JdbcMatch> matches = matchService.getMatchesForRound(roundNum);

		if (matches == null || matches.isEmpty()) {
			reply(channel, alias, "No matches found for round " + roundNum);
			return;
		}

		StringBuilder sb = new StringBuilder();

		List<JdbcMatch> unfinished = matches.stream().filter(m -> m.getWinTeamId() == null).toList();

		if (!unfinished.isEmpty()) {
			sb.append("## %d/%d unfinished matches\n".formatted(unfinished.size(), matches.size()));
			for (int i = 0; i < unfinished.size(); i++) {
				JdbcMatch m = unfinished.get(i);
				sb.append("- <#%s>\n".formatted(m.getMatchChannelId()));
				if (i >= 10) {
					sb.append("-# and %d more".formatted(unfinished.size() - i));
					break;
				}
			}
		} else {
			sb.append("## All %d matches are finished\n".formatted(matches.size()));
		}

		reply(channel, alias, "Info for round " + roundNum + "\n" + sb);
	}

}
