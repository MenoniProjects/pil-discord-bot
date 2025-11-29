package net.menoni.pil.bot.discord;

import net.menoni.jda.commons.discord.chatcommand.ChatCommandListener;
import net.menoni.jda.commons.discord.command.DiscordBotCommandHandler;
import net.menoni.jda.commons.discord.command.impl.StandardChannelCommandHandler;
import net.menoni.pil.bot.discord.command.impl.ImportSignupsCommandHandler;
import net.menoni.pil.bot.discord.command.impl.FeedbackCommandHandler;
import net.menoni.pil.bot.discord.command.impl.ParseMatchDumpCommandHandler;
import net.menoni.pil.bot.discord.command.impl.WinCommandHandler;
import net.menoni.pil.bot.discord.command.chat.HelpCommand;
import net.menoni.pil.bot.script.ScriptExecutor;
import net.menoni.ws.discord.command.impl.LinkCommand;
import net.menoni.ws.discord.command.impl.TmNickCommand;
import net.menoni.ws.discord.command.impl.WouterCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class DiscordBotProvider {

    @Autowired private DiscordBotConfig config;
    @Autowired private ScriptExecutor scriptExecutor;

    @Bean
    @ConditionalOnProperty(value = "jda.bot.startup-test", havingValue = "false")
    public DiscordBot discordBot(ApplicationContext applicationContext) throws InterruptedException {
        boolean testMode = false;
        if (scriptExecutor.getScript() != null) {
            testMode = true;
        }
        return new DiscordBot(this.config, applicationContext, testMode);
    }

    @Bean
    @ConditionalOnProperty(value = "jda.bot.startup-test", havingValue = "true")
    public DiscordBot discordBotOffline(ApplicationContext applicationContext) throws InterruptedException {
        return new DiscordBot(this.config, applicationContext, true);
    }

    @Bean
    public DiscordBotCommandHandler<DiscordBot> discordBotCommandHandler(DiscordBot bot) {
        return new DiscordBotCommandHandler<>(bot, Set.of(
                // standard commands
                new LinkCommand<>(bot, null),
                new StandardChannelCommandHandler<>(bot),
                new TmNickCommand<>(bot, null),
                new WouterCommand<>(bot, null),
                // PIL commands
                new FeedbackCommandHandler(bot),
                new ImportSignupsCommandHandler(bot),
                new ParseMatchDumpCommandHandler(bot),
                new WinCommandHandler(bot)
        ));
    }

    @Bean
    public ChatCommandListener chatCommandListener(
            DiscordBot bot,
            ApplicationContext applicationContext
    ) {
        return new ChatCommandListener(
                bot,
                applicationContext,
                List.of(
//            			new EndRoundCommand(),
//            			new EventsExportCommand(),
//            			new ForceWinCommand(),
                        new HelpCommand()//,
//            			new MatchChannelCommand(),
//            			new MatchCommand(),
//            			new MissingPlayersCommand(),
//            			new RefreshTeamsCommand(),
//            			new TeamCommand(),
//            			new WinCommand()
                )
        );
    }

}
