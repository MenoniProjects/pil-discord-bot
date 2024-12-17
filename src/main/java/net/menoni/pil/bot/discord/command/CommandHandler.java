package net.menoni.pil.bot.discord.command;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.menoni.commons.util.LoggerTextFormat;
import net.menoni.pil.bot.discord.DiscordBot;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.function.Consumer;

@Getter
public abstract class CommandHandler {

    private final String commandName;
    @Autowired private DiscordBot bot;

    public CommandHandler(String commandName) {
        this.commandName = commandName;
    }

    public abstract boolean adminChannelOnly();

    public abstract void handle(
            Guild guild,
            MessageChannelUnion channel,
            Member member,
            SlashCommandInteractionEvent event
    );

    public List<String> autoCompleteOption(
            Guild guild,
            MessageChannelUnion channel,
            Member member,
            CommandAutoCompleteInteractionEvent event,
            AutoCompleteQuery focussedOption
    ) { return List.of(); }

    protected void replyPrivate(SlashCommandInteractionEvent event, String message, Object... args) {
        message = LoggerTextFormat.fillArgs(message, args);
        event.reply(message).setEphemeral(true).queue();
    }

    protected void replyPublic(SlashCommandInteractionEvent event, String message, Object... args) {
        message = LoggerTextFormat.fillArgs(message, args);
        event.reply(message).setEphemeral(false).queue();
    }

    protected void replyPublicCallback(SlashCommandInteractionEvent event, String message, Consumer<InteractionHook> callback, Object... args) {
        message = LoggerTextFormat.fillArgs(message, args);
        event.reply(message).setEphemeral(false).queue((h) -> {
            if (callback != null) {
                callback.accept(h);
            }
        });
    }

    protected void editLater(InteractionHook hook, String message, Object... args) {
        message = LoggerTextFormat.fillArgs(message, args);
        hook.editOriginal(message).queue();
    }

    protected void replyPublicLater(InteractionHook hook, String message, Object... args) {
        message = LoggerTextFormat.fillArgs(message, args);
        hook.getInteraction().getMessageChannel().sendMessage(message).queue();
    }

    protected void replyPublicLater(SlashCommandInteractionEvent event, String message, Object... args) {
        message = LoggerTextFormat.fillArgs(message, args);
        event.getInteraction().getMessageChannel().sendMessage(message).queue();
    }

    protected void logAdmin(SlashCommandInteractionEvent event, String message, Object... args) {
        message = LoggerTextFormat.fillArgs(message, args);
        this.bot.logAdminChannel(String.format("(/%s by %s) %s", event.getCommandString(), event.getMember().getUser().getName(), message));
    }

}
