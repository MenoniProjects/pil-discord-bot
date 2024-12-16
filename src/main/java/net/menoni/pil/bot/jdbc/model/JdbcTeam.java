package net.menoni.pil.bot.jdbc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JdbcTeam {

	private Long id;
	private String name;
	private String color;
	private String imageUrl;
	private String discordRoleId;
	private String emoteName;
	private String emoteId;
	private Integer division;

}
