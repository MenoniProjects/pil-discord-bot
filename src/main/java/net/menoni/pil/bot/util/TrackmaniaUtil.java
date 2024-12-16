package net.menoni.pil.bot.util;

public class TrackmaniaUtil {

	public static String formatRecordTime(long recordTime) {
		if (recordTime <= 0) {
			return "NaN";
		}

		int hours = 0;
		int minutes = 0;
		int seconds = 0;

		while (recordTime >= (1000L * 60 * 60)) {
			recordTime -= (1000L * 60 * 60);
			hours++;
		}
		while (recordTime >= (1000L * 60)) {
			recordTime -= (1000L * 60);
			minutes++;
		}
		while (recordTime >= (1000L)) {
			recordTime -= 1000L;
			seconds++;
		}

		if (hours > 0) {
			return String.format("%d:%02d:%02d:%03d", hours, minutes, seconds, recordTime);
		}
		return String.format("%d:%02d:%03d", minutes, seconds, recordTime);
	}

}
