package net.menoni.pil.bot.menoni;

import jakarta.annotation.PostConstruct;
import net.menoni.irc.MenoniEventClientIRC;
import net.menoni.jda.commons.discord.emote.Emotable;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.emote.CustomEmote;
import net.menoni.ws.client.MenoniWsClient;
import net.menoni.ws.discord.bot.MenoniDiscordBanService;
import net.menoni.ws.discord.bot.MenoniDiscordService;
import net.menoni.ws.discord.service.EchelonEmoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MenoniConfiguration {

	@Autowired
	private DiscordBot bot;
	@Autowired
	private MenoniWsClient ws;
	@Autowired
	private MenoniEventClientIRC irc;

	@Autowired
	private EchelonEmoteService echelonEmoteService;

	@PostConstruct
	public void init() {
		echelonEmoteService.setEchelonEmotes(new Emotable[]{
				CustomEmote.ECHELON_0,
				CustomEmote.ECHELON_1,
				CustomEmote.ECHELON_2,
				CustomEmote.ECHELON_3,
				CustomEmote.ECHELON_4,
				CustomEmote.ECHELON_5,
				CustomEmote.ECHELON_6,
				CustomEmote.ECHELON_7,
				CustomEmote.ECHELON_8,
				CustomEmote.ECHELON_9,
		});
	}

	@Bean
	public MenoniDiscordService menoniDiscordService() {
		return new MenoniDiscordService(
				this.bot,
				this.ws,
				true
		);
	}

	@Bean
	public MenoniDiscordBanService menoniDiscordBanService() {
		return new MenoniDiscordBanService(
				this.bot,
				this.ws,
				this.irc
		);
	}

}
