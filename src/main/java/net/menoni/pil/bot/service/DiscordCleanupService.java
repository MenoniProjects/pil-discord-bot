package net.menoni.pil.bot.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.attribute.IGuildChannelContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.repository.MatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class DiscordCleanupService {

	@Autowired private ApplicationContext applicationContext;
	@Autowired private DiscordBot bot;
	@Autowired private MatchRepository matchRepository;

	@PostConstruct
	public void init() {
//		this.cleanupNonFinalsMatchChannels();

//		this.exit();
	}

	private void exit() {
		SpringApplication.exit(applicationContext, () -> 0);
		System.exit(0);
	}

	private void cleanupNonFinalsMatchChannels() {
		List<JdbcMatch> matches = matchRepository.all();
		matches = new ArrayList<>(matches.stream().filter(m -> m.getRoundNumber() < 20).sorted(Comparator.comparingLong(JdbcMatch::getId)).toList());

		List<TextChannel> channels = bot.applyGuild(IGuildChannelContainer::getTextChannels, List.of());

		for (JdbcMatch match : matches) {
			TextChannel matchChannel = channels.stream().filter(c -> Objects.equals(c.getId(), match.getMatchChannelId())).findAny().orElse(null);
			String matchChannelDisplay = "<not found>";
			if (matchChannel != null) {
				matchChannelDisplay = "#" + matchChannel.getName();
			}
			log.info("Match {} channel: {}", match.getId(), matchChannelDisplay);
			if (matchChannel != null) {
				JDAUtil.queueAndWait(matchChannel.delete());
			}
		}
	}

}
