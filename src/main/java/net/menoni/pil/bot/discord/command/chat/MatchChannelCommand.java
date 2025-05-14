package net.menoni.pil.bot.discord.command.chat;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.menoni.jda.commons.discord.chatcommand.ChatCommand;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.command.ChatCommandSupport;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.service.MatchChannelService;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import net.menoni.pil.bot.util.DiscordLinkUtil;
import net.menoni.pil.bot.util.RoundType;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class MatchChannelCommand implements ChatCommand {

	@Override
	public Collection<String> names() {
		return List.of("matchchannel", "mc");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.MANAGE_CHANNEL);
	}

	@Override
	public String shortHelpText() {
		return "Create a match channel for two teams in specified round & division";
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, boolean silent) {
		return ChatCommandSupport.requireBotCmdChannel(applicationContext, channel, silent);
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 2) {
			sendHelp(channel, null);
			return true;
		}

		String action = args[0];

		if (action.equalsIgnoreCase("create")) {
			return this.execute_create(applicationContext, channel, member, message, alias, args);
		} else if (action.equalsIgnoreCase("message")) {
			return this.execute_message(applicationContext, channel, member, message, alias, args);
		} else {
			sendHelp(channel, "invalid action");
		}
		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!mc create <division> <round-number> <@team-role-1> <@team-role-2> -- manually creates a match channel",
				"!mc message <message-id-or-link> -- set the message that will be pinned in every match channel"
		);
	}

	private boolean execute_create(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 5) {
			sendHelp(channel, null);
			return true;
		}

		String divNumArg = args[1];
		String roundNumArg = args[2];
		String role1Arg = args[3];
		String role2Arg = args[4];

		MatchChannelService matchChannelService = applicationContext.getBean(MatchChannelService.class);
		String pinMessageContent = matchChannelService.getPinMessageContent().getValue();
		if (pinMessageContent == null) {
			sendHelp(channel, "Pinned message content not set or expired - set it first (remaining command chain cancelled)");
			return false;
		}

		int divNum = -1;
		try {
			divNum = Integer.parseInt(divNumArg);
		} catch (NumberFormatException ex) {
			sendHelp(channel, "Second argument needs to be a division number (1 and up)");
			return true;
		}

		int roundNum = -1;
		try {
			roundNum = Integer.parseInt(roundNumArg);
		} catch (NumberFormatException ex) {
			sendHelp(channel, "Third argument needs to be a round number");
			return true;
		}

		if (!DiscordArgUtil.isRole(role1Arg)) {
			sendHelp(channel, "Fourth argument needs to be a team @ role");
			return true;
		}
		if (!DiscordArgUtil.isRole(role2Arg)) {
			sendHelp(channel, "Fifth argument needs to be a team @ role");
			return true;
		}

		String teamRole1Id = DiscordArgUtil.getRoleId(role1Arg);
		String teamRole2Id = DiscordArgUtil.getRoleId(role2Arg);

		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		Role teamRole1 = bot.getRoleById(teamRole1Id);
		Role teamRole2 = bot.getRoleById(teamRole2Id);

		if (teamRole1 == null) {
			sendHelp(channel, "First role does not exist");
			return true;
		}
		if (teamRole2 == null) {
			sendHelp(channel, "Second role does not exist");
			return true;
		}

		TeamService teamService = applicationContext.getBean(TeamService.class);
		JdbcTeam team1 = teamService.getTeamByRoleId(teamRole1.getId());
		JdbcTeam team2 = teamService.getTeamByRoleId(teamRole2.getId());

		if (team1 == null) {
			sendHelp(channel, "First team not found (for role %s)".formatted(DiscordFormattingUtil.roleAsString(teamRole1.getId())));
			return true;
		}
		if (team2 == null) {
			sendHelp(channel, "Second team not found (for role %s)".formatted(DiscordFormattingUtil.roleAsString(teamRole2.getId())));
			return true;
		}

		RoundType roundType = RoundType.forRoundNumber(roundNum);

		if (roundType == null) {
			sendHelp(channel, "Invalid round number");
			return true;
		}

		if (roundType.isRequireDivision()) {
			if (team1.getDivision() != divNum) {
				sendHelp(channel, "Requested first team %s is wrong division (%d), expected %d".formatted(
						DiscordFormattingUtil.roleAsString(teamRole1.getId()),
						team1.getDivision(),
						divNum
				));
				return true;
			}
			if (team2.getDivision() != divNum) {
				sendHelp(channel, "Requested first team %s is wrong division (%d), expected %d".formatted(
						DiscordFormattingUtil.roleAsString(teamRole1.getId()),
						team1.getDivision(),
						divNum
				));
				return true;
			}
		}

		int roundNumFinal = roundNum;
		int divNumFinal = divNum;
		RoundType roundTypeFinal = roundType;

		CompletableFuture<MatchChannelService.MatchChannelCreateResult> future = matchChannelService.createMatchChannel(divNum, roundNum, teamRole1, teamRole2, pinMessageContent);
		future.whenComplete((result, error) -> {
			if (error != null) {
				reply(channel, "matchchannel", "Failed to create match channel for %s vs %s (round d%d-r%d):\n```%s```".formatted(
						teamRole1.getName(), teamRole2.getName(), divNumFinal, roundNumFinal, error.getMessage()
				));
				log.error("Failed to create match channel for %s vs %s (round d%d-r%d)".formatted(teamRole1.getName(), teamRole2.getName(), divNumFinal, roundNumFinal), error);
				return;
			}
			String extra = result.additionalMessage();
			if (extra == null) {
				extra = "";
			} else {
				extra = "\n" + extra;
			}
			reply(channel, "matchchannel", "Created %s <#%s> for <@&%s> vs <@&%s> (round: d%d-r%d)%s".formatted(
					roundTypeFinal.name(),
					result.channel().getId(),
					teamRole1.getId(),
					teamRole2.getId(),
					divNumFinal,
					roundNumFinal,
					extra
			), m -> m.setAllowedMentions(List.of()));
		});
		try {
			future.join();
		} catch (Throwable t) {
			log.error("create-match-channel future-join error", t);
		}
		return true;
	}

	private boolean execute_message(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length < 2) {
			sendHelp(channel, null);
			return true;
		}

		DiscordBot bot = applicationContext.getBean(DiscordBot.class);
		MatchChannelService matchChannelService = applicationContext.getBean(MatchChannelService.class);

		String messageArg = args[1];
		String messageId;

		if (DiscordLinkUtil.isDiscordMessageLink(messageArg)) {
			String channelId = DiscordLinkUtil.getDiscordMessageLinkChannelId(messageArg);
			messageId = DiscordLinkUtil.getDiscordMessageLinkMessageId(messageArg);

			if (!bot.getConfig().getCmdChannelId().equalsIgnoreCase(channelId)) {
				reply(channel, "matchchannel", "Message needs to be from <#%s>".formatted(bot.getConfig().getCmdChannelId()));
				return true;
			}
		} else {
			messageId = messageArg;
		}

		if (messageId == null || messageId.isBlank()) {
			reply(channel, "matchchannel", "message argument not provided");
			return true;
		}

		TextChannel c = bot.getTextChannelById(bot.getConfig().getCmdChannelId());
		if (c == null) {
			reply(channel, "matchchannel", "Could not find configured cmd channel");
			return true;
		}

		c.retrieveMessageById(messageId).queue(
				m -> {
					if (m == null) {
						reply(channel, "matchchannel", "Could not find message");
						return;
					}

					matchChannelService.getPinMessageContent().setValue(m.getContentRaw());
					reply(channel, "matchchannel", "Set match-channel pin message content to:\n%s".formatted(m.getContentRaw()));
				},
				err -> {
					if (err instanceof ErrorResponseException discordError) {
						reply(channel, "matchchannel", "Error: " + errorMessageText(discordError.getErrorResponse()));
					} else if (err instanceof InsufficientPermissionException discordError) {
						reply(channel, "matchchannel", "Missing permission to read message: " + discordError.getPermission().name());
					} else {
						String errText = err != null ? err.toString() : "(null)";
						reply(channel, "matchchannel", "Unknown error retrieving message: " + errText);
					}
				}
		);
		return true;
	}

	private String errorMessageText(ErrorResponse type) {
		return switch (type) {
			case MISSING_ACCESS -> "Missing access to read messages in channel";
			case MISSING_PERMISSIONS -> "Missing permissions to read message history in channel";
			case UNKNOWN_MESSAGE -> "Message not found or not part of expected channel";
			case UNKNOWN_CHANNEL -> "Channel not found";
			default -> "unknown";
		};
	}

}
