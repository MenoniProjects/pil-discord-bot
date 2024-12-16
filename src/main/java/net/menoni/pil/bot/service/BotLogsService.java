package net.menoni.pil.bot.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BotLogsService {

	@Autowired
	private DiscordBot bot;

	private void log(String message, Object... args) {
		TextChannel channel = bot.getBotLogsChannel();
		if (channel != null) {
			log.info(DiscordFormattingUtil.formatMessageForContext(false, message, args));
			channel.sendMessage(DiscordFormattingUtil.formatMessageForContext(true, message, args)).queue();
		} else {
			log.error("[missing logs channel] {}", DiscordFormattingUtil.formatMessageForContext(false, message, args));
		}
	}

	public void reportMatchWin(Member member, Integer division, Integer roundNumber, JdbcTeam winTeam, JdbcTeam loseTeam, DiscordArgUtil.ParsedMatchScore parsedMatchScore) {
		this.log(
				"{} reported match (div: {}, round: {}, {} vs {}) as won by {} with result: {}",
				member, division, roundNumber, winTeam, loseTeam, winTeam, parsedMatchScore
		);
	}

	public void reportMatchForceWin(Member member, Integer divNumber, Integer roundNumber, JdbcTeam winTeam, JdbcTeam loseTeam, DiscordArgUtil.ParsedMatchScore parsedMatchScore) {
		this.log(
				"{} force-reported match (div: {}, round: {}, {} vs {}) as won by {} with result: {}",
				member, divNumber, roundNumber, winTeam, loseTeam, winTeam, parsedMatchScore
		);
	}

}
