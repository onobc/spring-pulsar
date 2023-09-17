/*
 * Copyright 2023 the original author or authors.
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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Reader;
import org.apache.pulsar.client.api.ReaderBuilder;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.ReaderBuilderImpl;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.log.LogAccessor;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

/**
 * Default implementation of {@link PulsarReaderFactory}.
 *
 * @param <T> message type
 * @author Soby Chacko
 */
public class DefaultPulsarReaderFactory<T> extends RestartableSingletonFactoryBase<PulsarClient>
		implements PulsarReaderFactory<T>, ApplicationContextAware, InitializingBean {

	private final LogAccessor logger = new LogAccessor(this.getClass());

	private PulsarClientFactory pulsarClientFactory;

	@Nullable
	private final List<ReaderBuilderCustomizer<T>> defaultConfigCustomizers;

	/**
	 * Construct a reader factory instance with no default configuration.
	 * @param pulsarClient the client used to consume
	 */
	public DefaultPulsarReaderFactory(PulsarClient pulsarClient) {
		this(pulsarClient, null);
	}

	/**
	 * Construct a reader factory instance.
	 * @param pulsarClientFactory the factory to create the client used to consume
	 * @param defaultConfigCustomizers the optional list of customizers to apply to the
	 * readers or null to use no default configuration
	 */
	public DefaultPulsarReaderFactory(PulsarClientFactory pulsarClientFactory,
			@Nullable List<ReaderBuilderCustomizer<T>> defaultConfigCustomizers) {
		this.pulsarClientFactory = pulsarClientFactory;
		this.defaultConfigCustomizers = defaultConfigCustomizers;
	}

	/**
	 * Construct a reader factory instance.
	 * @param pulsarClient the client used to consume
	 * @param defaultConfigCustomizers the optional list of customizers to apply to the
	 * readers or null to use no default configuration
	 */
	public DefaultPulsarReaderFactory(PulsarClient pulsarClient,
			@Nullable List<ReaderBuilderCustomizer<T>> defaultConfigCustomizers) {
		super(pulsarClient);
		this.defaultConfigCustomizers = defaultConfigCustomizers;
	}

	@Override
	public Reader<T> createReader(@Nullable List<String> topics, @Nullable MessageId messageId, Schema<T> schema,
			@Nullable List<ReaderBuilderCustomizer<T>> customizers) throws PulsarClientException {
		Objects.requireNonNull(schema, "Schema must be specified");
		ReaderBuilder<T> readerBuilder = this.getInstance().newReader(schema);

		// Apply the default config customizer (preserve the topics)
		if (!CollectionUtils.isEmpty(this.defaultConfigCustomizers)) {
			this.defaultConfigCustomizers.forEach((customizer -> customizer.customize(readerBuilder)));
		}

		if (!CollectionUtils.isEmpty(topics)) {
			replaceTopicsOnBuilder(readerBuilder, topics);
		}

		if (messageId != null) {
			readerBuilder.startMessageId(messageId);
		}

		if (!CollectionUtils.isEmpty(customizers)) {
			customizers.forEach(customizer -> customizer.customize(readerBuilder));
		}

		return readerBuilder.create();
	}

	private void replaceTopicsOnBuilder(ReaderBuilder<T> builder, Collection<String> topics) {
		var builderImpl = (ReaderBuilderImpl<T>) builder;
		builderImpl.getConf().setTopicNames(new HashSet<>(topics));
	}

	@Override
	public int getPhase() {
		return (Integer.MIN_VALUE / 2);
	}

	@Override
	protected PulsarClient createInstance() {
		this.logger.warn(() -> "Creating client");
		try {
			return this.pulsarClientFactory.createClient();
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
