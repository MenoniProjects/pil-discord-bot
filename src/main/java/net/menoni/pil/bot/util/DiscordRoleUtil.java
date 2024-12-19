package net.menoni.pil.bot.util;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.restaction.order.RoleOrderAction;

import java.util.List;

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

	public static RoleOrderAction createRoleOrderForRangeAction(Guild g, Role aboveRole, List<Role> sortedRoles) {
		if (sortedRoles.isEmpty() || g == null || aboveRole == null) {
			return null;
		}

		RoleOrderAction roleOrdering = g.modifyRolePositions();

		roleOrdering.selectPosition(sortedRoles.get(0)).moveBelow(aboveRole);

		if (sortedRoles.size() > 1) {
			for (int i = 1; i < sortedRoles.size(); i++) {
				Role roleAbove = sortedRoles.get(i - 1);
				Role role = sortedRoles.get(i);
				roleOrdering.selectPosition(role).moveBelow(roleAbove);
			}
		}

		return roleOrdering;
	}

}