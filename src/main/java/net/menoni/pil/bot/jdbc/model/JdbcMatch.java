package net.menoni.pil.bot.jdbc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class JdbcMatch {

	private Long id;
	private Integer division;
	private Integer roundNumber;
	private Long firstTeamId;
	private Long secondTeamId;
	private String matchChannelId;
	private Long winTeamId; // nullable
	private Integer winTeamScore; // nullable
	private Integer loseTeamScore; // nullable

}
