package net.menoni.pil.bot.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.commons.util.TMUtils;
import net.menoni.jda.commons.util.DiscordRoleUtil;
import net.menoni.jda.commons.util.JDAUtil;
import net.menoni.pil.bot.config.FeatureFlags;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.jdbc.model.JdbcMember;
import net.menoni.pil.bot.jdbc.model.JdbcTeam;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.jdbc.repository.TeamRepository;
import net.menoni.pil.bot.jdbc.repository.TeamSignupRepository;
import net.menoni.pil.bot.util.DiscordArgUtil;
import net.menoni.pil.bot.util.DiscordFormattingUtil;
import net.menoni.pil.bot.util.Scheduling;
import net.menoni.ws.client.MenoniWsClient;
import net.menoni.ws.common.model.discord.WsDiscordUserLinks;
import net.menoni.ws.discord.service.TmNameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
	@Autowired
	private TmNameService tmNameService;
	@Autowired
	private MenoniWsClient ws;

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
		this.updateMemberRolesAfterCsvImport();
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
		allTeams.sort(Comparator.comparing(t -> t.getName().toLowerCase()));

		List<JdbcTeam> noDivTeams = new ArrayList<>(allTeams.stream().filter(t -> t.getDivision() == null).toList());
		List<JdbcTeam> divTeams = new ArrayList<>(allTeams.stream().filter(t -> t.getDivision() != null).toList());

		divTeams.sort(Comparator.comparing(JdbcTeam::getDivision).thenComparing(t -> t.getName().toLowerCase()));

		List<JdbcTeamSignup> allSignups = this.teamSignupRepository.getAllSignups().stream().filter(s -> !s.isHidden()).toList();

		List<List<String>> messages = new ArrayList<>();

		if (!divTeams.isEmpty()) {
			List<String> messageLines = new ArrayList<>();
			int currentDiv = 0;
			for (JdbcTeam team : divTeams) {
				if (team.getDivision() != currentDiv) {
					currentDiv = team.getDivision();
					if (!messageLines.isEmpty()) {
						messages.add(messageLines);
					}
					messageLines = new ArrayList<>();
					messageLines.add("# Division %d".formatted(currentDiv));
				}

				printTeam(messageLines, team, allSignups);
			}
			if (!messageLines.isEmpty()) {
				messages.add(messageLines);
			}
		}

		if (!noDivTeams.isEmpty()) {
			List<String> messageLines = new ArrayList<>();
			messageLines.add("# Signed-up Teams");

			int i = 0;
			for (JdbcTeam team : noDivTeams) {
				printTeam(messageLines, team, allSignups);
				i++;
				if (i % 10 == 0) {
					if (!messageLines.isEmpty()) {
						messages.add(messageLines);
						messageLines = new ArrayList<>();
					}
				}
			}
			if (!messageLines.isEmpty()) {
				messages.add(messageLines);
			}
		}

		if (allTeams.isEmpty()) {
			messages.add(List.of("_No signups yet_"));
		} else {
			messages.get(messages.size() - 1).add("-# %d teams total".formatted(allTeams.size()));
		}

		for (int i = 0; i < messages.size(); i++) {
			List<String> messageLines = messages.get(i);
			systemMessageService.setIndexableSystemMessage(
					"teams_s3",
					i + 1,
					teamsChannel.getId(),
					m -> m.editMessage(String.join("\n", messageLines)),
					t -> t.sendMessage(String.join("\n", messageLines))
			);
		}
		systemMessageService.deleteFurtherIndexedMessages("teams", messages.size() + 1);
	}

	private void printTeam(List<String> messageLines, JdbcTeam team, List<JdbcTeamSignup> allSignups) {
		List<JdbcTeamSignup> signups = new ArrayList<>(allSignups.stream().filter(s -> Objects.equals(s.getTeamId(), team.getId())).toList());

		List<String> parts = new ArrayList<>();

		parts.add(DiscordFormattingUtil.teamEmoteAsString(team));

		parts.add(" **%s** - ".formatted(DiscordFormattingUtil.escapeFormatting(team.getName())));

		parts.add("_");
		for (int i = 0; i < signups.size(); i++) {
			JdbcTeamSignup signup = signups.get(i);
			if (signup.isHidden()) {
				continue;
			}
			String s = "";
			if (signup.isTeamLead()) {
				s = "__%s__".formatted(DiscordFormattingUtil.escapeFormatting(signup.getTrackmaniaName()));
			} else {
				s = "%s".formatted(DiscordFormattingUtil.escapeFormatting(signup.getTrackmaniaName()));
			}
			if (signup.isArchived()) {
				s = "~~%s~~".formatted(s);
			}
			parts.add(s);
			if (i + 1 < signups.size()) {
				parts.add(", ");
			}
		}
		parts.add("_");

		messageLines.add(String.join("", parts));
	}

	private void updateMemberRolesAfterCsvImport() {
		if (FeatureFlags.DISABLE_ROLE_CHANGES) {
			return;
		}
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

				DiscordRoleUtil.RoleUpdateAction updater = DiscordRoleUtil.updater(member);

				JdbcTeamSignup signup = allSignups.stream()
						.filter(Objects::nonNull)
						.filter(s -> Objects.equals(s.getDiscordName().toLowerCase(), jdbcMember.getDiscordName().toLowerCase()))
						.findAny()
						.orElse(null);
				boolean hidden = false;
				boolean update = false;
				if (signup != null) {
					hidden = signup.isHidden();
					if (!Objects.equals(jdbcMember.getTeamId(), signup.getTeamId())) {
						jdbcMember.setTeamId(signup.getTeamId());
						update = true;
					}
//					if (!hidden) {
						updater.conditional(playerRole, true);
						updater.conditional(teamLeadRole, signup.isTeamLead());
//					}

					allTeams.stream().filter(t -> Objects.equals(t.getId(), signup.getTeamId())).findAny().ifPresent((playerTeam) -> {
						if (playerTeam.getDivision() == null) {
							// no team division so remove any of the roles
							for (Role teamMemberDivRole : teamMemberDivRoles) {
								updater.conditional(teamMemberDivRole, false);
							}
							for (Role teamCaptainDivRole : teamCaptainDivRoles) {
								updater.conditional(teamCaptainDivRole, false);
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
								updater.conditional(teamDivMemberRole, true);
							}

							// find captain role they may need to have if they are captain, and do the same
							if (signup.isTeamLead()) {
								Role teamDivCaptainRole = getTeamCaptainDivRole(playerTeam.getDivision());
								if (teamDivCaptainRole != null) {
									possibleRemoveRoles.remove(teamDivCaptainRole);

									updater.conditional(teamDivCaptainRole, true);
								}
							}

							// check any of the other div-member/div-captain roles they should not have
							for (Role possibleRemoveRole : possibleRemoveRoles) {
								updater.conditional(possibleRemoveRole, false);
							}
						}
					});
				} else {
					if (jdbcMember.getTeamId() != null) {
						jdbcMember.setTeamId(null);
						update = true;
					}
					updater.conditional(playerRole, false);
					updater.conditional(teamLeadRole, false);
					for (Role teamMemberDivRole : teamMemberDivRoles) {
						updater.conditional(teamMemberDivRole, false);
					}
					for (Role teamCaptainDivRole : teamCaptainDivRoles) {
						updater.conditional(teamCaptainDivRole, false);
					}
				}

				for (JdbcTeam team : allTeams) {
					Role discordRole = teamRolesMapped.get(team.getDiscordRoleId());
					if (Objects.equals(team.getId(), jdbcMember.getTeamId())) {
						// player team
						updater.conditional(discordRole, true);
					} else {
						// not player team
						updater.conditional(discordRole, false);
					}
				}

				updater.modifyMemberRoles(r -> JDAUtil.queueAndWait(r.reason("CSV Signup Import")));

				if (update) {
					memberService.updateMember(jdbcMember);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}));
	}

	private void addIdToSetIfPresent(Set<BasicPlayer> set, String idVal, String nameVal) {
		String v = nullifyBlank(idVal);
		if (v == null) {
			return;
		}
		try {
			String uuid = factorTrackmaniaUuid(v);
			set.add(new BasicPlayer(uuid, nameVal));
		} catch (Exception e) {
		}
	}

	private record BasicPlayer(String id, String name) {
	}

	public List<String> importCsv(List<String[]> lines) {
		List<SignupCSVLine> csvLines = new ArrayList<>();
		for (String[] line : lines) {
			csvLines.add(parseLine(line));
		}

		Set<BasicPlayer> playerEntries = new HashSet<>();
		for (SignupCSVLine csvLine : csvLines) {
			addIdToSetIfPresent(playerEntries, csvLine.getMember1TrackmaniaUserLink(), csvLine.getMember1TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember2TrackmaniaUserLink(), csvLine.getMember2TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember3TrackmaniaUserLink(), csvLine.getMember3TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember4TrackmaniaUserLink(), csvLine.getMember4TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember5TrackmaniaUserLink(), csvLine.getMember5TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember6TrackmaniaUserLink(), csvLine.getMember6TrackmaniaName());
			// extra members
			addIdToSetIfPresent(playerEntries, csvLine.getMember7TrackmaniaUserLink(), csvLine.getMember7TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember8TrackmaniaUserLink(), csvLine.getMember8TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember9TrackmaniaUserLink(), csvLine.getMember9TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember10TrackmaniaUserLink(), csvLine.getMember10TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember11TrackmaniaUserLink(), csvLine.getMember11TrackmaniaName());
			addIdToSetIfPresent(playerEntries, csvLine.getMember12TrackmaniaUserLink(), csvLine.getMember12TrackmaniaName());
		}

		Map<String, String> nicks = tmNameService.getNicksForAccountIds(playerEntries, BasicPlayer::id, BasicPlayer::name);

		List<String> resultLines = new ArrayList<>();
		for (SignupCSVLine line : csvLines) {
			if (csvLines.stream().filter(l -> Objects.equals(l.getTeamName(), line.getTeamName())).count() > 1) {
				resultLines.add("Multiple teams registered with name **%s**".formatted(line.getTeamName()));
				List<SignupCSVLine> teamsWithSameName = csvLines.stream().filter(l -> Objects.equals(l.getTeamName(), line.getTeamName())).toList();
				for (int i = 0; i < teamsWithSameName.size(); i++) {
					SignupCSVLine renameTeam = teamsWithSameName.get(i);
					renameTeam.setTeamName(renameTeam.getTeamName() + " (%d)".formatted(i + 1));
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

				List<CSVSignupMember> csvSignupMembers = membersFromSignup(nicks, signupLine.getTeamName(), resultLines, signupLine);

				for (CSVSignupMember csvSignupMember : csvSignupMembers) {
					JdbcTeamSignup existingSignup = signupsForTeam.stream().filter(s -> Objects.equals(s.getTrackmaniaUuid(), csvSignupMember.trackmaniaUuid())).findAny().orElse(null);
					if (existingSignup == null) {
						existingSignup = new JdbcTeamSignup(null, teamId, csvSignupMember.discordName(), csvSignupMember.trackmaniaName(), csvSignupMember.trackmaniaUuid(), csvSignupMember.captain(), false, !csvSignupMember.active());
						this.teamSignupRepository.saveSignup(existingSignup);
						if (!newTeam) {
							String extra = "";
							if (csvSignupMember.extra()) {
								if (csvSignupMember.captain()) {
									extra = " (extra captain)";
								} else {
									extra = " (extra player)";
								}
							}
							resultLines.add("Added%s **%s** to team **%s**".formatted(extra, csvSignupMember.trackmaniaName(), teamForSignup.getName()));
						}
					} else {
						signupsForTeam.remove(existingSignup);

						if (!existingSignup.getTrackmaniaName().equals(csvSignupMember.trackmaniaName()) ||
								!existingSignup.getDiscordName().equals(csvSignupMember.discordName()) ||
								existingSignup.isTeamLead() != csvSignupMember.captain() ||
								existingSignup.isArchived() == csvSignupMember.active()) {
							if (!existingSignup.getDiscordName().equals(csvSignupMember.discordName())) {
								resultLines.add("updating discord name from **%s** to **%s** in team **%s**".formatted(
										existingSignup.getDiscordName(),
										csvSignupMember.discordName(),
										teamForSignup.getName()
								));
							}
							if (!existingSignup.getTrackmaniaName().equals(csvSignupMember.trackmaniaName())) {
								resultLines.add("updating TM name from **%s** to **%s** in team **%s**".formatted(
										existingSignup.getTrackmaniaName(),
										csvSignupMember.trackmaniaName(),
										teamForSignup.getName()
								));
							}
							existingSignup.setDiscordName(csvSignupMember.discordName());
							existingSignup.setTrackmaniaName(csvSignupMember.trackmaniaName());
							if (existingSignup.isTeamLead() != csvSignupMember.captain()) {
								if (csvSignupMember.captain()) {
									resultLines.add("set **%s** as captain for team **%s**".formatted(DiscordFormattingUtil.escapeFormatting(csvSignupMember.trackmaniaName()), teamForSignup.getName()));
								} else {
									resultLines.add("unset **%s** as captain for team **%s**".formatted(DiscordFormattingUtil.escapeFormatting(csvSignupMember.trackmaniaName()), teamForSignup.getName()));
								}
							}
							if (existingSignup.isArchived() == csvSignupMember.active()) {
								if (csvSignupMember.active()) {
									resultLines.add("re-activated player **%s** for team **%s**".formatted(DiscordFormattingUtil.escapeFormatting(csvSignupMember.trackmaniaName()), teamForSignup.getName()));
								} else {
									resultLines.add("de-activated player **%s** for team **%s**".formatted(DiscordFormattingUtil.escapeFormatting(csvSignupMember.trackmaniaName()), teamForSignup.getName()));
								}
							}
							existingSignup.setTeamLead(csvSignupMember.captain());
							existingSignup.setArchived(!csvSignupMember.active());
							this.teamSignupRepository.saveSignup(existingSignup);
						}
					}
				}
				if (newTeam) {
					String image = teamForSignup.getImageUrl();
					if (image == null || image.isBlank()) {
						image = "_not provided_";
					} else {
						image = "<%s>".formatted(image);
					}
					resultLines.add("Added team **%s** with members %s\n\timage: %s".formatted(
							teamForSignup.getName(),
							csvSignupMembers.stream()
									.map(CSVSignupMember::trackmaniaName)
									.map(DiscordFormattingUtil::escapeFormatting)
									.collect(Collectors.joining(", ")),
							image
					));
				}

				for (JdbcTeamSignup jdbcTeamSignup : signupsForTeam) {
					resultLines.add("Removed **%s** from team **%s**".formatted(jdbcTeamSignup.getTrackmaniaName(), teamForSignup.getName()));
					this.teamSignupRepository.deleteSignup(jdbcTeamSignup);
				}
				teamIdsParsed.add(teamForSignup.getId());
			} catch (Exception e) {
				resultLines.add(String.format("Failed parsing members for team `%s`: %s", signupLine.getTeamName(), e.getMessage()));
				log.error("Failed parsing members for team %s".formatted(signupLine.getTeamName()), e);
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
		if (!FeatureFlags.DISABLE_TEAM_SIZE_ERROR) {
			for (JdbcTeam team : allTeams) {
				long signupCount = allSignups.stream().filter(s -> Objects.equals(s.getTeamId(), team.getId())).count();
				if (signupCount < 3 || signupCount > 6) {
					resultLines.add("Team **%s** has %d sign-ups (expecting 3-6)".formatted(team.getName(), signupCount));
				}
			}
		}

		// /validations

		if (!resultLines.isEmpty()) {
			this.updateTeamsMessage();
			new Thread(this::updateMemberRolesAfterCsvImport).start();
		}

		Guild guild = this.bot.getGuild(this.bot.getGuildId());
		if (guild != null) {
			log.info("Declaring user links");
			List<WsDiscordUserLinks> linksForGuildMembers = new ArrayList<>();
			try {
				linksForGuildMembers.addAll(this.ws.getDiscordDomain().getUserService().getLinksForGuildMembers(this.bot.getGuildId()));
			} catch (Exception e) {
				log.warn("Failed to fetch guild member links", e);
			}

			guild.loadMembers().onSuccess(members -> {
				for (JdbcTeamSignup signup : allSignups) {
					if (signup.isHidden()) {
						continue;
					}
					Member member = members.stream().filter(m -> m.getUser().getName().equalsIgnoreCase(signup.getDiscordName())).findAny().orElse(null);
					if (member != null) {
						WsDiscordUserLinks existingLinks = linksForGuildMembers.stream().filter(link -> link.getDiscordId().equals(member.getId())).findAny().orElse(null);
						if (existingLinks != null) {
							if (existingLinks.getAccountIds().contains(signup.getTrackmaniaUuid())) {
								continue;
							}
						}
						try {
							this.ws.getDiscordDomain().getLinkRequestService().requestPlayerLink(member.getId(), signup.getTrackmaniaUuid());
						} catch (Exception e) {
							log.warn("Failed to request player link", e);
						}
					}
				}
			});
		}

		return resultLines;
	}

	private static List<CSVSignupMember> membersFromSignup(Map<String, String> nicks, String teamName, List<String> resultLines, SignupCSVLine line) throws Exception {
		List<CSVSignupMember> members = new ArrayList<>();
		addSignupMember(1, nicks, teamName, resultLines, members, line.getMember1DiscordName(), line.getMember1TrackmaniaName(), line.getMember1TrackmaniaUserLink(), line.isActive1(), true);
		addSignupMember(2, nicks, teamName, resultLines, members, line.getMember2DiscordName(), line.getMember2TrackmaniaName(), line.getMember2TrackmaniaUserLink(), line.isActive2(), false);
		addSignupMember(3, nicks, teamName, resultLines, members, line.getMember3DiscordName(), line.getMember3TrackmaniaName(), line.getMember3TrackmaniaUserLink(), line.isActive3(), false);
		addSignupMember(4, nicks, teamName, resultLines, members, line.getMember4DiscordName(), line.getMember4TrackmaniaName(), line.getMember4TrackmaniaUserLink(), line.isActive4(), false);
		addSignupMember(5, nicks, teamName, resultLines, members, line.getMember5DiscordName(), line.getMember5TrackmaniaName(), line.getMember5TrackmaniaUserLink(), line.isActive5(), false);
		addSignupMember(6, nicks, teamName, resultLines, members, line.getMember6DiscordName(), line.getMember6TrackmaniaName(), line.getMember6TrackmaniaUserLink(), line.isActive6(), false);
		// extra
		addSignupMember(7, nicks, teamName, resultLines, members, line.getMember7DiscordName(), line.getMember7TrackmaniaName(), line.getMember7TrackmaniaUserLink(), true, true);
		addSignupMember(8, nicks, teamName, resultLines, members, line.getMember8DiscordName(), line.getMember8TrackmaniaName(), line.getMember8TrackmaniaUserLink(), true, false);
		addSignupMember(9, nicks, teamName, resultLines, members, line.getMember9DiscordName(), line.getMember9TrackmaniaName(), line.getMember9TrackmaniaUserLink(), true, false);
		addSignupMember(10, nicks, teamName, resultLines, members, line.getMember10DiscordName(), line.getMember10TrackmaniaName(), line.getMember10TrackmaniaUserLink(), true, false);
		addSignupMember(11, nicks, teamName, resultLines, members, line.getMember11DiscordName(), line.getMember11TrackmaniaName(), line.getMember11TrackmaniaUserLink(), true, false);
		addSignupMember(12, nicks, teamName, resultLines, members, line.getMember12DiscordName(), line.getMember12TrackmaniaName(), line.getMember12TrackmaniaUserLink(), true, false);
		return members;
	}

	private static void addSignupMember(int number, Map<String, String> nicks, String teamName, List<String> resultLines, List<CSVSignupMember> members, String discordName, String trackmaniaName, String trackmaniaUuid, boolean active, boolean captain) throws Exception {
		discordName = nullifyBlank(discordName);
		trackmaniaName = nullifyBlank(trackmaniaName);
		trackmaniaUuid = nullifyBlank(trackmaniaUuid);
		if (discordName == null || trackmaniaName == null || trackmaniaUuid == null) {
			// incomplete captain
			if (captain && number <= 6) {
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
		String tmId = factorTrackmaniaUuid(trackmaniaUuid);
		String name = nicks.get(tmId);
		if (name == null) {
			name = trackmaniaName;
		}
		members.add(new CSVSignupMember(discordName, name, tmId, captain, active, number > 6));
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

	private static String factorTrackmaniaUuid(final String linkInput) throws Exception {
		String link = linkInput;
		if (link.length() == 36) {
			try {
				UUID.fromString(link);
				return link;
			} catch (IllegalArgumentException ex) {
				throw new Exception("Invalid trackmania uuid format", ex);
			}
		}
		if (TMUtils.isValidLogin(link)) {
			try {
				return TMUtils.decodeLoginToAccountId(link).toString();
			} catch (IOException ex) {
				throw new Exception("Invalid TM login " + link, ex);
			}
		}
		if (!link.startsWith("https://trackmania.io/#/player/")) {
			throw new Exception("Invalid trackmania.io link");
		}
		link = link.substring("https://trackmania.io/#/player/".length());
		if (link.contains("/")) {
			link = link.substring(0, link.indexOf("/"));
		}
		if (link.length() == 36) {
			return link;
		} else if (TMUtils.isValidLogin(link)) {
			try {
				return TMUtils.decodeLoginToAccountId(link).toString();
			} catch (IOException ex) {
				throw new Exception("Invalid TM login link " + link, ex);
			}
		} else {
			throw new Exception("Invalid link input: " + linkInput);
		}
	}

	public boolean ensurePlayerRoles(Member discordMember, JdbcMember botMember, Role playerRole, Role teamLeadRole) {
		if (FeatureFlags.DISABLE_ROLE_CHANGES) {
			return false;
		}
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
				if (!FeatureFlags.DONT_REMOVE_CAPTAIN) {
					removeRoles.add(teamLeadRole);
				}
			} else if (!DiscordRoleUtil.hasRole(discordMember, teamLeadRole) && isTeamLead) {
				addRoles.add(teamLeadRole);
			}

			if (FeatureFlags.DONT_REMOVE_CAPTAIN) {
				if (DiscordRoleUtil.hasRole(discordMember, teamLeadRole)) {
					isTeamLead = true;
				}
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
					allTeamDivRoles.removeIf(r -> Objects.equals(r.getId(), teamMemberDivRole.getId()));
					if (!DiscordRoleUtil.hasRole(discordMember, teamMemberDivRole)) {
						addRoles.add(teamMemberDivRole);
					}
				}

				// captain check
				if (isTeamLead) {
					Role teamCaptainDivRole = getTeamCaptainDivRole(playerTeam.getDivision());
					if (teamCaptainDivRole != null) {
						allTeamDivRoles.removeIf(r -> Objects.equals(r.getId(), teamCaptainDivRole.getId()));
						if (!DiscordRoleUtil.hasRole(discordMember, teamCaptainDivRole)) {
							addRoles.add(teamCaptainDivRole);
						}
					}
				}
			}
		}
		// any roles still in allTeamDivRoles are ones the player should not have -- check if it needs to be removed
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
			return bot.getRoleById(roleId);
		}
		return null;
	}

	private Role getTeamCaptainDivRole(int division) {
		String roleId = bot.getConfig().getTeamCaptainDivRole(division);
		if (roleId != null) {
			return bot.getRoleById(roleId);
		}
		return null;
	}

	private record CSVSignupMember(
			String discordName,
			String trackmaniaName,
			String trackmaniaUuid,
			boolean captain,
			boolean active,
			boolean extra
	) {
	}

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
				line[21].trim(),
				// active
				line[22].trim(),
				line[23].trim(),
				line[24].trim(),
				line[25].trim(),
				line[26].trim(),
				line[27].trim(),
				// extra
				line[28].trim(),
				line[29].trim(),
				line[30].trim(),
				line[31].trim(),
				line[32].trim(),
				line[33].trim(),
				line[34].trim(),
				line[35].trim(),
				line[36].trim(),
				line[37].trim(),
				line[38].trim(),
				line[39].trim(),
				line[40].trim(),
				line[41].trim(),
				line[42].trim(),
				line[43].trim(),
				line[44].trim(),
				line[45].trim()
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
		// checkmarks
		private String active1;
		private String active2;
		private String active3;
		private String active4;
		private String active5;
		private String active6;
		// extra
		private String member7DiscordName;
		private String member7TrackmaniaName;
		private String member7TrackmaniaUserLink;
		private String member8DiscordName;
		private String member8TrackmaniaName;
		private String member8TrackmaniaUserLink;
		private String member9DiscordName;
		private String member9TrackmaniaName;
		private String member9TrackmaniaUserLink;
		private String member10DiscordName;
		private String member10TrackmaniaName;
		private String member10TrackmaniaUserLink;
		private String member11DiscordName;
		private String member11TrackmaniaName;
		private String member11TrackmaniaUserLink;
		private String member12DiscordName;
		private String member12TrackmaniaName;
		private String member12TrackmaniaUserLink;

		public boolean isActive1() {
			return Boolean.parseBoolean(active1);
		}

		public boolean isActive2() {
			return Boolean.parseBoolean(active2);
		}

		public boolean isActive3() {
			return Boolean.parseBoolean(active3);
		}

		public boolean isActive4() {
			return Boolean.parseBoolean(active4);
		}

		public boolean isActive5() {
			return Boolean.parseBoolean(active5);
		}

		public boolean isActive6() {
			return Boolean.parseBoolean(active6);
		}
	}

}
