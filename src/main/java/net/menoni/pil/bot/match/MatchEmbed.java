package net.menoni.pil.bot.match;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

public class MatchEmbed {

	public static MessageEmbed top10(Match match) {
		StringBuilder sb = new StringBuilder();

		int r = 0;
		for (MatchPlayer player : match.getPlayers()) {
			sb
					.append("%d. ".formatted(r+1))
					.append("[%s](https://trackmania.io/#/player/%s) ".formatted(player.getPlayerName(), player.getPlayerId()))
					.append("**(%d)**".formatted(player.getPoints()));
			if (player.getTeamName() != null && !player.getTeamName().isBlank()) {
				sb.append(" [`%s`]".formatted(player.getTeamName()));
			}
			sb.append("\n");
			r++;
			if (r >= 10) {
				break;
			}
		}

		EmbedBuilder eb = new EmbedBuilder();

		eb.setTitle("Match - Top %d".formatted(r));
		eb.setDescription(sb.toString());

		return eb.build();
	}

}
