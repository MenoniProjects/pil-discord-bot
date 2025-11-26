package net.menoni.pil.bot.jdbc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JdbcFeedbackChannelMember {

	private Long feedbackChannelId;
	private Integer number;
	private String userId;

}
