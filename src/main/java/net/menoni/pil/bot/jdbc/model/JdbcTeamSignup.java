package net.menoni.pil.bot.jdbc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JdbcTeamSignup {

	private Long id;
	private Long teamId;
	private String discordName;
	private String trackmaniaName;
	private String trackmaniaUuid;
	private boolean teamLead;
	private boolean hidden;
	private boolean archived;

}
