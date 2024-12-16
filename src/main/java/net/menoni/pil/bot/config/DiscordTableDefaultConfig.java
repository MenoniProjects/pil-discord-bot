package net.menoni.pil.bot.config;

import jakarta.annotation.PostConstruct;
import net.menoni.spring.commons.util.DiscordTableSettings;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordTableDefaultConfig {

	@PostConstruct
	public void setTableConfig() {
		DiscordTableSettings.DEFAULT = DiscordTableSettings.builder()
				.centerHeaders(true)
				.build();
	}

}
