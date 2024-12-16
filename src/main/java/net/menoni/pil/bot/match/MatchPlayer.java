package net.menoni.pil.bot.match;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MatchPlayer implements Comparable<MatchPlayer> {

	private String playerId;
	private String playerName;
	private String teamName;
	private Integer points;

	@Override
	public int compareTo(@NotNull MatchPlayer o) {
		int i = Integer.compare(o.points, this.points);
		if (i != 0) {
			return i;
		}
		// FIXME: check what official tiebreaker rules are
		// FIXME: this just prevents losing people with equal points
		return playerId.compareTo(o.playerId);
	}
}
