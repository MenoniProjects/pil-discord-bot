package net.menoni.pil.bot.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcSystemMessage;
import net.menoni.pil.bot.jdbc.repository.SystemMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Slf4j
@Service
public class SystemMessageService {

	@Autowired
	private DiscordBot bot;
	@Autowired
	private SystemMessageRepository systemMessageRepository;

	public void setSystemMessage(String key, String channelId, Function<Message, MessageEditAction> updater, Function<TextChannel, MessageCreateAction> creator) {
		TextChannel textChannel = bot.getTextChannelById(channelId);
		if (textChannel == null) {
			log.warn("Channel does not exist for system-message '{}'", key);
			return;
		}
		if (!textChannel.canTalk()) {
			log.error("No permission in channel #{} for system-message '{}'", textChannel.getName(), key);
			return;
		}
		JdbcSystemMessage systemMessage = systemMessageRepository.getSystemMessage(key);
		if (systemMessage == null) {
			createNewMessage(key, channelId, textChannel, creator);
			return;
		}

		if (!systemMessage.getChannelId().equals(channelId)) {
			TextChannel oldChannel = bot.getTextChannelById(systemMessage.getChannelId());
			if (oldChannel != null) {
				oldChannel.deleteMessageById(systemMessage.getMessageId()).queue();
			}
			systemMessageRepository.deleteSystemMessage(systemMessage.getChannelId(), systemMessage.getMessageId());
			createNewMessage(key, channelId, textChannel, creator);
			return;
		}

		textChannel.retrieveMessageById(systemMessage.getMessageId()).queue((m) -> {
			MessageEditAction editAction = updater.apply(m);
			editAction.queue((updatedMessage) -> {
				systemMessage.setChannelId(textChannel.getId());
				systemMessage.setMessageId(updatedMessage.getId());
				systemMessageRepository.saveSystemMessage(systemMessage);
			}, (err) -> log.error("Failed to update system message: " + key, err));
		}, (err) -> {
			systemMessageRepository.deleteSystemMessage(systemMessage.getChannelId(), systemMessage.getMessageId());
			createNewMessage(key, channelId, textChannel, creator);
		});
	}

	private void createNewMessage(String key, String channelId, TextChannel textChannel, Function<TextChannel, MessageCreateAction> creator) {
		MessageCreateAction createAction = creator.apply(textChannel);
		createAction.queue((m) -> {
			systemMessageRepository.saveSystemMessage(new JdbcSystemMessage(null, key, channelId, m.getId()));
		}, (err) -> log.error("Failed creating system message: " + key, err));
	}

}
