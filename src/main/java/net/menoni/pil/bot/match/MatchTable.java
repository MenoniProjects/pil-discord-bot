package net.menoni.pil.bot.match;

import net.menoni.spring.commons.service.CsvService;
import net.menoni.spring.commons.util.DiscordTableBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class MatchTable {

	public static String playersRanked(Match match, EnumSet<MatchTableColumn> columns) {
		DiscordTableBuilder t = new DiscordTableBuilder(columns.stream().map(MatchTableColumn::getHeader).toList());

		for (MatchPlayer player : match.getPlayers()) {
			List<Object> row = new ArrayList<>();
			for (MatchTableColumn column : columns) {
				row.add(getRowValue(column, match, player));
			}
			t.addRow(row);
		}

		return t.build();
	}

	public static byte[] playersRankedCsv(CsvService csvService, Match match, EnumSet<MatchTableColumn> columns) throws IOException {
		String[] headers = columns.stream().map(MatchTableColumn::getHeader).toArray(String[]::new);
		List<Object[]> lines = new ArrayList<>();
		for (MatchPlayer player : match.getPlayers()) {
			List<String> row = new ArrayList<>();
			for (MatchTableColumn column : columns) {
				row.add(getRowValue(column, match, player).toString());
			}

			lines.add(row.toArray());
		}
		return csvService.create(headers, lines);
	}

	private static Object getRowValue(
			MatchTableColumn column,
			Match match,
			MatchPlayer player
	) {
		return column.getExtractor().apply(match, player);
	}
}
