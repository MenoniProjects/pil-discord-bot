package net.menoni.pil.bot.discord.command.chat;

import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.menoni.jda.commons.discord.chatcommand.ChatCommand;
import net.menoni.pil.bot.config.FeatureFlags;
import net.menoni.pil.bot.discord.command.ChatCommandSupport;
import net.menoni.pil.bot.service.SignupSheetService;
import net.menoni.pil.bot.service.TeamService;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Slf4j
public class RefreshTeamsCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("refreshteams");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.MANAGE_ROLES);
	}

	@Override
	public String shortHelpText() {
		return "Refresh the teams message, or manually run a google sheet import";
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, boolean silent) {
		return ChatCommandSupport.requireBotCmdChannel(applicationContext, channel, silent);
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("sheet")) {
			if (FeatureFlags.DISABLE_REGISTRATION) {
				reply(channel, alias, "Registrations are closed");
				return true;
			}
			SignupSheetService signupSheetService = applicationContext.getBean(SignupSheetService.class);
			try {
				List<String> res = signupSheetService.runManualSignupSheetImport();
				reply(channel, alias, "Manually ran sheet re-import job:\n" + String.join("\n", res));
			} catch (IOException | CsvException e) {
				reply(channel, alias, "Failed to import signup sheet: " + e.getClass().getName() + ": " + e.getMessage());
				log.error("Manual sign-up sheet import failure", e);
			}
			return true;
		}

		applicationContext.getBean(TeamService.class).updateTeamsMessage();
		reply(channel, alias, "Refreshing teams message");
		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!refreshteams -- refreshes message in teams channel",
				"!refreshteams sheet -- downloads sheet and does re-import"
		);
	}
}
