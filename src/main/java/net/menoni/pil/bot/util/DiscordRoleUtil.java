package net.menoni.pil.bot.util;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class DiscordRoleUtil {

	public static boolean hasRole(Member guildMember, Role guildRole) {
		return hasRole(guildMember, guildRole.getId());
	}

	public static boolean hasRole(Member guildMember, String guildRoleId) {
		for (Role role : guildMember.getRoles()) {
			if (role.getId().equals(guildRoleId)) {
				return true;
			}
		}
		return false;
	}

}
