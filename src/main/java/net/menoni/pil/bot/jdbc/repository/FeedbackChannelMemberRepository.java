package net.menoni.pil.bot.jdbc.repository;

import net.menoni.pil.bot.jdbc.model.JdbcFeedbackChannelMember;
import net.menoni.spring.commons.jdbc.AbstractTypeRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class FeedbackChannelMemberRepository extends AbstractTypeRepository<JdbcFeedbackChannelMember> {

	public synchronized int getFeedbackUserNumber(Long feedbackChannelDataId, String userId) {
		Integer num = this.queryOneOfPrimitive(
				"SELECT number FROM feedback_channel_member WHERE feedback_channel_id = ? AND user_id = ?",
				Integer.class,
				feedbackChannelDataId,
				userId
		);

		if (num != null) {
			return num;
		}

		int next = this.queryOneOfPrimitive("SELECT IFNULL(MAX(number)+1, 1) FROM feedback_channel_member", Integer.class);
		this.update(
				"INSERT INTO feedback_channel_member (feedback_channel_id, number, user_id) VALUES (:feedbackChannelDataId, :number, :userId)",
				Map.of(
						"feedbackChannelDataId", feedbackChannelDataId,
						"number", next,
						"userId", userId
				)
		);
		return next;
	}

}
