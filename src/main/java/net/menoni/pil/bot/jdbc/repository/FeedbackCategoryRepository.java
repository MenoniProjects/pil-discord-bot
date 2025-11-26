package net.menoni.pil.bot.jdbc.repository;

import net.menoni.pil.bot.jdbc.model.JdbcFeedbackCategory;
import net.menoni.spring.commons.jdbc.AbstractTypeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FeedbackCategoryRepository extends AbstractTypeRepository<JdbcFeedbackCategory> {

	public List<JdbcFeedbackCategory> findAll() {
		return this.queryMany(
				"SELECT `key`, mapper_category_id, feedback_category_id, feedback_role_id FROM feedback_category"
		);
	}

}
