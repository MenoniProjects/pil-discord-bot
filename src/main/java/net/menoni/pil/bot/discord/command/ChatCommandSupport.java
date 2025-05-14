package net.menoni.pil.bot.discord.command;

import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.service.MatchService;
import org.springframework.context.ApplicationContext;

import java.util.Objects;

public class ChatCommandSupport {

	public static boolean requireBotCmdChannel(ApplicationContext applicationContext, GuildMessageChannelUnion channel, boolean silent) {
		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		boolean allow = Objects.equals(channel.getId(), bot.getConfig().getCmdChannelId());
		if (!allow) {
			if (!silent) {
				channel.sendMessage("This command can only be executed in the bot-admin channel").queue();
			}
		}
		return allow;
	}

	public static boolean requireBotCmdChannelOrMatchChannel(ApplicationContext applicationContext, GuildMessageChannelUnion channel) {
		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		boolean allow = Objects.equals(channel.getId(), bot.getConfig().getCmdChannelId());
		if (allow) {
			return true;
		}

		MatchService matchService = applicationContext.getBean(MatchService.class);
		JdbcMatch matchForChannel = matchService.getMatchForChannel(channel.getId());
		if (matchForChannel != null) {
			return true;
		}

		channel.sendMessage("This command can only be executed in the bot-admin channel or in match channels").queue();
		return false;
	}

}
