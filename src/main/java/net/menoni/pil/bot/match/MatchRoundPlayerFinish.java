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
public class MatchRoundPlayerFinish implements Comparable<MatchRoundPlayerFinish> {

	private Long time;
	private String trackName;
	private String playerId;
	private Long recordTime;
	private Integer roundNumber;
	private Integer score;

	@Override
	public int compareTo(@NotNull MatchRoundPlayerFinish o) {
		int i = Integer.compare(this.roundNumber, o.roundNumber);
		if (i != 0) {
			return i;
		}
		return Integer.compare(o.score, this.score);
	}
}
