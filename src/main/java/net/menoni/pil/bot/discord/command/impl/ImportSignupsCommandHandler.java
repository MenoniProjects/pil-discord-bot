package net.menoni.pil.bot.discord.command.impl;

import com.opencsv.CSVReader;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.menoni.pil.bot.discord.command.CommandHandler;
import net.menoni.pil.bot.service.TeamService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ImportSignupsCommandHandler extends CommandHandler {

    @Autowired private TeamService teamService;

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
        CSVReader reader = new CSVReader(new InputStreamReader(stream));
	    try {
            reader.skip(1);
            List<String[]> lines = reader.readAll();

            List<SignupCSVLine> parsedLines = new ArrayList<>();
            for (String[] line : lines) {
                parsedLines.add(parseLine(line));
            }

            List<String> resultLines = teamService.importCsv(parsedLines);
            if (resultLines.isEmpty()) {
                resultLines = new ArrayList<>(List.of("No changes"));
            }

            hook.editOriginal("### Sign-ups imported\n" + String.join("\n", resultLines)).queue();
        } catch (Throwable e) {
            hook.editOriginal("Error importing signups csv: " + e.getMessage()).queue();
		    log.error("Error importing signups csv", e);
	    }
    }

    private static SignupCSVLine parseLine(String[] line) {
        return new SignupCSVLine(
                // 0 = timestamp
                line[1].trim(),
                line[2].trim(),
                line[3].trim(),
                line[4].trim(),
                line[5].trim(),
                line[6].trim(),
                line[7].trim(),
                line[8].trim(),
                line[9].trim(),
                line[10].trim(),
                line[11].trim(),
                line[12].trim(),
                line[13].trim(),
                line[14].trim(),
                line[15].trim(),
                line[16].trim(),
                line[17].trim(),
                line[18].trim(),
                line[19].trim(),
                line[20].trim(),
                line[21].trim()
        );
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class SignupCSVLine {
        private String teamName;
        private String teamColor;
        private String teamImageUrl;
        private String member1DiscordName;
        private String member1TrackmaniaName;
        private String member1TrackmaniaUserLink;
        private String member2DiscordName;
        private String member2TrackmaniaName;
        private String member2TrackmaniaUserLink;
        private String member3DiscordName;
        private String member3TrackmaniaName;
        private String member3TrackmaniaUserLink;
        private String member4DiscordName;
        private String member4TrackmaniaName;
        private String member4TrackmaniaUserLink;
        private String member5DiscordName;
        private String member5TrackmaniaName;
        private String member5TrackmaniaUserLink;
        private String member6DiscordName;
        private String member6TrackmaniaName;
        private String member6TrackmaniaUserLink;
    }
}
