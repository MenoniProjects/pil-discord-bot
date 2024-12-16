package net.menoni.pil.bot.match;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MatchRound implements Comparable<MatchRound> {

	private Integer roundNumber;
	private Set<MatchRoundPlayerFinish> finishers;

	@Override
	public int compareTo(@NotNull MatchRound o) {
		return Integer.compare(this.roundNumber, o.roundNumber);
	}
}
