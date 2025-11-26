package net.menoni.pil.bot.jdbc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JdbcFeedbackCategory {

	private String key;
	private String mapperCategoryId;
	private String feedbackCategoryId;
	private String feedbackRoleId;

}
