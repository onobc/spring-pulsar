/*
 * Copyright 2022-2023 the original author or authors.
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.log.LogAccessor;
import org.springframework.lang.Nullable;

/**
 * Provides a simple base implementation for a restartable singleton factory.
 * <p>It is restartable in the sense that it can be stopped and then started and
 * still be in a usable state.
 * <p>Because it releases its resources when {@link SmartLifecycle#stop() stopped} and
 * re-acquires them when subsequently {@link SmartLifecycle#start() started}, it can
 * also be used as a base implementation for coordinated checkpoint and restore.
 *
 * @param <T> the bean type
 * @author Chris Bono
 */
public abstract class RestartableSingletonFactoryBase<T> implements SmartLifecycle, InitializingBean, DisposableBean {

	private final LogAccessor logger = new LogAccessor(this.getClass());

	private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);

	private T instance;

	protected RestartableSingletonFactoryBase() {
	}

	protected RestartableSingletonFactoryBase(T instance) {
		this.instance = instance;
	}

	/**
	 * Lifecycle state of this factory.
	 */
	enum State {

		CREATED, STARTING, STARTED, STOPPING, STOPPED, DESTROYED;

	}

	@Override
	public void afterPropertiesSet() throws Exception {
		ensureInstanceCreated();
	}

	@Override
	public boolean isRunning() {
		return State.STARTED.equals(this.state.get());
	}

	protected State currentState() {
		return this.state.get();
	}

	@Override
	public void start() {
		State current = this.state.getAndUpdate(state -> isCreatedOrStopped(state) ? State.STARTING : state);
		if (isCreatedOrStopped(current)) {
			this.logger.warn(() -> "Starting...");
			ensureInstanceCreated();
			doStart();
			this.state.set(State.STARTED);
			this.logger.warn(() -> "Started");
		}
	}

	private static boolean isCreatedOrStopped(@Nullable State state) {
		return State.CREATED.equals(state) || State.STOPPED.equals(state);
	}

	private void ensureInstanceCreated() {
		if (this.instance == null) {
			this.logger.warn(() -> "Creating instance...");
			this.instance = createInstance();
		}
	}

	protected void doStart() {
	}

	@Override
	public void stop() {
		State current = this.state.getAndUpdate(state -> isCreatedOrStarted(state) ? State.STOPPING : state);
		if (isCreatedOrStarted(current)) {
			this.logger.warn(() -> "Stopping...");
			doStop();
			ensureInstanceDestroyed();
			this.state.set(State.STOPPED);
			this.logger.warn(() -> "Stopped");
		}
	}

	private static boolean isCreatedOrStarted(@Nullable State state) {
		return State.CREATED.equals(state) || State.STARTED.equals(state);
	}

	protected void doStop() {
	}

	private void ensureInstanceDestroyed() {
		if (this.instance != null) {
			this.logger.warn(() -> "Destroying instance...");
			destroyInstance(this.instance);
			this.instance = null;
		}
	}

	@Override
	public void destroy() {
		this.logger.warn(() -> "Destroying");
		stop();
		this.state.set(State.DESTROYED);
		this.logger.warn(() -> "Destroyed");
	}

	/**
	 * Gets the singleton instance.
	 * @return the singleton instance
	 */
	public final T getInstance() {
		return this.instance;
	}

	/**
	 * Template method that subclasses must override to construct the backing singleton
	 * instance returned by this factory.
	 * <p>
	 * Implementations should throw a {@link RuntimeException} if an error occurs during
	 * creation.
	 * <p>
	 * Invoked on {@link #afterPropertiesSet() initialization} of this bean if the
	 * instance has not already been set via the constructor OR during {@link #start()} if
	 * the instance is null.
	 * @return the single object managed by the factory
	 */
	protected abstract T createInstance();

	/**
	 * Callback for destroying the singleton instance. Subclasses may override this to
	 * destroy the previously created instance.
	 * <p>
	 * Implementations should throw a {@link RuntimeException} if an error occurs during
	 * destruction.
	 * <p>
	 * The default implementation is empty.
	 * @param instance the singleton instance, as returned by {@link #createInstance()}
	 */
	protected void destroyInstance(@Nullable T instance) {
	}

}
