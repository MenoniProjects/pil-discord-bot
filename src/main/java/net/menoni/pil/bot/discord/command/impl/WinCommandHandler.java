package net.menoni.pil.bot.discord.command.impl;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.command.CommandHandler;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.service.BotLogsService;
import net.menoni.pil.bot.service.MatchService;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;

@Slf4j
public class WinCommandHandler extends CommandHandler {

	@Autowired private TeamService teamService;
	@Autowired private MatchService matchService;
	@Autowired private BotLogsService botLogsService;

	public WinCommandHandler() {
		super("win");
	}

	@Override
	public boolean adminChannelOnly() {
		return false;
	}

	@Override
	public void handle(Guild guild, MessageChannelUnion channel, Member member, SlashCommandInteractionEvent event) {
		String score = getOption(event);

		JdbcMatch match = matchService.getMatchForChannel(channel.getId());
		if (match == null) {
			replyPrivate(event, "Can only use this command in a match channel");
			return;
		}
		// mark win
		JdbcTeamSignup teamMemberSignup = teamService.getSignupForMember(member);
		if (teamMemberSignup == null) {
			replyPrivate(event, "Could not find your team membership");
			return;
		}
		if (match.getWinTeamId() != null) {
			replyPrivate(event, "This match has already been reported as complete - ask staff to override results if needed.");
			return;
		}

		if (score == null) {
			replyPrivate(event, "Invalid score result");
			return;
		}

		DiscordArgUtil.ParsedMatchScore parsedMatchScore = DiscordArgUtil.parseMatchScoreArg(score);
		if (parsedMatchScore == null) {
			replyPrivate(event, "Invalid score result argument");
			return;
		}

		match.setWinTeamId(teamMemberSignup.getTeamId());
		match.setWinTeamScore(parsedMatchScore.winTeamScore());
		match.setLoseTeamScore(parsedMatchScore.loseTeamScore());
		matchService.updateMatch(match);

		JdbcTeam winTeam = teamService.getTeamById(teamMemberSignup.getTeamId());
		JdbcTeam loseTeam = teamService.getTeamById(Objects.equals(teamMemberSignup.getTeamId(), match.getFirstTeamId()) ? match.getSecondTeamId() : match.getFirstTeamId());

		replyPublic(event, "Marked match as won by %s with result: %s".formatted(
				DiscordFormattingUtil.roleAsString(winTeam.getDiscordRoleId()),
				parsedMatchScore.print()
		));

		botLogsService.reportMatchWin(member, match.getDivision(), match.getRoundNumber(), winTeam, loseTeam, parsedMatchScore);
	}

	@Override
	public List<String> autoCompleteOption(Guild guild, MessageChannelUnion channel, Member member, CommandAutoCompleteInteractionEvent event, AutoCompleteQuery focussedOption) {
		if (focussedOption.getName().equals("score")) {
			return List.of(
					"2-0",
					"2-1",
					"ff"
			);
		}
		return List.of();
	}

	private String getOption(SlashCommandInteractionEvent event) {
		OptionMapping optionOption = event.getOption("score");
		if (optionOption == null) {
			return null;
		}
		return optionOption.getAsString();
	}
}
