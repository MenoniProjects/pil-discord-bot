package net.menoni.pil.bot.discord.command.chat;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.menoni.jda.commons.discord.chatcommand.ChatCommand;
import net.menoni.jda.commons.discord.chatcommand.ChatCommandListener;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
public class HelpCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("help");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of();
	}

	@Override
	public String shortHelpText() {
		return "List usable commands";
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, boolean silent) {
		// disallow in non-text channels
		if (channel.getType() != ChannelType.TEXT) {
			return false;
		}
		// not usable in public channels
		if (channel.getGuild().getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL)) {
			return false;
		}
		return true;
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		ChatCommandListener chatCommandListener = applicationContext.getBean(ChatCommandListener.class);
		List<ChatCommand> commands = chatCommandListener.getCommands();
		List<ChatCommand> executableCommands = commands.stream().filter(c -> chatCommandListener.checkExecutionPermissionsAndChannel(c, channel, member)).toList();

		if (args.length > 0) {
			String name = args[0];
			Optional<ChatCommand> allCmdOptional = commands.stream().filter(c -> c.names().contains(name.toLowerCase().trim())).findAny();
			Optional<ChatCommand> cmdOptional = executableCommands.stream().filter(c -> c.names().contains(name.toLowerCase().trim())).findAny();
			if (cmdOptional.isPresent()) {
				ChatCommand cmd = cmdOptional.get();
				cmd.sendHelp(channel, null);
			} else {
				if (allCmdOptional.isPresent()) {
					reply(channel, alias, "Command `" + name + "` can not be used by you in this channel");
				} else {
					reply(channel, alias, "Command `" + name + "` does not exist");
				}
			}
			return true;
		}

		if (executableCommands.isEmpty()) {
			reply(channel, "help", "No usable commands for you in this channel");
			return true;
		}

		StringBuilder sb = new StringBuilder("Usable commands (for you in this channel):\n");
		for (ChatCommand executableCommand : executableCommands) {
			String names = String.join("**, **", executableCommand.names());
			sb.append("- **").append(names).append("** - ").append(executableCommand.shortHelpText()).append("\n");
		}
		reply(channel, "help", sb.toString());

		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!help -- List all usable commands and show short help info",
				"!help [command] -- Show detailed help for another command"
		);
	}
}
