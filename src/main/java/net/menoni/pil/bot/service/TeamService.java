package net.menoni.pil.bot.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.discord.DiscordBot;
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
import java.util.stream.Collectors;

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

	public void deleteTeam(JdbcTeam team) {
		this.teamSignupRepository.deleteSignupsForTeam(team.getId());
		this.teamRepository.deleteTeam(bot, team);
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
		log.info("Scheduling teams-message update");
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
		Set<Role> teamCaptainDivRoles = getTeamCaptainDivRoles();
		Set<Role> teamMemberDivRoles = getTeamMemberDivRoles();
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

					allTeams.stream().filter(t -> Objects.equals(t.getId(), signup.getTeamId())).findAny().ifPresent((playerTeam) -> {
						if (playerTeam.getDivision() == null) {
							// no team division so remove any of the roles
							for (Role teamMemberDivRole : teamMemberDivRoles) {
								if (DiscordRoleUtil.hasRole(member, teamMemberDivRole)) {
									rolesToRemove.add(teamMemberDivRole);
								}
							}
							for (Role teamCaptainDivRole : teamCaptainDivRoles) {
								if (DiscordRoleUtil.hasRole(member, teamCaptainDivRole)) {
									rolesToRemove.add(teamCaptainDivRole);
								}
							}
						} else {
							// make set of any roles the user should not have, start with all roles
							Set<Role> possibleRemoveRoles = new HashSet<>(teamCaptainDivRoles);
							possibleRemoveRoles.addAll(teamMemberDivRoles);

							// find role they need to have, make sure that one isn't removed
							Role teamDivMemberRole = getTeamMemberDivRole(playerTeam.getDivision());
							if (teamDivMemberRole != null) {
								possibleRemoveRoles.remove(teamDivMemberRole);

								// ensure player has team-div-n role
								if (!DiscordRoleUtil.hasRole(member, teamDivMemberRole)) {
									rolesToAdd.add(teamDivMemberRole);
								}
							}

							// find captain role they may need to have if they are captain, and do the same
							if (signup.isTeamLead()) {
								Role teamDivCaptainRole = getTeamCaptainDivRole(playerTeam.getDivision());
								if (teamDivCaptainRole != null) {
									possibleRemoveRoles.remove(teamDivCaptainRole);

									if (!DiscordRoleUtil.hasRole(member, teamDivCaptainRole)) {
										rolesToAdd.add(teamDivCaptainRole);
									}
								}
							}

							// check any of the other div-member/div-captain roles they should not have
							for (Role possibleRemoveRole : possibleRemoveRoles) {
								if (DiscordRoleUtil.hasRole(member, possibleRemoveRole)) {
									rolesToRemove.add(possibleRemoveRole);
								}
							}
						}
					});
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
					for (Role teamMemberDivRole : teamMemberDivRoles) {
						if (DiscordRoleUtil.hasRole(member, teamMemberDivRole)) {
							rolesToRemove.add(teamMemberDivRole);
						}
					}
					for (Role teamCaptainDivRole : teamCaptainDivRoles) {
						if (DiscordRoleUtil.hasRole(member, teamCaptainDivRole)) {
							rolesToRemove.add(teamCaptainDivRole);
						}
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

	public List<String> importCsv(List<String[]> lines) {
		List<SignupCSVLine> csvLines = new ArrayList<>();
		for (String[] line : lines) {
			csvLines.add(parseLine(line));
		}

		List<String> resultLines = new ArrayList<>();
		for (SignupCSVLine line : csvLines) {
			if (csvLines.stream().filter(l -> Objects.equals(l.getTeamName(), line.getTeamName())).count() > 1) {
				resultLines.add("Multiple teams registered with name **%s**".formatted(line.getTeamName()));
				List<SignupCSVLine> teamsWithSameName = csvLines.stream().filter(l -> Objects.equals(l.getTeamName(), line.getTeamName())).toList();
				for (int i = 0; i < teamsWithSameName.size(); i++) {
					SignupCSVLine renameTeam = teamsWithSameName.get(i);
					renameTeam.setTeamName(renameTeam.getTeamName() + " (%d)".formatted(i+1));
				}
			}
		}

		List<JdbcTeam> existingTeams = new ArrayList<>(this.getAllTeams());
		List<JdbcTeamSignup> existingSignups = new ArrayList<>(this.teamSignupRepository.getAllSignups());

		Set<Long> teamIdsParsed = new HashSet<>();

		for (SignupCSVLine signupLine : csvLines) {
			try {
				JdbcTeam teamForSignup = existingTeams.stream().filter(e -> Objects.equals(e.getName(), signupLine.getTeamName())).findAny().orElse(null);
				boolean newTeam = false;
				if (teamForSignup == null) {
					teamForSignup = ensureTeam(signupLine.getTeamName(), signupLine.getTeamColor(), nullifyBlank(signupLine.getTeamImageUrl()));
					existingTeams.add(teamForSignup);
					newTeam = true;
				}
				Long teamId = teamForSignup.getId();

				List<JdbcTeamSignup> signupsForTeam = new ArrayList<>(existingSignups.stream().filter(s -> Objects.equals(s.getTeamId(), teamId)).toList());

				List<CSVSignupMember> csvSignupMembers = membersFromSignup(signupLine.getTeamName(), resultLines, signupLine);

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
					resultLines.add("Added team **%s** with members %s".formatted(
							teamForSignup.getName(),
							csvSignupMembers.stream().map(CSVSignupMember::trackmaniaName).collect(Collectors.joining(", "))
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
			this.teamSignupRepository.deleteSignupsForTeam(e.getId());
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

		// check every team having 3-6 sign-ups
		List<JdbcTeam> allTeams = this.teamRepository.getAll();
		for (JdbcTeam team : allTeams) {
			long signupCount = allSignups.stream().filter(s -> Objects.equals(s.getTeamId(), team.getId())).count();
			if (signupCount < 3 || signupCount > 6) {
				resultLines.add("Team **%s** has %d sign-ups (expecting 3-6)".formatted(team.getName(), signupCount));
			}
		}

		// /validations

		if (!resultLines.isEmpty()) {
			this.updateTeamsMessage();
			new Thread(this::updateMemberRolesAfterCsvImport).start();
		}

		return resultLines;
	}

	private static List<CSVSignupMember> membersFromSignup(String teamName, List<String> resultLines, SignupCSVLine line) throws Exception {
		List<CSVSignupMember> members = new ArrayList<>();
		addSignupMember(1, teamName, resultLines, members, line.getMember1DiscordName(), line.getMember1TrackmaniaName(), line.getMember1TrackmaniaUserLink(), true);
		addSignupMember(2, teamName, resultLines, members, line.getMember2DiscordName(), line.getMember2TrackmaniaName(), line.getMember2TrackmaniaUserLink(), false);
		addSignupMember(3, teamName, resultLines, members, line.getMember3DiscordName(), line.getMember3TrackmaniaName(), line.getMember3TrackmaniaUserLink(), false);
		addSignupMember(4, teamName, resultLines, members, line.getMember4DiscordName(), line.getMember4TrackmaniaName(), line.getMember4TrackmaniaUserLink(), false);
		addSignupMember(5, teamName, resultLines, members, line.getMember5DiscordName(), line.getMember5TrackmaniaName(), line.getMember5TrackmaniaUserLink(), false);
		addSignupMember(6, teamName, resultLines, members, line.getMember6DiscordName(), line.getMember6TrackmaniaName(), line.getMember6TrackmaniaUserLink(), false);
		return members;
	}

	private static void addSignupMember(int number, String teamName, List<String> resultLines, List<CSVSignupMember> members, String discordName, String trackmaniaName, String trackmaniaUuid, boolean captain) throws Exception {
		discordName = nullifyBlank(discordName);
		trackmaniaName = nullifyBlank(trackmaniaName);
		trackmaniaUuid = nullifyBlank(trackmaniaUuid);
		if (discordName == null || trackmaniaName == null || trackmaniaUuid == null) {
			// incomplete captain
			if (captain) {
				List<String> missingFieldNames = new ArrayList<>();
				if (discordName == null) {
					missingFieldNames.add("discord-name");
				}
				if (trackmaniaName == null) {
					missingFieldNames.add("trackmania-name");
				}
				if (trackmaniaUuid == null) {
					missingFieldNames.add("trackmania-uuid");
				}
				resultLines.add("Captain sign-up for team \"%s\" not valid - missing: %s".formatted(
						teamName,
						String.join(", ", missingFieldNames)
				));
				return;
			}
			// partially incomplete member
			if (discordName != null || trackmaniaName != null || trackmaniaUuid != null) {
				List<String> providedFields = new ArrayList<>();
				List<String> missingFields = new ArrayList<>();
				markFieldStatus(providedFields, missingFields, discordName, "discord-name");
				markFieldStatus(providedFields, missingFields, trackmaniaName, "trackmania-name");
				markFieldStatus(providedFields, missingFields, trackmaniaUuid, "trackmania-uuid");
				resultLines.add("Sign-up %d for team \"%s\" has %s but is missing %s".formatted(
						number, teamName,
						String.join(", ", providedFields),
						String.join(", ", missingFields)
				));
			}
			return;
		}
		members.add(new CSVSignupMember(discordName, trackmaniaName, factorTrackmaniaUuid(trackmaniaUuid), captain));
	}

	private static void markFieldStatus(List<String> provided, List<String> missing, String fieldValue, String fieldName) {
		if (fieldValue == null) {
			missing.add(fieldName);
		} else {
			provided.add(fieldName);
		}
	}

	private static String nullifyBlank(String str) {
		if (str != null && str.isBlank()) {
			return null;
		}
		return str;
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

	public boolean ensurePlayerRoles(Member discordMember, JdbcMember botMember, Role playerRole, Role teamLeadRole) {
		List<Role> addRoles = new ArrayList<>();
		List<Role> removeRoles = new ArrayList<>();
		if (playerRole != null) {
			if (DiscordRoleUtil.hasRole(discordMember, playerRole) && botMember.getTeamId() == null) {
				removeRoles.add(playerRole);
			} else if (!DiscordRoleUtil.hasRole(discordMember, playerRole) && botMember.getTeamId() != null) {
				addRoles.add(playerRole);
			}
		}
		JdbcTeamSignup signupForMember = teamSignupRepository.getSignupForMember(discordMember);
		boolean isTeamLead = signupForMember != null && signupForMember.isTeamLead();
		if (teamLeadRole != null) {
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
		Set<Role> teamMemberDivRoles = getTeamMemberDivRoles();
		Set<Role> teamCaptainDivRoles = getTeamCaptainDivRoles();
		Set<Role> allTeamDivRoles = new HashSet<>(teamMemberDivRoles);
		allTeamDivRoles.addAll(teamCaptainDivRoles);
		// start with assumption they should not have any of these roles
		// remove roles from the set that they should have, and check if they need to be given
		if (botMember.getTeamId() != null) {
			JdbcTeam playerTeam = teams.stream().filter(t -> Objects.equals(t.getId(), botMember.getTeamId())).findAny().orElse(null);
			if (playerTeam != null && playerTeam.getDivision() != null) {
				// member check
				Role teamMemberDivRole = getTeamMemberDivRole(playerTeam.getDivision());
				if (teamMemberDivRole != null) {
					allTeamDivRoles.remove(teamMemberDivRole);
					if (!DiscordRoleUtil.hasRole(discordMember, teamMemberDivRole)) {
						addRoles.add(teamMemberDivRole);
					}
				}

				// captain check
				if (isTeamLead) {
					Role teamCaptainDivRole = getTeamCaptainDivRole(playerTeam.getDivision());
					if (teamCaptainDivRole != null) {
						allTeamDivRoles.remove(teamCaptainDivRole);
						if (!DiscordRoleUtil.hasRole(discordMember, teamCaptainDivRole)) {
							addRoles.add(teamCaptainDivRole);
						}
					}
				}
			}
		}
		// any roles still in allTeamDivRoles are ones the player should not have -- check if need to remove
		for (Role allTeamDivRole : allTeamDivRoles) {
			if (DiscordRoleUtil.hasRole(discordMember, allTeamDivRole)) {
				removeRoles.add(allTeamDivRole);
			}
		}

		if (!addRoles.isEmpty() || !removeRoles.isEmpty()) {
			JDAUtil.queueAndWait(discordMember.getGuild().modifyMemberRoles(
					discordMember,
					addRoles,
					removeRoles
			));
			return true;
		}
		return false;
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
		if (division > 0) {
			team.setDivision(division);
		} else {
			team.setDivision(null);
		}
		this.teamRepository.updateDivision(team);
	}

	private Set<Role> getTeamMemberDivRoles() {
		Set<Role> roleSet = new HashSet<>();
		for (int i = 1; i < 10; i++) {
			String roleId = bot.getConfig().getTeamMemberDivRole(i);
			if (roleId != null) {
				Role role = bot.getRoleById(roleId);
				if (role != null) {
					roleSet.add(role);
				}
			}
		}
		return roleSet;
	}

	private Set<Role> getTeamCaptainDivRoles() {
		Set<Role> roleSet = new HashSet<>();
		for (int i = 1; i < 10; i++) {
			String roleId = bot.getConfig().getTeamCaptainDivRole(i);
			if (roleId != null) {
				Role role = bot.getRoleById(roleId);
				if (role != null) {
					roleSet.add(role);
				}
			}
		}
		return roleSet;
	}

	private Role getTeamMemberDivRole(int division) {
		String roleId = bot.getConfig().getTeamMemberDivRole(division);
		if (roleId != null) {
			Role role = bot.getRoleById(roleId);
			if (role != null) {
				return role;
			}
		}
		return null;
	}

	private Role getTeamCaptainDivRole(int division) {
		String roleId = bot.getConfig().getTeamMemberDivRole(division);
		if (roleId != null) {
			Role role = bot.getRoleById(roleId);
			if (role != null) {
				return role;
			}
		}
		return null;
	}

	private record CSVSignupMember(
			String discordName,
			String trackmaniaName,
			String trackmaniaUuid,
			boolean first
	) { }

	private SignupCSVLine parseLine(String[] line) {
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
