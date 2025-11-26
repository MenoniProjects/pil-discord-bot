package net.menoni.pil.bot.service.model;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public record FeedbackChannelCreationResult(
		boolean success,
		String error,
		TextChannel mapperChannel,
		TextChannel feedbackChannel
) {

	public static FeedbackChannelCreationResult success(TextChannel mapperChannel, TextChannel feedbackChannel) {
		return new FeedbackChannelCreationResult(true, null, mapperChannel, feedbackChannel);
	}

	public static FeedbackChannelCreationResult failure(String error) {
		return new FeedbackChannelCreationResult(false, error, null, null);
	}


}
