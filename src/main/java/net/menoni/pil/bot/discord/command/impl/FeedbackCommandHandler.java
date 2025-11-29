package net.menoni.pil.bot.discord.command.impl;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.menoni.jda.commons.discord.command.CommandHandler;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcFeedbackCategory;
import net.menoni.pil.bot.service.FeedbackChannelService;
import net.menoni.pil.bot.service.model.FeedbackChannelCreationResult;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class FeedbackCommandHandler extends CommandHandler<DiscordBot> implements EventListener {

	private static final String OPT_TYPE = "type";

	private static final String MODAL_ID = "mdl:mappers:";
	private static final String MODAL_MAPPER_SELECT = "mdl:mappers:member";

	@Autowired
	private FeedbackChannelService feedbackChannelService;

	public FeedbackCommandHandler(DiscordBot bot) {
		super(bot, "feedback");
		bot.addEventListener(this);
	}

	@Override
	public SlashCommandData getSlashCommandData() {
		return Commands.slash(getCommandName(), "Create mapper + feedback channels")
				.setContexts(InteractionContextType.GUILD)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(List.of(Permission.ADMINISTRATOR)))
				.addOption(OptionType.STRING, OPT_TYPE, "Gear type", true, true);
//				.addOption(OptionType.USER, OPT_MAPPER, "Mapper user", true);
	}

	@Override
	public boolean allowCommand(Guild g, MessageChannelUnion channel, Member member, SlashCommandInteractionEvent event, boolean silent) {
		return true;
	}

	@Override
	public void handle(Guild guild, MessageChannelUnion channel, Member member, SlashCommandInteractionEvent event) {
		String type = event.getOption(OPT_TYPE, OptionMapping::getAsString);

		List<JdbcFeedbackCategory> feedbackCategories = this.feedbackChannelService.getFeedbackCategories();

		JdbcFeedbackCategory category = feedbackCategories.stream().filter(fc -> fc.getKey().equalsIgnoreCase(type)).findAny().orElse(null);

		if (category == null) {
			replyPublic(event, "No category found for type `" + type + "`");
			return;
		}

		JDAUtil.queueAndWait(event.replyModal(
				Modal.create(MODAL_ID + category.getKey(), "Mappers")
						.addComponents(Label.of("Members",
								EntitySelectMenu.create(MODAL_MAPPER_SELECT, EntitySelectMenu.SelectTarget.USER)
										.setRequiredRange(1, 10)
										.build()
						))
						.build()
		));
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

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (!(event instanceof ModalInteractionEvent e)) {
			return;
		}

		if (!e.getCustomId().startsWith(MODAL_ID)) {
			return;
		}

		String type = e.getCustomId().substring(MODAL_ID.length());
		List<JdbcFeedbackCategory> feedbackCategories = this.feedbackChannelService.getFeedbackCategories();
		JdbcFeedbackCategory category = feedbackCategories.stream().filter(fc -> fc.getKey().equalsIgnoreCase(type)).findAny().orElse(null);

		if (category == null) {
			e.reply("Magic caused category to become null").queue();
			return;
		}

		Mentions mentionsValue = null;

		for (ModalMapping modalEntry : e.getValues()) {
			if (!modalEntry.getCustomId().equals(MODAL_MAPPER_SELECT)) {
				continue;
			}
			mentionsValue = modalEntry.getAsMentions();
			break;
		}

		if (mentionsValue == null || mentionsValue.getMembers().isEmpty()) {
			e.reply("No members found in response").setEphemeral(true).queue();
			return;
		}

		List<Member> mapperMembers = mentionsValue.getMembers();

		FeedbackChannelCreationResult result = feedbackChannelService.createMapChannels(category, mapperMembers);
		if (result.success()) {
			JDAUtil.queueAndWait(e.reply("Created channels %s and %s".formatted(
					result.mapperChannel().getAsMention(),
					result.feedbackChannel().getAsMention()
			)));
		} else {
			String error = "no error provided";
			if (result.error() != null) {
				error = result.error();
			}
			JDAUtil.queueAndWait(e.reply("Failed to create channels: %s".formatted(error)));
		}
	}

}
