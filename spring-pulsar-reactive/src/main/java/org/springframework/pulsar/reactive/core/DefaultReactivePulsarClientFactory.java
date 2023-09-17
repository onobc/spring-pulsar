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

package org.springframework.pulsar.reactive.core;

import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.log.LogAccessor;
import org.springframework.pulsar.core.PulsarClientFactory;
import org.springframework.pulsar.core.RestartableSingletonFactoryBase;

/**
 * Default implementation for {@link ReactivePulsarClientFactory}.
 *
 * @author Chris Bono
 */
public class DefaultReactivePulsarClientFactory extends RestartableSingletonFactoryBase<ReactivePulsarClient>
		implements ReactivePulsarClientFactory, ApplicationContextAware, InitializingBean {

	private final LogAccessor logger = new LogAccessor(this.getClass());

	private PulsarClientFactory pulsarClientFactory;

	public DefaultReactivePulsarClientFactory(PulsarClientFactory pulsarClientFactory) {
		this.pulsarClientFactory = pulsarClientFactory;
	}

	@Override
	public ReactivePulsarClient createClient() {
		return this.getInstance();
	}

	@Override
	public int getPhase() {
		return (Integer.MIN_VALUE / 2) - 100;
	}

	@Override
	protected ReactivePulsarClient createInstance() {
		this.logger.warn(() -> "Creating client...");
		try {
			var pulsarClient = this.pulsarClientFactory.createClient();
			return AdaptedReactivePulsarClientFactory.create(pulsarClient);
		}
		catch (PulsarClientException e) {
			throw new RuntimeException(e);
		}
	}


	// TODO ---- REMOVE THIS
	private ApplicationContext applicationContext;
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
	@Override
	public void afterPropertiesSet() throws Exception {
		if (this.applicationContext != null) {
			this.pulsarClientFactory = this.applicationContext.getBean(PulsarClientFactory.class);
			this.logger.warn(() -> "Initialized w/ " + this.pulsarClientFactory);
		}
	}
}
