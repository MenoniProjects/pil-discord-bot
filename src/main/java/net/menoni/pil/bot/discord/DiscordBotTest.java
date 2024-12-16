package net.menoni.pil.bot.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.menoni.spring.commons.util.LoggerTextFormat;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.util.function.Consumer;
import java.util.function.Function;

public class DiscordBotTest extends DiscordBot {

	public DiscordBotTest(DiscordBotConfig config, AutowireCapableBeanFactory autowireCapableBeanFactory, String pilBotVersion) throws InterruptedException {
		super(config, autowireCapableBeanFactory, pilBotVersion);
	}

	@Override
	public void withGuild(Consumer<Guild> consumer) {

	}

	@Override
	public <T> T applyGuild(Function<Guild, T> function, T fallback) {
		return fallback;
	}


	@Override
	public void addEventListener(Object... objects) {

	}

	@Override
	public void logAdminChannel(String text, Object... args) {
		System.out.println("[admin channel] " + LoggerTextFormat.fillArgs(text, args));
	}
}
