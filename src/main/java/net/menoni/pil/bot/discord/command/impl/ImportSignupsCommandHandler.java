package net.menoni.pil.bot.discord.command.impl;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.menoni.pil.bot.discord.command.CommandHandler;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.spring.commons.service.CsvService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ImportSignupsCommandHandler extends CommandHandler {

    @Autowired private TeamService teamService;
    @Autowired private CsvService csvService;

    public ImportSignupsCommandHandler() {
        super("importsignups");
    }

    @Override
    public boolean adminChannelOnly() {
        return true;
    }

    @Override
    public void handle(Guild guild, MessageChannelUnion channel, Member member, SlashCommandInteractionEvent event) {
        OptionMapping csvOption = event.getOption("csv");
        if (csvOption == null) {
            replyPrivate(event, "Missing CSV");
            return;
        }

        Message.Attachment attachment = csvOption.getAsAttachment();
        event.deferReply(false).queue(hook -> {
            attachment.getProxy().download().whenCompleteAsync(((inputStream, throwable) -> {
                if (throwable != null) {
                    hook.editOriginal("Error importing csv: " + throwable.getMessage()).queue();
                    log.error("Error importing signups csv", throwable);
                    return;
                }
                importSignupsCsv(hook, inputStream);
            }));
        });
    }

    private void importSignupsCsv(InteractionHook hook, InputStream stream) {
	    try {
            List<String[]> lines = csvService.read(new InputStreamReader(stream));
            if (lines.isEmpty()) {
                hook.editOriginal("Could not parse Empty CSV").queue();
                return;
            }
            lines.remove(0);

            List<String> resultLines = teamService.importCsv(lines);
            if (resultLines.isEmpty()) {
                resultLines = new ArrayList<>(List.of("No changes"));
            }

            hook.editOriginal("### Sign-ups imported\n" + String.join("\n", resultLines)).queue();
        } catch (Throwable e) {
            hook.editOriginal("Error importing signups csv: " + e.getMessage()).queue();
		    log.error("Error importing signups csv", e);
	    }
    }
}
