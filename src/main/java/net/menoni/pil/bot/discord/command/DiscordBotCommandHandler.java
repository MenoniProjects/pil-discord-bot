package net.menoni.pil.bot.discord.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.menoni.jda.commons.util.DiscordTagUtil;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.command.impl.ImportSignupsCommandHandler;
import net.menoni.pil.bot.discord.command.impl.ParseMatchDumpCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiscordBotCommandHandler implements EventListener {

    private static final Logger logger = LoggerFactory.getLogger(DiscordBotCommandHandler.class);

    private static final Map<String, CommandData> COMMAND_DEFINITIONS = Stream.of(
            Commands.slash("importsignups", "Import signups from CSV")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                    .addOption(OptionType.ATTACHMENT, "csv", "Signups CSV file", true),
            Commands.slash("parsematchdump", "Parse Match dump from CSV")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                    .addOption(OptionType.ATTACHMENT, "csv", "Match dump CSV file", true)
    ).collect(Collectors.toMap(
            c -> c.getType().name() + ":" + c.getName(),
            c -> c
    ));

    private final DiscordBot bot;

    private final Map<String, CommandHandler> commandHandlers;

    public DiscordBotCommandHandler(DiscordBot bot) {
        logger.info("Initializing discord bot command handler");
        this.bot = bot;
        this.bot.addEventListener(this);
        this.commandHandlers = Stream.of(
                new ImportSignupsCommandHandler(),
                new ParseMatchDumpCommandHandler()
        ).collect(Collectors.toMap(CommandHandler::getCommandName, c -> c));
        this.commandHandlers.values().forEach(this.bot::autowire);
        this.ensureCommands();
    }

    @Override
    public void onEvent(GenericEvent event) {
        if (event instanceof SlashCommandInteractionEvent slashCommandEvent) {
            this.onSlashCommand(slashCommandEvent);
        } else if (event instanceof CommandAutoCompleteInteractionEvent autoCompleteEvent) {
            this.onOptionAutoCompleteEvent(autoCompleteEvent);
        }
    }

    private void ensureCommands() {
        this.bot.withGuild((g) -> {
            List<CommandData> updateCommands = new ArrayList<>(COMMAND_DEFINITIONS.values());
            if (!this.bot.isForceUpdateCommands()) {
                List<String> existingKeys = g.retrieveCommands().complete().stream().map((c) -> c.getType().name() + ":" + c.getName()).toList();
                updateCommands.clear();
                for (Map.Entry<String, CommandData> e : COMMAND_DEFINITIONS.entrySet()) {
                    if (!existingKeys.contains(e.getKey())) {
                        updateCommands.add(e.getValue());
                    }
                }
                logger.info("{} commands to update", updateCommands.size());
            } else {
                logger.info("Force-upserting all commands ({})", updateCommands.size());
            }

            if (!updateCommands.isEmpty()) {
                for (CommandData updateCommand : updateCommands) {
                    logger.info("Upserting command {}/{}", updateCommand.getType().name(), updateCommand.getName());
                    g.upsertCommand(updateCommand).queue();
                }
            }
        });
    }

    private void onSlashCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        MessageChannelUnion channel = event.getChannel();
        Member member = event.getMember();

        if (this.bot.getConfig().getLimitInteractionsToMember() != null) {
            if (!event.getUser().getId().equals(this.bot.getConfig().getLimitInteractionsToMember())) {
                event.reply("Bot is in development mode and limited to actions from " + DiscordTagUtil.getUserAsMention(this.bot.getConfig().getLimitInteractionsToMember())).setEphemeral(true).queue();
                return;
            }
        }

        if (this.commandHandlers.containsKey(event.getName())) {
            CommandHandler handler = this.commandHandlers.get(event.getName());
            if (handler.adminChannelOnly()) {
                if (!event.getChannel().getId().equals(this.bot.getConfig().getAdminChannelId())) {
                    event.reply("This command can only be used in the admin channel (" + DiscordTagUtil.getChannelAsMention(this.bot.getConfig().getAdminChannelId()) + ")").setEphemeral(true).queue();
                    return;
                }
            }
            handler.handle(guild, channel, member, event);
        } else {
            event.reply("Command not recognised").setEphemeral(true).queue();
        }
    }

    private void onOptionAutoCompleteEvent(CommandAutoCompleteInteractionEvent event) {
        Guild guild = event.getGuild();
        MessageChannelUnion channel = event.getChannel();
        Member member = event.getMember();

        if (this.bot.getConfig().getLimitInteractionsToMember() != null) {
            if (!event.getUser().getId().equals(this.bot.getConfig().getLimitInteractionsToMember())) {
                event.replyChoiceStrings().queue();
                return;
            }
        }

        if (this.commandHandlers.containsKey(event.getName())) {
            CommandHandler handler = this.commandHandlers.get(event.getName());
            if (handler.adminChannelOnly()) {
                if (!event.getChannel().getId().equals(this.bot.getConfig().getAdminChannelId())) {
                    event.replyChoiceStrings().queue();
                    return;
                }
            }
            List<String> choices = handler.autoCompleteOption(guild, channel, member, event, event.getFocusedOption());
            event.replyChoiceStrings(choices).queue();
        } else {
            event.replyChoiceStrings().queue();
        }
    }

}
