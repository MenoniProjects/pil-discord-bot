package net.menoni.pil.bot.discord.command.impl;

import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.menoni.pil.bot.discord.command.CommandHandler;
import net.menoni.pil.bot.match.*;
import net.menoni.pil.bot.match.*;
import net.menoni.pil.bot.service.TeamService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

@Slf4j
public class ParseMatchDumpCommandHandler extends CommandHandler {

	@Autowired
	private TeamService teamService;

	public ParseMatchDumpCommandHandler() {
		super("parsematchdump");
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
					log.error("Error importing match-dump csv", throwable);
					return;
				}
				try {
					Match match = MatchDumpParser.parse(teamService, inputStream);

					MessageEmbed messageEmbed = MatchEmbed.top10(match);
					String matchTable = MatchTable.playersRanked(match, EnumSet.allOf(MatchTableColumn.class));
					byte[] matchCsv = MatchTable.playersRankedCsv(match, EnumSet.allOf(MatchTableColumn.class));

					MessageEditBuilder editBuilder = new MessageEditBuilder();
					editBuilder.setEmbeds(messageEmbed);
					editBuilder.setAttachments(
							AttachedFile.fromData(matchTable.getBytes(StandardCharsets.UTF_8), "match-table.txt"),
							AttachedFile.fromData(matchCsv, "match-table.csv")
					);
					editBuilder.setContent("");

					hook.editOriginal(editBuilder.build()).queue();
				} catch (IOException | CsvException e) {
					hook.editOriginal("Error parsing csv: " + e.getMessage()).queue();
					log.error("Error parsing csv", e);
				}
			}));
		});
	}
}
