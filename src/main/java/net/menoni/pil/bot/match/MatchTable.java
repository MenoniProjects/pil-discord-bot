package net.menoni.pil.bot.match;

import net.menoni.spring.commons.service.CsvService;
import net.menoni.spring.commons.util.DiscordTableBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class MatchTable {

	public static String teamsRanked(Match match, EnumSet<MatchTableColumn> columns) {
		DiscordTableBuilder t = new DiscordTableBuilder(columns.stream().map(MatchTableColumn::getHeader).toList());

		for (Map.Entry<String, Integer> e : match.getTeamScoresRanked().entrySet()) {
			String teamName = e.getKey();
			List<Object> row = new ArrayList<>();
			for (MatchTableColumn column : columns) {
				row.add(getRowValue(column, match, teamName));
			}
			t.addRow(row);
		}

		return t.build();
	}

	public static byte[] teamsRankedCsv(CsvService csvService, Match match, EnumSet<MatchTableColumn> columns) throws IOException {
		String[] headers = columns.stream().map(MatchTableColumn::getHeader).toArray(String[]::new);
		List<Object[]> lines = new ArrayList<>();
		for (Map.Entry<String, Integer> e : match.getTeamScoresRanked().entrySet()) {
			String teamName = e.getKey();
			List<String> row = new ArrayList<>();
			for (MatchTableColumn column : columns) {
				row.add(getRowValue(column, match, teamName).toString());
			}
			lines.add(row.toArray());
		}
		return csvService.create(headers, lines);
	}

	private static Object getRowValue(
			MatchTableColumn column,
			Match match,
			String team
	) {
		return column.getExtractor().apply(match, team);
	}
}
