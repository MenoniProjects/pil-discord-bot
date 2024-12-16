package net.menoni.pil.bot.jdbc.repository;

import net.menoni.pil.bot.jdbc.model.JdbcSystemMessage;
import net.menoni.spring.commons.jdbc.AbstractTypeRepository;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class SystemMessageRepository extends AbstractTypeRepository<JdbcSystemMessage> {

	public JdbcSystemMessage getSystemMessage(String key) {
		return this.queryOne("SELECT id, `key`, channel_id, message_id FROM system_message WHERE `key` = ?", key);
	}

	public JdbcSystemMessage saveSystemMessage(JdbcSystemMessage message) {
		if (message.getId() == null) {
			GeneratedKeyHolder key = this.insertOne("INSERT INTO system_message (`key`, channel_id, message_id) VALUES (:key, :channelId, :messageId)", message);
			message.setId(key.getKey().longValue());
		} else {
			this.update(
					"UPDATE system_message SET channel_id = :channelId, message_id = :messageId WHERE id = :id",
					Map.of(
							"channelId", message.getChannelId(),
							"messageId", message.getMessageId(),
							"id", message.getId()
					)
			);
		}
		return message;
	}

	public void deleteSystemMessage(String channelId, String messageId) {
		this.update(
				"DELETE FROM system_message WHERE channel_id = :channelId AND message_id = :messageId",
				Map.of("channelId", channelId, "messageId", messageId)
		);
	}

}
