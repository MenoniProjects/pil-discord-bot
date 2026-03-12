package net.menoni.pil.bot.discord.command.chat;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.menoni.jda.commons.discord.chatcommand.ChatCommand;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.jdbc.model.JdbcFeedbackChannel;
import net.menoni.pil.bot.jdbc.model.JdbcMatch;
import net.menoni.pil.bot.jdbc.repository.FeedbackChannelRepository;
import net.menoni.pil.bot.service.MatchChannelService;
import net.menoni.pil.bot.service.MatchService;
import net.menoni.ws.discord.config.BaseConstants;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Slf4j
public class DebugCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("debug");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.ADMINISTRATOR);
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, boolean silent) {
		return Objects.equals(BaseConstants.USER_ID_DEV, member.getId());
	}

	@Override
	public String shortHelpText() {
		return "Debug stuff";
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		if (args.length == 0) {
			sendHelp(channel, null);
			return true;
		}

		try {
			if (args[0].equalsIgnoreCase("channelnames")) {
				this._exec_channelNames(applicationContext, channel, member, message, alias, args);
			}
//			if (args[0].equalsIgnoreCase("s3")) {
//				this._exec_s3(applicationContext, channel, member, message, alias, args);
//			} else if (args[0].equalsIgnoreCase("cleanfeedback")) {
//				this._exec_cleanfeedback(applicationContext, channel, member, message, alias, args);
//			}
			else {
				reply(channel, alias, "Invalid sub-command: `%s`".formatted(args[0]));
			}
		} catch (Exception ex) {
			reply(channel, alias, "Error executing command:\n```%s```".formatted(ex.getMessage()));
		}

		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!debug -- show this help",
				"!debug channelnames -- update all channel names"
//				"!debug s3 -- do s2 -> s3 ding",
//				"!debug cleanfeedback - clean feedback"
		);
	}

	private void _exec_channelNames(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) throws Exception {
		MatchService matchService = applicationContext.getBean(MatchService.class);
		MatchChannelService matchChannelService = applicationContext.getBean(MatchChannelService.class);

		List<JdbcMatch> matches = matchService.findAll();
		int updated = 0;
		for (JdbcMatch match : matches) {
			TextChannel c = channel.getGuild().getTextChannelById(match.getMatchChannelId());
			if (c == null) {
				continue;
			}
			try {
				if (matchChannelService.updateMatchChannelName(channel.getGuild(), match)) {
					updated++;
				}
			} catch (Exception e) {
				log.error("Failed to update match channel name", e);
			}
		}
		reply(channel, alias, "Completed (updated %d)".formatted(updated));
	}

	private void _exec_cleanfeedback(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) throws Exception {
		FeedbackChannelRepository repo = applicationContext.getBean(FeedbackChannelRepository.class);
		List<JdbcFeedbackChannel> all = repo.findAll();
		List<String> res = new ArrayList<>(List.of("Removing feedback channels"));
		for (JdbcFeedbackChannel c : all) {
			if (c.getSelected() == null || c.getSelected()) {
				res.add("Skipping <#%s> / <#%s>".formatted(c.getFeedbackChannelId(), c.getMapperChannelId()));
				continue;
			}
			String name = "channel-%d".formatted(c.getId());
			TextChannel feedbackChannel = channel.getGuild().getTextChannelById(c.getFeedbackChannelId());
			TextChannel mapperChannel = channel.getGuild().getTextChannelById(c.getMapperChannelId());
			if (feedbackChannel != null) {
				JDAUtil.queueAndWait(feedbackChannel.delete());
			}
			if (mapperChannel != null) {
				name = mapperChannel.getName();
				JDAUtil.queueAndWait(mapperChannel.delete());
			}
			res.add("Removing feedback channel: %s".formatted(name));
		}
		replyLong(channel, alias, res);
	}

	private void _exec_s3(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) throws Exception {
		String s2CategoryId = "1304238693032136704";
		String s3CategoryId = "1468706838072787134";

		Category s2Cat = channel.getGuild().getCategoryById(s2CategoryId);
		Category s3Cat = channel.getGuild().getCategoryById(s3CategoryId);

		if (s2Cat == null || s3Cat == null) {
			reply(channel, alias, "Missing s2 or s3 category");
			return;
		}

		if (!s3Cat.getChannels().isEmpty()) {
			reply(channel, alias, "s3 category is not empty");
			return;
		}

		List<String> res = new ArrayList<>();
		res.add("Creating s3 section");

		for (GuildChannel s2Channel : s2Cat.getChannels()) {
			if (s2Channel.getType() != ChannelType.TEXT) {
				reply(channel, alias, "Skipping non-text channel: <#%s>".formatted(s2Channel.getId()));
				continue;
			}

			String name = s2Channel.getName().replace('2', '3');

			ChannelAction<TextChannel> chanCreator = s3Cat.createTextChannel(name);

			List<PermissionOverride> permissionOverrides = s2Channel.getPermissionContainer().getPermissionOverrides();
			for (PermissionOverride permissionOverride : permissionOverrides) {
				if (!permissionOverride.isRoleOverride() || permissionOverride.getPermissionHolder() == null) {
					log.info("Skipping non-role override {} for channel {}", permissionOverride.getId(), s2Channel.getName());
					continue;
				}
				chanCreator.addPermissionOverride(
						permissionOverride.getPermissionHolder(),
						permissionOverride.getAllowed(),
						permissionOverride.getDenied()
				);
			}

			TextChannel chan = JDAUtil.queueAndWait(chanCreator);
			res.add("- created <#%s>".formatted(chan.getId()));
		}

		replyLong(channel, alias, res);
	}

}
