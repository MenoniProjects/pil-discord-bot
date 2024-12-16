package net.menoni.pil.bot.discord.listener.chatcmd.impl;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.utils.FileUpload;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.listener.ChatCommandListener;
import net.menoni.pil.bot.discord.listener.chatcmd.ChatCommand;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.service.MatchChannelService;
import net.menoni.pil.bot.service.MatchService;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import net.menoni.pil.bot.util.JDAUtil;
import net.menoni.pil.bot.util.RoundType;
import org.springframework.context.ApplicationContext;

import java.util.*;

public class EndRoundCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("endround");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.MANAGE_ROLES);
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		return ChatCommandListener.requireBotCmdChannel(applicationContext, channel);
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 1) {
			sendHelp(channel, null);
			return true;
		}

		int round = -1;
		try {
			round = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			sendHelp(channel, "Failed to parse round-number argument");
			return true;
		}

		if (round <= 0) {
			reply(channel, "endround", "Round should be positive number");
			return true;
		}
		RoundType roundType = RoundType.forRoundNumber(round);
		if (roundType == null) {
			reply(channel, "endround", "Invalid round number");
			return true;
		}

		MatchService matchService = applicationContext.getBean(MatchService.class);
		TeamService teamService = applicationContext.getBean(TeamService.class);
		DiscordBot bot = applicationContext.getBean(DiscordBot.class);

		List<JdbcMatch> matches = new ArrayList<>(matchService.getMatchesForRound(round));
		if (matches.isEmpty()) {
			reply(channel, "endround", "No matches found for round " + round);
			return true;
		}

		List<JdbcTeam> teams = teamService.getAllTeams();

		List<JdbcMatch> unfinishedMatches = matches.stream().filter(m -> m.getWinTeamId() == null).toList();
		if (!unfinishedMatches.isEmpty()) {
			StringBuilder sb = new StringBuilder("**Round has unfinished matches** _(resolve these first)_\n");
			for (JdbcMatch m : unfinishedMatches) {
				String display = getMatchDisplay(teams, bot, m);
				sb.append("- ").append(display).append("\n");
			}
			reply(channel, "endround", sb.toString());
			return true;
		}

		reply(channel, "endround", "Archiving channels...");
		MatchChannelService matchChannelService = applicationContext.getBean(MatchChannelService.class);
		String resultText = matchChannelService.endRoundArchiveChannels(round, matches);
		reply(channel, "endround", "\n" + resultText);

		FileUpload csv = matchService.createEndRoundCsv(roundType, round, matches);
		JDAUtil.queueAndWait(channel.sendMessage("Round data CSV").addFiles(csv));

		if (roundType == RoundType.LEAGUE) {
			matches.sort(Comparator.comparingInt(JdbcMatch::getDivision).thenComparingLong(JdbcMatch::getId));

			StringBuilder sb = new StringBuilder("# Round %d\n".formatted(round));
			int div = 0;
			for (JdbcMatch match : matches) {
				if (match.getDivision() != div) {
					div = match.getDivision();
					sb.append("## Division %d:\n".formatted(div));
				}
				JdbcTeam winTeam = teams.stream().filter(t -> t.getId().equals(match.getWinTeamId())).findFirst().orElse(null);
				JdbcTeam loseTeam = teams.stream().filter(t -> t.getId().equals(Objects.equals(match.getFirstTeamId(), match.getWinTeamId()) ? match.getSecondTeamId() : match.getWinTeamId())).findFirst().orElse(null);

				String winTeamEmote = DiscordFormattingUtil.teamEmoteAsString(winTeam);
				String loseTeamEmote = DiscordFormattingUtil.teamEmoteAsString(loseTeam);
				String winTeamName = DiscordFormattingUtil.teamName(winTeam);
				String loseTeamName = DiscordFormattingUtil.teamName(loseTeam);

				sb.append("%s %s vs %s %s: %s".formatted(
						winTeamEmote,
						winTeamName,
						loseTeamEmote,
						loseTeamName,
						formatMatchScore(match)
				));
			}
			channel.sendMessage(sb.toString()).queue();
		}

		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!endround <round-number> -- end a round",
				"- hide channels for the teams & casters",
				"- move channels to an archive category",
				"- send csv with round results",
				"- send round result aggregate message for results channel"
		);
	}

	private String getMatchDisplay(List<JdbcTeam> teams, DiscordBot bot, JdbcMatch match) {
		if (match.getMatchChannelId() != null) {
			TextChannel channel = bot.getTextChannelById(match.getMatchChannelId());
			if (channel != null) {
				return "<#%s>".formatted(match.getMatchChannelId());
			}
		}
		String firstTeamName = teams.stream().filter(t -> Objects.equals(t.getId(), match.getFirstTeamId())).findAny().map(JdbcTeam::getName).orElse("unknown(?)");
		String secondTeamName = teams.stream().filter(t -> Objects.equals(t.getId(), match.getSecondTeamId())).findAny().map(JdbcTeam::getName).orElse("unknown(?)");
		return firstTeamName + " vs " + secondTeamName + " (no channel found)";
	}

	private String formatMatchScore(JdbcMatch match) {
		if (match.getWinTeamScore() == 0 && match.getLoseTeamScore() == 0) {
			return "2-0 / FF";
		}
		return match.getWinTeamScore() + "-" + match.getLoseTeamScore();
	}

}
