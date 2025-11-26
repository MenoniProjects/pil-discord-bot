package net.menoni.pil.bot.jdbc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JdbcFeedbackChannel {

	private Long id;
	private String mapperUserId;
	private String mapperChannelId;
	private String feedbackChannelId;

}
