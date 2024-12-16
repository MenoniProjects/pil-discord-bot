package net.menoni.pil.bot.util;

import java.time.Duration;

public class TemporaryValue<T> {

	private final Duration ttl;
	private long invalidateValueAt;
	private T value;

	public static <T> TemporaryValue<T> of(T value, Duration ttl) {
		return new TemporaryValue<>(value, ttl);
	}

	public static <T> TemporaryValue<T> empty(Duration ttl) {
		return new TemporaryValue<>(null, ttl);
	}

	private TemporaryValue(T value, Duration ttl) {
		this.value = value;
		this.ttl = ttl;
		this._onValueSet();
	}

	public T getValue() {
		this._check();
		return value;
	}

	public void setValue(T value) {
		this.value = value;
		this._onValueSet();
	}

	public boolean isPresent() {
		return getValue() != null;
	}

	private void _onValueSet() {
		if (this.value == null) {
			this.invalidateValueAt = -1;
		} else {
			this.invalidateValueAt = System.currentTimeMillis() + ttl.toMillis();
		}
	}

	private void _check() {
		if (System.currentTimeMillis() >= invalidateValueAt) {
			this.setValue(null);
		}
	}

}
