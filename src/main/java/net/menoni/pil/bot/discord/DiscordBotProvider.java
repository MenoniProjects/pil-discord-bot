package net.menoni.pil.bot.discord;

import net.menoni.pil.bot.discord.command.DiscordBotCommandHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class DiscordBotProvider {

    @Autowired
    private DiscordBotConfig config;

    @Bean
    @ConditionalOnProperty(value = "jda.bot.startup-test", havingValue = "false")
    public DiscordBot discordBot(AutowireCapableBeanFactory beanFactory) throws InterruptedException {
        return new DiscordBot(this.config, beanFactory, false);
    }

    @Bean
    @ConditionalOnProperty(value = "jda.bot.startup-test", havingValue = "true")
    public DiscordBot discordBotOffline(AutowireCapableBeanFactory beanFactory) throws InterruptedException {
        return new DiscordBotTest(this.config, beanFactory);
    }

    @Bean
    public DiscordBotCommandHandler discordBotCommandHandler(DiscordBot bot) {
        return new DiscordBotCommandHandler(bot);
    }

}
