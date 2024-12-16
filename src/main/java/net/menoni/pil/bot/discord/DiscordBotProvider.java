package net.menoni.pil.bot.discord;

import net.menoni.pil.bot.discord.command.DiscordBotCommandHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class DiscordBotProvider {

    @Value("${pil.bot.version:'debugging'}")
    private String pilBotVersion;

    @Autowired
    private DiscordBotConfig config;

    private String getVersion() {
        if (this.pilBotVersion.equalsIgnoreCase("@project.version@")) {
            return "debugging";
        }
        return this.pilBotVersion;
    }

    @Bean
    @ConditionalOnProperty(value = "pil.bot.startup-test", havingValue = "false")
    public DiscordBot discordBot(AutowireCapableBeanFactory beanFactory) throws InterruptedException {
        return new DiscordBot(this.config, beanFactory, this.getVersion());
    }

    @Bean
    @ConditionalOnProperty(value = "pil.bot.startup-test", havingValue = "true")
    public DiscordBot discordBotOffline(AutowireCapableBeanFactory beanFactory) throws InterruptedException {
        return new DiscordBotTest(this.config, beanFactory, this.getVersion());
    }

    @Bean
    public DiscordBotCommandHandler discordBotCommandHandler(DiscordBot bot) {
        return new DiscordBotCommandHandler(bot);
    }

}
