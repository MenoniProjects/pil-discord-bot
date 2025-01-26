package net.menoni.pil.bot.discord.listener.chatcmd;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.menoni.jda.commons.util.JDAUtil;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.function.Function;

public interface ChatCommand {

	Collection<String> names();

	Collection<Permission> requiredPermissions();

	String shortHelpText();

	boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args);

	Collection<String> help();

	boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, boolean silent);

	default void reply(GuildMessageChannelUnion channel, String alias, String message) {
		reply(channel, alias, message, null);
	}

	default void reply(GuildMessageChannelUnion channel, String alias, String message, Function<MessageCreateAction, MessageCreateAction> modifier) {
		if (modifier == null) {
			modifier = m -> m;
		}
		try {
			JDAUtil.completableFutureQueue(modifier.apply(channel.sendMessage("**%s**: %s".formatted(alias, message)))).join();
		} catch (Throwable e) {
			String channelName = channel != null ? "#" + channel.getName() : "null-channel";
			System.err.println("failed to send message to %s:\n%s\n%s".formatted(
					channelName,
					message,
					e.getClass().getName() + ": " + e.getMessage()
			));
		}
	}

	default void sendHelp(GuildMessageChannelUnion channel, String errorText) {
		String name = names().stream().findFirst().orElse("?");
		if (errorText != null) {
			errorText = " (%s)".formatted(errorText);
		} else {
			errorText = "";
		}
		channel.sendMessage("**%s** help:%s\n%s".formatted(name, errorText, String.join("\n", help()))).queue();
	}

}
