package net.menoni.pil.bot.script;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScriptExecutor {

	@Getter private IScript script = null;

	@Autowired private ApplicationContext applicationContext;

	// scripts

	@PostConstruct
	public void init() {
		script = null;

		if (script == null) {
			return;
		}

		log.info("Waiting 2s to start {}", script.getClass().getSimpleName());
		new Thread(() -> {
			try {
				Thread.sleep(2_000L);
			} catch (InterruptedException e) {
				log.error("Failed to wait 5s for script to start");
			}
			this.executeScript();
		}).start();

	}

	private void executeScript() {
		log.info("Running {}", script.getClass().getName());
		try {
			script.run();
		} catch (Exception e) {
			log.error("Script execution failed", e);
		}
		log.info("Exiting app");
		SpringApplication.exit(applicationContext, () -> 0);
		System.exit(0);
	}

}
