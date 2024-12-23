package net.menoni.pil.bot.discord.listener.chatcmd.impl;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.utils.FileUpload;
import net.menoni.pil.bot.discord.listener.ChatCommandListener;
import net.menoni.pil.bot.discord.listener.chatcmd.ChatCommand;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.service.TeamService;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Slf4j
public class EventsExportCommand implements ChatCommand {
	@Override
	public Collection<String> names() {
		return List.of("eventsexport");
	}

	@Override
	public Collection<Permission> requiredPermissions() {
		return List.of(Permission.MANAGE_ROLES);
	}

	@Override
	public String shortHelpText() {
		return "Create CSV with all teams and members for trackmania.events";
	}

	@Override
	public boolean canExecute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member) {
		return ChatCommandListener.requireBotCmdChannel(applicationContext, channel);
	}

	@Override
	public boolean execute(ApplicationContext applicationContext, GuildMessageChannelUnion channel, Member member, Message message, String alias, String[] args) {
		TeamService teamService = applicationContext.getBean(TeamService.class);
		List<JdbcTeam> teams = teamService.getAllTeams();
		List<JdbcTeamSignup> signups = teamService.getAllSignups();

		ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(fileBytes);
		try (CSVWriter w = new CSVWriter(writer)) {
			List<String> errors = new ArrayList<>();
			w.writeNext(new String[] {"Team", "Player1", "Player2", "Player3", "Player4", "Player5", "Player6"});
			teams.forEach(team -> {
				String[] line = new String[7];
				line[0] = team.getName();

				List<JdbcTeamSignup> teamSignups = signups.stream().filter(s -> Objects.equals(s.getTeamId(), team.getId())).toList();
				if (teamSignups.size() < 3 || teamSignups.size() > 6) {
					errors.add("Team `%s` has %d members (expecting 3-6)".formatted(team.getName(), teamSignups.size()));
				} else {
					line[1] = teamSignups.get(0).getTrackmaniaUuid();
					line[2] = teamSignups.get(1).getTrackmaniaUuid();
					line[3] = teamSignups.get(2).getTrackmaniaUuid();
					line[4] = teamSignups.size() >= 4 ? teamSignups.get(3).getTrackmaniaUuid() : "";
					line[5] = teamSignups.size() >= 5 ? teamSignups.get(4).getTrackmaniaUuid() : "";
					line[6] = teamSignups.size() >= 6 ? teamSignups.get(5).getTrackmaniaUuid() : "";
					w.writeNext(line);
				}
			});

			writer.flush();

			channel
					.sendMessage("Created trackmania.events teams CSV")
					.addFiles(FileUpload.fromData(fileBytes.toByteArray(), "pil_nations_teams_%d.csv".formatted(System.currentTimeMillis())))
					.queue();
		} catch (IOException e) {
			log.error("Failed to write trackmania.events CSV", e);
			reply(channel, alias, "Failed to write trackmania.events CSV");
		}
		return true;
	}

	@Override
	public Collection<String> help() {
		return List.of(
				"!eventsexport -- Creates a teams csv that can be sent to the trackmania.events admin"
		);
	}
}
