package net.menoni.pil.bot.jdbc.repository;

import net.menoni.pil.bot.jdbc.model.JdbcFeedbackCategory;
import net.menoni.pil.bot.jdbc.model.JdbcFeedbackChannel;
import net.menoni.spring.commons.jdbc.AbstractTypeRepository;
import net.menoni.spring.commons.util.NullableMap;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class FeedbackChannelRepository extends AbstractTypeRepository<JdbcFeedbackChannel> {

	public JdbcFeedbackChannel create(String mapperId) {
		GeneratedKeyHolder keyHolder = this.insert(
				"INSERT INTO feedback_channel (mapper_user_id) VALUES (:mapperId)",
				Map.of("mapperId", mapperId)
		);
		return new JdbcFeedbackChannel(
				keyHolder.getKey().longValue(),
				mapperId,
				null,
				null
		);
	}

	public void updateChannels(JdbcFeedbackChannel feedbackChannelData) {
		this.update(
				"UPDATE feedback_channel SET mapper_channel_id = :mapperChannelId, feedback_channel_id = :feedbackChannelId WHERE id = :id",
				NullableMap.create()
						.add("mapperChannelId", feedbackChannelData.getMapperChannelId())
						.add("feedbackChannelId", feedbackChannelData.getFeedbackChannelId())
						.add("id", feedbackChannelData.getId())
		);
	}

	public JdbcFeedbackChannel findByChannel(String channelId) {
		return this.queryOne(
				"SELECT id, mapper_user_id, mapper_channel_id, feedback_channel_id FROM feedback_channel WHERE mapper_channel_id = ? OR feedback_channel_id = ?",
				channelId, channelId
		);
	}

}
