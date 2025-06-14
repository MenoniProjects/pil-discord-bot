package net.menoni.pil.bot.discord.command.impl;

import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.menoni.jda.commons.discord.command.CommandHandler;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.match.*;
import net.menoni.pil.bot.service.TeamService;
import net.menoni.spring.commons.service.CsvService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ParseMatchDumpCommandHandler extends CommandHandler<DiscordBot> {

	@Autowired private TeamService teamService;
	@Autowired private CsvService csvService;

	public ParseMatchDumpCommandHandler(DiscordBot bot) {
		super(bot, "parsematchdump");
	}

	@Override
	public boolean allowCommand(Guild g, MessageChannelUnion channel, Member member, SlashCommandInteractionEvent event, boolean silent) {
		boolean allow = Objects.equals(channel.getId(), getBot().getConfig().getCmdChannelId());
		if (!allow && !silent) {
			replyPrivate(event, "Command is only allowed in command channel");
		}
		return allow;
	}

	@Override
	public SlashCommandData getSlashCommandData() {
		return Commands.slash("parsematchdump", "Parse Match dump from CSV")
				.setContexts(InteractionContextType.GUILD)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
				.addOption(OptionType.ATTACHMENT, "csv", "Match dump CSV file", true);
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
					List<String[]> lines = csvService.read(new InputStreamReader(inputStream));
					if (lines.isEmpty()) {
						hook.editOriginal("Failed to read empty CSV").queue();
						return;
					}
					lines.remove(0);

					String[] headers = new String[] {"Team", "Player", "Score"};
					List<Object[]> rLines = new ArrayList<>();
					Set<String> foundPlayerIds = new HashSet<>();

					Map<Long, JdbcTeam> teamsById = teamService.getAllTeams().stream().collect(Collectors.toMap(JdbcTeam::getId, t -> t));
					Map<String, JdbcTeamSignup> signupsByPlayerId = teamService.getAllSignups().stream().collect(Collectors.toMap(JdbcTeamSignup::getTrackmaniaUuid, s -> s));

					for (String[] line : lines) {
						String playerId = line[2];
						String playerName = line[3];

						if (foundPlayerIds.contains(playerId)) {
							continue;
						}
						foundPlayerIds.add(playerId);

						JdbcTeamSignup signup = signupsByPlayerId.get(playerId);
						if (signup == null) {
							continue;
						}
						JdbcTeam team = teamsById.get(signup.getTeamId());
						if (team == null) {
							continue;
						}

						rLines.add(new Object[] {
								team.getName(), playerName, ""
						});
					}

					byte[] bytes = csvService.create(headers, rLines);

					MessageEditBuilder b = new MessageEditBuilder();
					b.setContent("Converted CSV");
					b.setAttachments(AttachedFile.fromData(bytes, attachment.getFileName() + "-converted.csv"));

					hook.editOriginal(b.build()).queue();
				} catch (IOException | CsvException e) {
					log.error("Error parsing csv", e);
					hook.editOriginal("Error parsing csv: " + e.getMessage()).queue();
				}
			}));
		});
	}

	//@Override
	public void handle_old(Guild guild, MessageChannelUnion channel, Member member, SlashCommandInteractionEvent event) {
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
					List<String[]> lines = csvService.read(new InputStreamReader(inputStream));
					if (lines.isEmpty()) {
						hook.editOriginal("Failed to read empty CSV").queue();
						return;
					}
					lines.remove(0);
					Match match = MatchDumpParser.parse(teamService, lines);

					String matchTable = MatchTable.teamsRanked(match, EnumSet.allOf(MatchTableColumn.class));
					byte[] matchCsv = MatchTable.teamsRankedCsv(csvService, match, EnumSet.allOf(MatchTableColumn.class));
					MessageEditBuilder editBuilder = new MessageEditBuilder();
					editBuilder.setAttachments(
							AttachedFile.fromData(matchTable.getBytes(StandardCharsets.UTF_8), "match-table.txt"),
							AttachedFile.fromData(matchCsv, "match-table.csv")
					);
					if (match.getProblems().isEmpty()) {
						editBuilder.setContent("");
					} else {
						StringBuilder sb = new StringBuilder("Found " + match.getProblems().size() + " problems:\n");
						for (int i = 0; i < match.getProblems().size(); i++) {
							String problem = match.getProblems().get(i);
							if (problem.length() + sb.length() > 1800) {
								sb.append("-# and %d more".formatted(match.getProblems().size() - i));
								break;
							}
							sb.append("- ").append(problem).append("\n");
						}
						editBuilder.setContent(sb.toString());
					}

					hook.editOriginal(editBuilder.build()).queue();
				} catch (IOException | CsvException e) {
					log.error("Error parsing csv", e);
					hook.editOriginal("Error parsing csv: " + e.getMessage()).queue();
				}
			}));
		});
	}
}
