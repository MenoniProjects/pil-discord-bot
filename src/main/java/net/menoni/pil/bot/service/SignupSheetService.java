package net.menoni.pil.bot.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.menoni.pil.bot.discord.DiscordBot;
import net.menoni.pil.bot.util.JDAUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SignupSheetService {

	private final RestTemplate template = new RestTemplateBuilder()
			.messageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
			.build();

	@Autowired
	private DiscordBot bot;

	@Autowired
	private TeamService teamService;

	// key part of edit url
	@Value("${pil.signups.sheet.key:}")
	private String signupsSheetKey;

	@PostConstruct
	public void testSheetTabPresence() {
		if (this.signupsSheetKey == null || this.signupsSheetKey.isBlank()) {
			log.warn("Missing signups sheet key");
		}
	}

	@Scheduled(cron = "0 0 * * * *")
	public void signupSheetImportScheduling() {
		log.info("Starting hourly signup-sheet-import");
		try {
			_runSignupsSheetImport();
		} catch (IOException | CsvException e) {
			log.error("scheduled sheet import failed", e);
		}
	}

	private void _runSignupsSheetImport() throws IOException, CsvException {
		List<String[]> lines = getSignupsCsv();
		if (lines == null) {
			log.info("automatic sign-up sheet import aborted - no sheet contents");
			return;
		}
		List<String> resultLines = teamService.importCsv(lines);
		if (resultLines.isEmpty()) {
			log.info("automatic sign-up sheet import finished without changes");
			return;
		}
		TextChannel channel = bot.getTextChannelById(bot.getConfig().getCmdChannelId());
		if (channel != null) {
			JDAUtil.queueAndWait(channel.sendMessage("### automatic hourly import job:\n" + String.join("\n", resultLines)));
		}
	}

	private boolean isEnabled() {
		return this.signupsSheetKey != null && !this.signupsSheetKey.isBlank();
	}

	private String getSheetCsvUrl() {
		if (!this.isEnabled()) {
			return null;
		}
		return "https://docs.google.com/spreadsheets/d/%s/export?format=csv".formatted(this.signupsSheetKey);
	}

	private List<String[]> getSignupsCsv() throws IOException, CsvException {
		if (!this.isEnabled()) {
			return null;
		}
		String url = getSheetCsvUrl();
		if (url == null) {
			return null;
		}

		ResponseEntity<String> csvEntity = template.getForEntity(url, String.class);
		if (csvEntity.getStatusCode().isError()) {
			log.error("sheet error: {}", csvEntity.getStatusCode().value());
			return null;
		}
		if (csvEntity.getBody() == null) {
			return new ArrayList<>();
		}

		String body = csvEntity.getBody();
		CSVReader reader = new CSVReader(new StringReader(body));
		List<String[]> result = reader.readAll();
		reader.close();
		if (result != null && result.size() > 0) {
			result.remove(0);
		}
		return result;
	}

}
