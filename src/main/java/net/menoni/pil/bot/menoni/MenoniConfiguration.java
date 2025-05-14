package net.menoni.pil.bot.menoni;

import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.ws.client.MenoniWsClient;
import net.menoni.ws.discord.bot.MenoniDiscordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MenoniConfiguration {

	@Autowired
	private DiscordBot bot;
	@Autowired
	private MenoniWsClient ws;

	@Bean
	public MenoniDiscordService menoniDiscordService() {
		return new MenoniDiscordService(
				this.bot,
				this.ws,
				true
		);
	}

}
