package net.menoni.pil.bot.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcFeedbackCategory;
import net.menoni.pil.bot.jdbc.model.JdbcFeedbackChannel;
import net.menoni.pil.bot.jdbc.repository.FeedbackCategoryRepository;
import net.menoni.pil.bot.jdbc.repository.FeedbackChannelMemberRepository;
import net.menoni.pil.bot.jdbc.repository.FeedbackChannelRepository;
import net.menoni.pil.bot.service.model.FeedbackChannelCreationResult;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FeedbackChannelService implements EventListener {

	@Autowired
	private DiscordBot bot;

	@Autowired
	private FeedbackCategoryRepository feedbackCategoryRepository;
	@Autowired
	private FeedbackChannelRepository feedbackChannelRepository;
	@Autowired
	private FeedbackChannelMemberRepository feedbackChannelMemberRepository;

	@PostConstruct
	public void postConstruct() {
		this.bot.addEventListener(this);
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof MessageReceivedEvent messageEvent) {
			handleMessageEvent(messageEvent);
		}
	}

	public List<JdbcFeedbackCategory> getFeedbackCategories() {
		return this.feedbackCategoryRepository.findAll();
	}

	public FeedbackChannelCreationResult createMapChannels(JdbcFeedbackCategory category, Member mapper) {
		Guild g = bot.getGuild(bot.getGuildId());
		Category mapperCategory = g.getCategoryById(category.getMapperCategoryId());
		Category feedbackCategory = g.getCategoryById(category.getFeedbackCategoryId());
		Role feedbackRole = g.getRoleById(category.getFeedbackRoleId());

		if (mapperCategory == null) {
			return FeedbackChannelCreationResult.failure("Mapper category not found");
		}
		if (feedbackCategory == null) {
			return FeedbackChannelCreationResult.failure("Feedback category not found");
		}
		if (feedbackRole == null) {
			return FeedbackChannelCreationResult.failure("Feedback role not found");
		}

		JdbcFeedbackChannel feedbackChannelData = this.feedbackChannelRepository.create(mapper.getId());

		TextChannel mapperChannel = JDAUtil.queueAndWait(mapperCategory.createTextChannel("%s-%s-%d".formatted(
						category.getKey(),
						mapper.getEffectiveName(),
						feedbackChannelData.getId()
				))
				.addRolePermissionOverride(g.getIdLong(), null, List.of(Permission.VIEW_CHANNEL)) // not public
				.addPermissionOverride(g.getSelfMember(), List.of(Permission.VIEW_CHANNEL), List.of()) // add bot
				.addMemberPermissionOverride(mapper.getIdLong(), List.of(Permission.VIEW_CHANNEL), List.of())); // add mapper

		TextChannel feedbackChannel = JDAUtil.queueAndWait(feedbackCategory.createTextChannel("map-%d".formatted(
						feedbackChannelData.getId()
				))
				.addRolePermissionOverride(g.getIdLong(), null, List.of(Permission.VIEW_CHANNEL)) // not public
				.addPermissionOverride(g.getSelfMember(), List.of(Permission.VIEW_CHANNEL), List.of()) // add bot
				.addPermissionOverride(feedbackRole, List.of(Permission.VIEW_CHANNEL), List.of())); // add feedback role

		feedbackChannelData.setMapperChannelId(mapperChannel.getId());
		feedbackChannelData.setFeedbackChannelId(feedbackChannel.getId());
		this.feedbackChannelRepository.updateChannels(feedbackChannelData);

		return FeedbackChannelCreationResult.success(mapperChannel, feedbackChannel);
	}

	private void handleMessageEvent(MessageReceivedEvent event) {
		if (event.getMember() == null) {
			return;
		}

		if (event.getAuthor().isBot()) {
			return;
		}

		JdbcFeedbackChannel feedbackChannelData = this.feedbackChannelRepository.findByChannel(event.getChannel().getId());
		if (feedbackChannelData == null) {
			return;
		}

		if (feedbackChannelData.getMapperChannelId().equals(event.getChannel().getId())) {
			this.handleForwardFromMapperChannel(feedbackChannelData, event);
		} else if (feedbackChannelData.getFeedbackChannelId().equals(event.getChannel().getId())) {
			this.handleForwardFromFeedbackChannel(feedbackChannelData, event);
		}
	}

	private void handleForwardFromMapperChannel(JdbcFeedbackChannel feedbackChannelData, MessageReceivedEvent event) {
		String forwardName = "[unknown]";

		Role adminRole = bot.getRoleById(bot.getConfig().getAdminRoleId());
		if (feedbackChannelData.getMapperUserId().equals(event.getMember().getId())) {
			forwardName = "Mapper";
		} else if (event.getMember().getRoles().contains(adminRole)) {
			forwardName = event.getMember().getAsMention();
		}

		TextChannel forwardTo = bot.getTextChannelById(feedbackChannelData.getFeedbackChannelId());
		JDAUtil.queueAndWait(
				forwardMessageStuff(
						forwardTo.sendMessage("**From %s:**\n%s".formatted(forwardName, event.getMessage().getContentRaw())),
						event.getMessage()
				).setAllowedMentions(List.of())
		);
	}

	private void handleForwardFromFeedbackChannel(JdbcFeedbackChannel feedbackChannelData, MessageReceivedEvent event) {
		String forwardName = "[unknown]";

		Role adminRole = bot.getRoleById(bot.getConfig().getAdminRoleId());
		if (event.getMember().getRoles().contains(adminRole)) {
			forwardName = event.getMember().getAsMention();
		} else if (feedbackChannelData.getMapperUserId().equals(event.getMember().getId())) {
			int num = this.feedbackChannelMemberRepository.getFeedbackUserNumber(feedbackChannelData.getId(), event.getMember().getId());
			forwardName = "Tester-%d".formatted(num);
		}

		TextChannel forwardTo = bot.getTextChannelById(feedbackChannelData.getMapperChannelId());
		JDAUtil.queueAndWait(
				forwardMessageStuff(
						forwardTo.sendMessage("**From %s:**\n%s".formatted(forwardName, event.getMessage().getContentRaw())),
						event.getMessage()
				).setAllowedMentions(List.of())
		);
	}

	private MessageCreateAction forwardMessageStuff(MessageCreateAction action, Message source) {
		if (source.getStickers() != null && !source.getStickers().isEmpty()) {
			action = action.setContent(action.getContent() + "\n-# [some ugly-ahh sticker]");
		}
		if (source.getAttachments() != null && !source.getAttachments().isEmpty()) {
			List<Message.Attachment> resAttachments = new ArrayList<>();
			boolean hasMapFiles = false;
			for (Message.Attachment attachment : source.getAttachments()) {
				if (attachment.getFileName().endsWith(".Map.Gbx")) {
					hasMapFiles = true;
					continue;
				}
				resAttachments.add(attachment);
			}
			if (hasMapFiles) {
				action = action.setContent(action.getContent() + "\n-# contains map file upload");
			}
			if (!resAttachments.isEmpty()) {
				List<FileUpload> fileUploads = new ArrayList<>();
				for (Message.Attachment attachment : resAttachments) {
					try {
						InputStream inputStream = attachment.getProxy().download().join();
						fileUploads.add(FileUpload.fromData(inputStream, attachment.getFileName()));
					} catch (Throwable t) {
						log.error("Failed to download attachment for re-upload", t);
					}
				}
				action = action.addFiles(fileUploads);
			}
		}
		return action;
	}

}
