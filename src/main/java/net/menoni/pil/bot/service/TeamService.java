package net.menoni.pil.bot.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.discord.command.impl.ImportSignupsCommandHandler;
import net.menoni.pil.bot.jdbc.model.JdbcMember;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.jdbc.repository.TeamRepository;
import net.menoni.pil.bot.jdbc.repository.TeamSignupRepository;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import net.menoni.pil.bot.util.DiscordRoleUtil;
import net.menoni.pil.bot.util.Scheduling;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TeamService {

	@Autowired
	private DiscordBot bot;
	@Autowired
	private MemberService memberService;
	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private TeamSignupRepository teamSignupRepository;
	@Autowired
	private SystemMessageService systemMessageService;

	private ScheduledFuture<?> updateTeamsMessageTask;

	public JdbcTeam ensureTeam(String name, String color, String imageUrl) throws Exception {
		CompletableFuture<JdbcTeam> teamFuture = teamRepository.ensureTeam(bot, name, color, imageUrl);
		return teamFuture.get(10L, TimeUnit.SECONDS);
	}

	public JdbcTeam getPlayerTeam(String playerTrackmaniaId) {
		JdbcTeamSignup signup = this.teamSignupRepository.getSignupForTrackmaniaId(playerTrackmaniaId);
		if (signup == null || signup.getTeamId() == null) {
			return null;
		}
		return this.getTeamById(signup.getTeamId());
	}

	public List<JdbcTeamSignup> getAllSignups() {
		return teamSignupRepository.getAllSignups();
	}

	public List<JdbcTeam> getAllTeams() {
		return teamRepository.getAll();
	}

	public JdbcTeam getTeamByRoleId(String roleId) {
		return teamRepository.getByRoleId(roleId);
	}

	public JdbcTeam getTeamById(Long id) {
		return teamRepository.getById(id);
	}

	public JdbcTeamSignup getSignupForMember(Member member) {
		return teamSignupRepository.getSignupForMember(member);
	}

	public void updateTeamsMessage() {
		log.error("Scheduling teams-message update");
		if (updateTeamsMessageTask != null) {
			updateTeamsMessageTask.cancel(false);
		}
		updateTeamsMessageTask = Scheduling.SCHEDULED_EXECUTOR_SERVICE.schedule(this::_updateTeamsMessage_internal, 5, TimeUnit.SECONDS);
	}

	private void _updateTeamsMessage_internal() {
		updateTeamsMessageTask = null;
		log.info("Executing teams-message update");
		TextChannel teamsChannel = bot.getTeamsChannel();
		if (teamsChannel == null) {
			return;
		}

		List<JdbcTeam> allTeams = new ArrayList<>(getAllTeams());
		allTeams.sort(Comparator.comparing(JdbcTeam::getName));

		List<JdbcTeam> noDivTeams = new ArrayList<>(allTeams.stream().filter(t -> t.getDivision() == null).toList());
		List<JdbcTeam> divTeams = new ArrayList<>(allTeams.stream().filter(t -> t.getDivision() != null).toList());

		divTeams.sort(Comparator.comparing(JdbcTeam::getDivision).thenComparing(JdbcTeam::getName));

		List<JdbcTeamSignup> allSignups = this.teamSignupRepository.getAllSignups();

		List<String> messageLines = new ArrayList<>();

		if (!divTeams.isEmpty()) {
			int currentDiv = 0;
			for (JdbcTeam team : divTeams) {
				if (team.getDivision() != currentDiv) {
					currentDiv = team.getDivision();
					messageLines.add("# Division %d".formatted(currentDiv));
				}

				printTeam(messageLines, team, allSignups);
			}
		}

		if (!noDivTeams.isEmpty()) {
			messageLines.add("# Signed-up Teams");

			for (JdbcTeam team : noDivTeams) {
				printTeam(messageLines, team, allSignups);
			}
		}

		if (allTeams.isEmpty()) {
			messageLines.add("_No signups yet_");
		}

		systemMessageService.setSystemMessage(
				"teams",
				teamsChannel.getId(),
				m -> m.editMessage(String.join("\n", messageLines)),
				t -> t.sendMessage(String.join("\n", messageLines))
		);
	}

	private void printTeam(List<String> messageLines, JdbcTeam team, List<JdbcTeamSignup> allSignups) {
		List<JdbcTeamSignup> signups = new ArrayList<>(allSignups.stream().filter(s -> Objects.equals(s.getTeamId(), team.getId())).toList());

		List<String> parts = new ArrayList<>();

		parts.add(DiscordFormattingUtil.teamEmoteAsString(team));

		parts.add(" **%s** - ".formatted(DiscordFormattingUtil.escapeFormatting(team.getName())));

		parts.add("_");
		for (int i = 0; i < signups.size(); i++) {
			JdbcTeamSignup signup = signups.get(i);
			if (signup.isTeamLead()) {
				parts.add("__%s__".formatted(DiscordFormattingUtil.escapeFormatting(signup.getTrackmaniaName())));
			} else {
				parts.add("%s".formatted(DiscordFormattingUtil.escapeFormatting(signup.getTrackmaniaName())));
			}
			if (i + 1 < signups.size()) {
				parts.add(", ");
			}
		}
		parts.add("_");

		messageLines.add(String.join("", parts));
	}

	private void updateMemberRolesAfterCsvImport() {
		Role playerRole = bot.getPlayerRole();
		Role teamLeadRole = bot.getTeamLeadRole();
		bot.withGuild(g -> g.loadMembers().onSuccess(members -> {
			List<JdbcTeamSignup> allSignups = this.teamSignupRepository.getAllSignups();
			List<JdbcTeam> allTeams = this.teamRepository.getAll();
			List<String> allTeamRoleIds = allTeams.stream().map(JdbcTeam::getDiscordRoleId).toList();
			Map<String, Role> teamRolesMapped = new HashMap<>();
			for (String allTeamRoleId : allTeamRoleIds) {
				teamRolesMapped.put(allTeamRoleId, bot.getRoleById(allTeamRoleId));
			}

			for (Member member : members) {
				JdbcMember jdbcMember = memberService.getOrCreateMember(member);
				if (jdbcMember == null || jdbcMember.getDiscordName() == null) { // bots etc
					continue;
				}

				List<Role> rolesToAdd = new ArrayList<>();
				List<Role> rolesToRemove = new ArrayList<>();

				JdbcTeamSignup signup = allSignups.stream()
						.filter(Objects::nonNull)
						.filter(s -> Objects.equals(s.getDiscordName().toLowerCase(), jdbcMember.getDiscordName().toLowerCase()))
						.findAny()
						.orElse(null);
				boolean update = false;
				if (signup != null) {
					if (!Objects.equals(jdbcMember.getTeamId(), signup.getTeamId())) {
						jdbcMember.setTeamId(signup.getTeamId());
						update = true;
					}
					if (!DiscordRoleUtil.hasRole(member, playerRole)) {
						rolesToAdd.add(playerRole);
					}
					if (signup.isTeamLead() && !DiscordRoleUtil.hasRole(member, teamLeadRole)) {
						rolesToAdd.add(teamLeadRole);
					}
				} else {
					if (jdbcMember.getTeamId() != null) {
						jdbcMember.setTeamId(null);
						update = true;
					}
					if (DiscordRoleUtil.hasRole(member, playerRole)) {
						rolesToRemove.add(playerRole);
					}
					if (DiscordRoleUtil.hasRole(member, teamLeadRole)) {
						rolesToRemove.add(teamLeadRole);
					}
				}

				for (JdbcTeam team : allTeams) {
					Role discordRole = teamRolesMapped.get(team.getDiscordRoleId());
					if (Objects.equals(team.getId(), jdbcMember.getTeamId())) {
						// player team
						if (!DiscordRoleUtil.hasRole(member, discordRole)) {
							rolesToAdd.add(discordRole);
						}
					} else {
						// not player team
						if (DiscordRoleUtil.hasRole(member, discordRole)) {
							rolesToRemove.add(discordRole);
						}
					}
				}

				if (!rolesToAdd.isEmpty() || !rolesToRemove.isEmpty()) {
					g.modifyMemberRoles(member, rolesToAdd, rolesToRemove).reason("CSV Signup Import").queue();
				}

				if (update) {
					memberService.updateMember(jdbcMember);
					try { Thread.sleep(1000); } catch (InterruptedException e) { }
				}
			}
		}));
	}

	public List<String> importCsv(List<ImportSignupsCommandHandler.SignupCSVLine> csvLines) {
		List<String> resultLines = new ArrayList<>();
		for (ImportSignupsCommandHandler.SignupCSVLine line : csvLines) {
			if (csvLines.stream().filter(l -> Objects.equals(l.getTeamName(), line.getTeamName())).count() > 1) {
				resultLines.add("Multiple teams registered with name **%s**".formatted(line.getTeamName()));
				List<ImportSignupsCommandHandler.SignupCSVLine> teamsWithSameName = csvLines.stream().filter(l -> Objects.equals(l.getTeamName(), line.getTeamName())).toList();
				for (int i = 0; i < teamsWithSameName.size(); i++) {
					ImportSignupsCommandHandler.SignupCSVLine renameTeam = teamsWithSameName.get(i);
					renameTeam.setTeamName(renameTeam.getTeamName() + " (%d)".formatted(i+1));
				}
			}
		}

		List<JdbcTeam> existingTeams = new ArrayList<>(this.getAllTeams());
		List<JdbcTeamSignup> existingSignups = new ArrayList<>(this.teamSignupRepository.getAllSignups());

		Set<Long> teamIdsParsed = new HashSet<>();

		for (ImportSignupsCommandHandler.SignupCSVLine signupLine : csvLines) {
			try {
				JdbcTeam teamForSignup = existingTeams.stream().filter(e -> Objects.equals(e.getName(), signupLine.getTeamName())).findAny().orElse(null);
				boolean newTeam = false;
				if (teamForSignup == null) {
					teamForSignup = ensureTeam(signupLine.getTeamName(), signupLine.getTeamColor(), signupLine.getTeamImageUrl());
					existingTeams.add(teamForSignup);
					newTeam = true;
				}
				Long teamId = teamForSignup.getId();

				List<JdbcTeamSignup> signupsForTeam = new ArrayList<>(existingSignups.stream().filter(s -> Objects.equals(s.getTeamId(), teamId)).toList());

				List<CSVSignupMember> csvSignupMembers = membersFromSignup(signupLine);

				for (CSVSignupMember csvSignupMember : csvSignupMembers) {
					JdbcTeamSignup existingSignup = signupsForTeam.stream().filter(s -> Objects.equals(s.getTrackmaniaUuid(), csvSignupMember.trackmaniaUuid())).findAny().orElse(null);
					if (existingSignup == null) {
						existingSignup = new JdbcTeamSignup(null, teamId, csvSignupMember.discordName(), csvSignupMember.trackmaniaName(), csvSignupMember.trackmaniaUuid(), csvSignupMember.first());
						this.teamSignupRepository.saveSignup(existingSignup);
						if (!newTeam) {
							resultLines.add("Added **%s** to team **%s**".formatted(csvSignupMember.trackmaniaName(), teamForSignup.getName()));
						}
					} else {
						signupsForTeam.remove(existingSignup);

						if (!existingSignup.getTrackmaniaName().equals(csvSignupMember.trackmaniaName()) ||
							!existingSignup.getDiscordName().equals(csvSignupMember.discordName()) ||
							existingSignup.isTeamLead() != csvSignupMember.first()) {
							existingSignup.setDiscordName(csvSignupMember.discordName());
							existingSignup.setTrackmaniaName(csvSignupMember.trackmaniaName());
							existingSignup.setTeamLead(csvSignupMember.first());
							this.teamSignupRepository.saveSignup(existingSignup);
						}
					}
				}
				if (newTeam) {
					resultLines.add("Added team **%s** with members %s, %s, %s, %s, %s and %s".formatted(
							teamForSignup.getName(),
							csvSignupMembers.get(0).trackmaniaName(),
							csvSignupMembers.get(1).trackmaniaName(),
							csvSignupMembers.get(2).trackmaniaName(),
							csvSignupMembers.get(3).trackmaniaName(),
							csvSignupMembers.get(4).trackmaniaName(),
							csvSignupMembers.get(5).trackmaniaName()
					));
				}

				for (JdbcTeamSignup jdbcTeamSignup : signupsForTeam) {
					resultLines.add("Removed **%s** from team **%s**".formatted(jdbcTeamSignup.getTrackmaniaName(), teamForSignup.getName()));
					this.teamSignupRepository.deleteSignup(jdbcTeamSignup);
				}

				teamIdsParsed.add(teamForSignup.getId());
			} catch (Exception e) {
				resultLines.add(String.format("Failed parsing members for team `%s`: %s", signupLine.getTeamName(), e.getMessage()));
				log.error("Failed parsing members for team " + signupLine.getTeamName(), e);
			}
		}

		existingTeams.stream().filter(t -> !teamIdsParsed.contains(t.getId())).forEach(e -> {
			resultLines.add("Removed team **%s**".formatted(e.getName()));
			this.teamRepository.deleteTeam(bot, e);
			existingSignups.stream().filter(s -> Objects.equals(s.getTeamId(), e.getId())).forEach(signup -> {
				this.teamSignupRepository.deleteSignup(signup);
			});
		});
		existingTeams.removeIf(t -> !teamIdsParsed.contains(t.getId()));

		// validations

		// check player double signups
		List<JdbcTeamSignup> allSignups = this.teamSignupRepository.getAllSignups();
		for (JdbcTeamSignup signup : allSignups) {
			if (allSignups.stream().filter(s -> Objects.equals(s.getTrackmaniaUuid(), signup.getTrackmaniaUuid())).count() > 1) {
				resultLines.add("**%s** is signed up more than once".formatted(signup.getTrackmaniaName()));
			}
		}

		// check every team having 3 sign-ups
		List<JdbcTeam> allTeams = this.teamRepository.getAll();
		for (JdbcTeam team : allTeams) {
			long signupCount = allSignups.stream().filter(s -> Objects.equals(s.getTeamId(), team.getId())).count();
			if (signupCount != 6) {
				resultLines.add("Team **%s** has %d sign-ups".formatted(team.getName(), signupCount));
			}
		}

		// /validations

		this.updateTeamsMessage();
		new Thread(this::updateMemberRolesAfterCsvImport).start();

		return resultLines;
	}

	private static List<CSVSignupMember> membersFromSignup(ImportSignupsCommandHandler.SignupCSVLine line) throws Exception {
		return List.of(
				new CSVSignupMember(line.getMember1DiscordName(), line.getMember1TrackmaniaName(), factorTrackmaniaUuid(line.getMember1TrackmaniaUserLink()), true),
				new CSVSignupMember(line.getMember2DiscordName(), line.getMember2TrackmaniaName(), factorTrackmaniaUuid(line.getMember2TrackmaniaUserLink()), false),
				new CSVSignupMember(line.getMember3DiscordName(), line.getMember3TrackmaniaName(), factorTrackmaniaUuid(line.getMember3TrackmaniaUserLink()), false),
				new CSVSignupMember(line.getMember4DiscordName(), line.getMember4TrackmaniaName(), factorTrackmaniaUuid(line.getMember4TrackmaniaUserLink()), false),
				new CSVSignupMember(line.getMember5DiscordName(), line.getMember5TrackmaniaName(), factorTrackmaniaUuid(line.getMember5TrackmaniaUserLink()), false),
				new CSVSignupMember(line.getMember6DiscordName(), line.getMember6TrackmaniaName(), factorTrackmaniaUuid(line.getMember6TrackmaniaUserLink()), false)
		);
	}

	private static String factorTrackmaniaUuid(String link) throws Exception {
		if (link.length() == 36) {
			try {
				UUID.fromString(link);
				return link;
			} catch (IllegalArgumentException ex) {
				throw new Exception("Invalid trackmania uuid format", ex);
			}
		}
		if (!link.startsWith("https://trackmania.io/#/player/")) {
			throw new Exception("Invalid trackmania.io link");
		}
		link = link.substring("https://trackmania.io/#/player/".length());
		if (link.contains("/")) {
			link = link.substring(0, link.indexOf("/"));
		}
		return link;
	}

	public void ensurePlayerRoles(Member discordMember, JdbcMember botMember, Role memberRole, Role playerRole, Role teamLeadRole) {
		List<Role> addRoles = new ArrayList<>();
		List<Role> removeRoles = new ArrayList<>();
		if (memberRole != null) {
			if (!DiscordRoleUtil.hasRole(discordMember, memberRole)) {
				addRoles.add(memberRole);
			}
		}
		if (playerRole != null) {
			if (DiscordRoleUtil.hasRole(discordMember, playerRole) && botMember.getTeamId() == null) {
				removeRoles.add(playerRole);
			} else if (!DiscordRoleUtil.hasRole(discordMember, playerRole) && botMember.getTeamId() != null) {
				addRoles.add(playerRole);
			}
		}
		if (teamLeadRole != null) {
			JdbcTeamSignup signupForMember = teamSignupRepository.getSignupForMember(discordMember);
			boolean isTeamLead = signupForMember != null && signupForMember.isTeamLead();
			if (DiscordRoleUtil.hasRole(discordMember, teamLeadRole) && !isTeamLead) {
				removeRoles.add(teamLeadRole);
			} else if (!DiscordRoleUtil.hasRole(discordMember, teamLeadRole) && isTeamLead) {
				addRoles.add(teamLeadRole);
			}
		}
		List<JdbcTeam> teams = teamRepository.getAll();
		for (JdbcTeam team : teams) {
			if (Objects.equals(botMember.getTeamId(), team.getId())) {
				// in team
				if (!DiscordRoleUtil.hasRole(discordMember, team.getDiscordRoleId())) {
					Role r = bot.getRoleById(team.getDiscordRoleId());
					addRoles.add(r);
				}
			} else {
				// not in team
				if (DiscordRoleUtil.hasRole(discordMember, team.getDiscordRoleId())) {
					Role r = bot.getRoleById(team.getDiscordRoleId());
					removeRoles.add(r);
				}
			}
		}
		if (!addRoles.isEmpty() || !removeRoles.isEmpty()) {
			discordMember.getGuild().modifyMemberRoles(
					discordMember,
					addRoles,
					removeRoles
			).queue();
		}
	}

	public JdbcTeam updateTeamEmote(JdbcTeam team, DiscordArgUtil.ParsedEmote emoji) {
		if (emoji != null) {
			team.setEmoteName(emoji.emoteName());
			team.setEmoteId(emoji.emoteId());
		} else {
			team.setEmoteName(null);
			team.setEmoteId(null);
		}
		return this.teamRepository.updateTeamEmote(team);
	}

	public void updateTeamDiv(JdbcTeam team, int division) {
		team.setDivision(division);
		this.teamRepository.updateDivision(team);
	}

	private record CSVSignupMember(
			String discordName,
			String trackmaniaName,
			String trackmaniaUuid,
			boolean first
	) { }
}
