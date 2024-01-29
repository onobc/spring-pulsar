/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.pulsar.core;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.lang.Nullable;

/**
 * A resolved value or an exception if it could not be resolved.
 *
 * @param <T> the resolved type
 * @author Christophe Bornet
 */
public final class Resolved<T> {

	@Nullable
	private final T value;

	@Nullable
	private final RuntimeException exception;

	private Resolved(@Nullable T value, @Nullable RuntimeException exception) {
		this.value = value;
		this.exception = exception;
	}

	public static <T> Resolved<T> of(T value) {
		return new Resolved<>(value, null);
	}

	public static <T> Resolved<T> failed(String reason) {
		return new Resolved<>(null, new IllegalArgumentException(reason));
	}

	public static <T> Resolved<T> failed(RuntimeException e) {
		return new Resolved<>(null, e);
	}

	/**
	 * Gets the optional resolved value
	 * @return an optional with the resolved value or empty if failed to resolve
	 * @deprecated use {@link Resolved#value())} instead
	 */
	@Deprecated(since = "1.1.0", forRemoval = true)
	public Optional<T> get() {
		return value();
	}

	/**
	 * Gets the resolved value.
	 * @return an optional with the resolved value or empty if failed to resolve
	 */
	public Optional<T> value() {
		return Optional.ofNullable(this.value);
	}

	/**
	 * Gets the exception that may have occurred during resolution.
	 * @return an optional with the resolution exception or empty if no error occurred
	 */
	public Optional<RuntimeException> exception() {
		return Optional.ofNullable(this.exception);
	}

	/**
	 * Performs the given action with the resolved value if a value was resolved and no
	 * exception occurred.
	 * @param action the action to be performed
	 */
	public void ifResolved(Consumer<? super T> action) {
		if (this.value != null) {
			action.accept(this.value);
		}
	}

	public T orElseThrow() {
		if (this.value == null && this.exception != null) {
			throw this.exception;
		}
		return this.value;
	}

	public T orElseThrow(Supplier<String> wrappingErrorMessage) {
		if (this.value == null && this.exception != null) {
			throw new RuntimeException(wrappingErrorMessage.get(), this.exception);
		}
		return this.value;
	}

}
