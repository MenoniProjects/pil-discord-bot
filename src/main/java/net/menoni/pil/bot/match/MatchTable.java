package net.menoni.pil.bot.match;

import com.opencsv.CSVWriter;
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

	public static byte[] playersRankedCsv(Match match, EnumSet<MatchTableColumn> columns) throws IOException {
		ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(fileBytes);
		CSVWriter w = new CSVWriter(writer);
		w.writeNext(columns.stream().map(MatchTableColumn::getHeader).toArray(String[]::new));

		for (MatchPlayer player : match.getPlayers()) {
			List<String> row = new ArrayList<>();
			for (MatchTableColumn column : columns) {
				row.add(getRowValue(column, match, player).toString());
			}

			w.writeNext(row.toArray(String[]::new));
		}

		writer.flush();
		byte[] data = fileBytes.toByteArray();
		writer.close();

		return data;
	}

	private static Object getRowValue(
			MatchTableColumn column,
			Match match,
			MatchPlayer player
	) {
		return column.getExtractor().apply(match, player);
	}
}
