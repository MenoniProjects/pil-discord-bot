package net.menoni.pil.bot.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import net.menoni.pil.bot.util.RoundType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BotLogsService {

	@Autowired
	private DiscordBot bot;

	private void log(String message, Object... args) {
		TextChannel channel = bot.getBotLogsChannel();
		if (channel != null) {
			log.info(DiscordFormattingUtil.formatMessageForContext(false, message, args));
			channel.sendMessage(DiscordFormattingUtil.formatMessageForContext(true, message, args)).setAllowedMentions(List.of()).queue();
		} else {
			log.error("[missing logs channel] {}", DiscordFormattingUtil.formatMessageForContext(false, message, args));
		}
	}

	public void reportMatchWin(Member member, Integer division, Integer roundNumber, JdbcTeam winTeam, JdbcTeam loseTeam, DiscordArgUtil.ParsedMatchScore parsedMatchScore) {
		RoundType type = RoundType.forRoundNumber(roundNumber);
		String typeName = "unknown";
		if (type != null) {
			roundNumber = type.adjustRoundNumber(roundNumber);
			typeName = type.getDisplayName();
		}
		this.log(
				"{} reported match (type: {}, div: {}, round: {}, {} vs {}) as won by {} with result: {}",
				member, typeName, division, roundNumber, winTeam, loseTeam, winTeam, parsedMatchScore
		);
	}

	public void reportMatchForceWin(Member member, Integer divNumber, Integer roundNumber, JdbcTeam winTeam, JdbcTeam loseTeam, DiscordArgUtil.ParsedMatchScore parsedMatchScore) {
		RoundType type = RoundType.forRoundNumber(roundNumber);
		String typeName = "unknown";
		if (type != null) {
			roundNumber = type.adjustRoundNumber(roundNumber);
			typeName = type.getDisplayName();
		}
		this.log(
				"{} force-reported match (type: {}, div: {}, round: {}, {} vs {}) as won by {} with result: {}",
				member, typeName, divNumber, roundNumber, winTeam, loseTeam, winTeam, parsedMatchScore
		);
	}

}
