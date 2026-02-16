package net.menoni.pil.bot.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import net.menoni.pil.bot.util.RoundType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class BotLogsService {

	@Autowired
	private DiscordBot bot;

	private void log(String message, Object... args) {
		log.info(DiscordFormattingUtil.formatMessageForContext(false, message, args));
	}

	public void reportMatchWin(Member member, Integer division, Integer roundNumber, JdbcTeam team1, JdbcTeam team2, JdbcTeam winTeam, DiscordArgUtil.ParsedMatchScore parsedMatchScore) {
		RoundType type = RoundType.forRoundNumber(roundNumber);
		String typeName = "unknown";
		if (type != null) {
			roundNumber = type.adjustRoundNumber(roundNumber);
			typeName = type.getDisplayName();
		}
		this.log(
				"{} reported match (type: {}, div: {}, round: {}, {} vs {}) as won by {} with result: {}",
				member, typeName, division, roundNumber, team1, team2, winTeam, parsedMatchScore
		);
		this.logMatchResult(
				team1,
				team2,
				winTeam,
				parsedMatchScore,
				division,
				roundNumber,
				member,
				false
		);
	}

	public void reportMatchForceWin(Member member, Integer divNumber, Integer roundNumber, JdbcTeam team1, JdbcTeam team2, JdbcTeam winTeam, DiscordArgUtil.ParsedMatchScore parsedMatchScore) {
		RoundType type = RoundType.forRoundNumber(roundNumber);
		String typeName = "unknown";
		if (type != null) {
			roundNumber = type.adjustRoundNumber(roundNumber);
			typeName = type.getDisplayName();
		}
		this.log(
				"{} force-reported match (type: {}, div: {}, round: {}, {} vs {}) as won by {} with result: {}",
				member, typeName, divNumber, roundNumber, team1, team2, winTeam, parsedMatchScore
		);
		this.logMatchResult(
				team1,
				team2,
				winTeam,
				parsedMatchScore,
				divNumber,
				roundNumber,
				member,
				true
		);
	}

	private void logMatchResult(
			JdbcTeam team1,
			JdbcTeam team2,
			JdbcTeam winTeam,
			DiscordArgUtil.ParsedMatchScore score,
			Integer division,
			Integer roundNumber,
			Member submitter,
			boolean forceSubmit
	) {
		TextChannel channel = bot.getBotLogsChannel();
		if (channel == null) {
			log.warn("Missing log channel");
			return;
		}

		RoundType type = RoundType.forRoundNumber(roundNumber);
		String typeName = "unknown";
		if (type != null) {
			roundNumber = type.adjustRoundNumber(roundNumber);
			typeName = type.getDisplayName();
		}

		List<MessageTopLevelComponent> components = List.of(
				Container.of(
						TextDisplay.of("### %s vs %s\n**Winner:** %s\n**Score:** %s".formatted(
								team1.getName(),
								team2.getName(),
								winTeam.getName(),
								score.print()
						)),
						Separator.createDivider(Separator.Spacing.SMALL),
						TextDisplay.of("-# div %d ｜ round %d ｜ %s\n-# %s by %s".formatted(
								division,
								roundNumber,
								typeName,
								forceSubmit ? "admin-submitted" : "submitted",
								submitter.getAsMention()
						))
				).withAccentColor(0xA04FEC)
		);

		channel.sendMessageComponents(components)
				.useComponentsV2()
				.setAllowedMentions(List.of())
				.queue();
	}

}
