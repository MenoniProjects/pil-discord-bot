package net.menoni.pil.bot.discord.listener;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.listener.chatcmd.ChatCommand;
import net.menoni.pil.bot.discord.listener.chatcmd.impl.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ChatCommandListener implements EventListener {

	private final Set<ChatCommand> commands = Set.of(
			new EndRoundCommand(),
			new EventsExportCommand(),
			new ForceWinCommand(),
			new MatchChannelCommand(),
			new MissingPlayersCommand(),
			new RefreshTeamsCommand(),
			new TeamCommand(),
			new WinCommand()
	);

	@Autowired
	private DiscordBot bot;

	@Autowired
	private ApplicationContext applicationContext;

	@PostConstruct
	public void init() {
		this.bot.addEventListener(this);
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof MessageReceivedEvent messageEvent) {
			handleMessageEvent(messageEvent);
		}
	}

	private void handleMessageEvent(MessageReceivedEvent event) {
		if (!validateEvent(event)) {
			return;
		}
		List<String> lines = Arrays.asList(event.getMessage().getContentRaw().split("\n"));
		GuildMessageChannelUnion channel = event.getGuildChannel();
		Member member = event.getMember();
		for (String line : lines) {
			boolean allowContinue = executeCommand(channel, member, event.getMessage(), line);
			if (!allowContinue) {
				break;
			}
		}
	}

	private boolean executeCommand(GuildMessageChannelUnion channel, Member member, Message message, String line) {
		if (line.trim().isBlank()) {
			return true;
		}
		String[] args = new String[0];
		String alias = line;
		if (line.contains(" ")) {
			args = line.split(" ");
			alias = args[0];
			args = Arrays.copyOfRange(args, 1, args.length);
		}
		if (!alias.startsWith("!")) {
			return true;
		}
		alias = alias.substring(1);
		if (alias.isBlank()) {
			return true;
		}

		ChatCommand command = getCommand(alias);
		if (command == null) {
			channel.sendMessage("Command `%s` does not exist".formatted(alias)).queue();
			return true;
		}
		Collection<Permission> perms = command.requiredPermissions();
		if (perms != null && !perms.isEmpty()) {
			if (!member.hasPermission(perms)) {
				channel.sendMessage("You do not have permission to execute `%s`".formatted(alias)).queue();
				return true;
			}
		}
		if (!command.canExecute(applicationContext, channel, member, message, alias, args)) {
			// canExecute is expected to give a no-access reason itself
			return true;
		}
		return command.execute(applicationContext, channel, member, message, alias, args);
	}

	private ChatCommand getCommand(String name) {
		for (ChatCommand command : commands) {
			if (command.names().stream().anyMatch(s -> s.equalsIgnoreCase(name))) {
				return command;
			}
		}
		return null;
	}

	private boolean validateEvent(MessageReceivedEvent event) {
		if (this.bot.getConfig().getGuildId() == null) {
			return false;
		}
		if (!event.isFromGuild()) {
			return false;
		}
		if (event.getAuthor().isBot() || event.getAuthor().isSystem()) {
			return false;
		}
		if (!this.bot.getConfig().getGuildId().equals(event.getGuildChannel().getGuild().getId())) {
			return false;
		}
		if (!event.getMessage().getContentRaw().startsWith("!")) {
			return false;
		}
		return true;
	}

	public static boolean requireBotCmdChannel(ApplicationContext applicationContext, GuildMessageChannelUnion channel) {
		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		boolean allow = Objects.equals(channel.getId(), bot.getConfig().getCmdChannelId());
		if (!allow) {
			channel.sendMessage("This command can only be executed in the bot-cmd channel").queue();
		}
		return allow;
	}

}
