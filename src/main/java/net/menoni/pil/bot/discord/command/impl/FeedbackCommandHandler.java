package net.menoni.pil.bot.discord.command.impl;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.menoni.jda.commons.discord.command.CommandHandler;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcFeedbackCategory;
import net.menoni.pil.bot.service.FeedbackChannelService;
import net.menoni.pil.bot.service.model.FeedbackChannelCreationResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FeedbackCommandHandler extends CommandHandler<DiscordBot>  {

	private static final String OPT_TYPE = "type";
	private static final String OPT_MAPPER = "mapper";

	@Autowired
	private FeedbackChannelService feedbackChannelService;

	public FeedbackCommandHandler(DiscordBot bot) {
		super(bot, "feedback");
	}

	@Override
	public SlashCommandData getSlashCommandData() {
		return Commands.slash(getCommandName(), "Create mapper + feedback channels")
				.setContexts(InteractionContextType.GUILD)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(List.of(Permission.ADMINISTRATOR)))
				.addOption(OptionType.STRING, OPT_TYPE, "Gear type", true, true)
				.addOption(OptionType.USER, OPT_MAPPER, "Mapper user", true);
	}

	@Override
	public boolean allowCommand(Guild g, MessageChannelUnion channel, Member member, SlashCommandInteractionEvent event, boolean silent) {
		return true;
	}

	@Override
	public void handle(Guild guild, MessageChannelUnion channel, Member member, SlashCommandInteractionEvent event) {
		CompletableFuture<InteractionHook> hookFuture = JDAUtil.completableFutureQueue(event.deferReply(false));

		String type = event.getOption(OPT_TYPE, OptionMapping::getAsString);
		Member mapper = event.getOption(OPT_MAPPER, OptionMapping::getAsMember);

		List<JdbcFeedbackCategory> feedbackCategories = this.feedbackChannelService.getFeedbackCategories();

		JdbcFeedbackCategory category = feedbackCategories.stream().filter(fc -> fc.getKey().equalsIgnoreCase(type)).findAny().orElse(null);

		if (category == null) {
			replyEditLater(hookFuture, "No category found for type `" + type + "`");
			return;
		}

		if (mapper == null) {
			replyEditLater(hookFuture, "Could not find mapper");
			return;
		}

		FeedbackChannelCreationResult result = feedbackChannelService.createMapChannels(category, mapper);
		if (result.success()) {
			replyEditLater(hookFuture, "Created channels %s and %s".formatted(
					result.mapperChannel().getAsMention(),
					result.feedbackChannel().getAsMention()
			));
		} else {
			String error = "no error provided";
			if (result.error() != null) {
				error = result.error();
			}
			replyEditLater(hookFuture, "Failed to create channels: %s".formatted(error));
		}
	}

	@Override
	public List<String> autoCompleteOption(Guild guild, MessageChannelUnion channel, Member member, CommandAutoCompleteInteractionEvent event, AutoCompleteQuery focussedOption) {
		if (focussedOption.getName().equals(OPT_TYPE)) {
			return this.feedbackChannelService.getFeedbackCategories()
					.stream()
					.map(JdbcFeedbackCategory::getKey)
					.toList();
		}
		return List.of();
	}
}
