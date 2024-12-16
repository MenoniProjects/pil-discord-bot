package net.menoni.pil.bot.util;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
public class JDAUtil {

	public static <T> CompletableFuture<T> completableFutureQueue(RestAction<T> action) {
		CompletableFuture<T> future = new CompletableFuture<>();
		action.queue(future::complete, future::completeExceptionally);
		return future;
	}

	public static <T> T queueAndWait(RestAction<T> action, Consumer<Throwable> errorHandler) {
		try {
			return completableFutureQueue(action).join();
		} catch (Throwable e) {
			if (errorHandler != null) {
				errorHandler.accept(e);
			}
			return null;
		}
	}

	public static <T> T queueAndWait(RestAction<T> action) {
		return queueAndWait(action, (e) -> {
			log.error("rest-action %s error".formatted(action.getClass().getName()), e);
		});
	}

}
