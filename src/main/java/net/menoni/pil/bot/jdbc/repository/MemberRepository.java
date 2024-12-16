package net.menoni.pil.bot.jdbc.repository;

import net.menoni.spring.commons.jdbc.AbstractTypeRepository;
import net.menoni.pil.bot.jdbc.model.JdbcMember;
import net.menoni.spring.commons.util.NullableMap;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Repository
public class MemberRepository extends AbstractTypeRepository<JdbcMember> {

    public List<JdbcMember> getMembersByTeam(Long teamId) {
        return this.queryMany("SELECT id, discord_id, discord_name, discord_nick, team_id FROM member WHERE team_id = ?", teamId);
    }

    public JdbcMember getMemberByDiscordId(String discordId) {
        return this.queryOne(
                "SELECT id, discord_id, discord_name, discord_nick, team_id FROM member WHERE discord_id = ?",
                discordId
        );
    }

    public JdbcMember getById(Long id) {
        return this.queryOne(
                "SELECT id, discord_id, discord_name, discord_nick, team_id FROM member WHERE id = ?",
                id
        );
    }

    public boolean deleteMember(String discordId) {
        return this.update(
                "DELETE FROM member WHERE discord_id = :discordId",
                Map.of("discordId", discordId)
        ) > 0;
    }

    public JdbcMember saveMember(JdbcMember member) {
        NullableMap props = NullableMap.create()
                .add("discordId", member.getDiscordId())
                .add("discordName", member.getDiscordName())
                .add("discordNick", member.getDiscordNick())
                .add("teamId", member.getTeamId());
        if (member.getId() == null) {
            return this.insertMember(member, props);
        }
        this.update(
                "UPDATE member SET discord_name = :discordName, discord_nick = :discordNick, team_id = :teamId WHERE discord_id = :discordId",
                props
        );
        return member;
    }

    private JdbcMember insertMember(JdbcMember member, Map<String, Object> props) {
        GeneratedKeyHolder res = this.insert(
                "INSERT INTO member (discord_id, discord_name, discord_nick, team_id) VALUES (:discordId, :discordName, :discordNick, :teamId)",
                props
        );
        member.setId(res.getKeyAs(BigInteger.class).longValue());
        return member;
    }

    public List<JdbcMember> getAll() {
        return this.queryMany(
                "SELECT id, discord_id, discord_name, discord_nick, team_id FROM member"
        );
    }
}
