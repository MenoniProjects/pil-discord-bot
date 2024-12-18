package net.menoni.pil.bot.service;

import net.dv8tion.jda.api.entities.Member;
import net.menoni.pil.bot.jdbc.model.JdbcMember;
import net.menoni.pil.bot.jdbc.model.JdbcTeamSignup;
import net.menoni.pil.bot.jdbc.repository.MemberRepository;
import net.menoni.pil.bot.jdbc.repository.TeamSignupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberService {

    @Autowired private MemberRepository memberRepository;
	@Autowired private TeamSignupRepository signupRepository;

	public JdbcMember getOrCreateMember(Member member) {
		if (member == null) {
			return null;
		}
		if (member.getUser().isBot() || member.getUser().isSystem()) {
			return null;
		}
		JdbcMember existingMember = getExistingMember(member.getId());
		if (existingMember == null) {
			JdbcTeamSignup signupForMember = signupRepository.getSignupForMember(member);
			JdbcMember jdbcMember = new JdbcMember(
					null,
					member.getId(),
					member.getUser().getName(),
					member.getEffectiveName(),
					signupForMember == null ? null : signupForMember.getTeamId()
			);
			existingMember = this.memberRepository.saveMember(jdbcMember);
			// none found, new created & returned without links
		} else {
			existingMember = updateJdbcMemberFromDiscord(existingMember, member);
		}
		return existingMember;
	}

	public JdbcMember getExistingMember(String memberId) {
		if (memberId == null) {
			return null;
		}
		return this.memberRepository.getMemberByDiscordId(memberId);
	}

	private JdbcMember updateJdbcMemberFromDiscord(JdbcMember jdbcMember, Member member) {
		// found match does not match name or nick, update & save
		if (!member.getEffectiveName().equals(jdbcMember.getDiscordNick()) ||
				!member.getUser().getName().equals(jdbcMember.getDiscordName())) {
			jdbcMember.setDiscordName(member.getUser().getName());
			jdbcMember.setDiscordNick(member.getEffectiveName());
			jdbcMember = this.memberRepository.saveMember(jdbcMember);
		}
		return jdbcMember;
	}

	public List<JdbcMember> getForTeam(Long teamId) {
		return this.memberRepository.getMembersByTeam(teamId);
	}

	public void deleteMember(String id) {
		this.memberRepository.deleteMember(id);
	}

	public void updateMember(JdbcMember jdbcMember) {
		this.memberRepository.saveMember(jdbcMember);
	}

	public List<JdbcMember> getAll() {
		return this.memberRepository.getAll();
	}

	public void removeTeam(Long teamId) {
		this.memberRepository.removeTeam(teamId);
	}

}
