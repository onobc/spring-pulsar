/*
 * Copyright 2022-present the original author or authors.
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

package org.springframework.pulsar.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.RedeliveryBackoff;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.pulsar.config.ConcurrentPulsarListenerContainerFactory;
import org.springframework.pulsar.config.PulsarListenerEndpoint;
import org.springframework.pulsar.core.PulsarConsumerFactory;
import org.springframework.pulsar.observation.PulsarListenerObservationConvention;
import org.springframework.util.backoff.BackOff;

import io.micrometer.observation.ObservationRegistry;

/**
 * @author Soby Chacko
 * @author Alexander Preuß
 * @author Chris Bono
 */
public class ConcurrentPulsarMessageListenerContainerTests {

	@Test
	@SuppressWarnings("unchecked")
	void createConcurrentContainerFromFactoryAndVerifyBatchReceivePolicy() {
		PulsarConsumerFactory<Object> consumerFactory = mock(PulsarConsumerFactory.class);
		PulsarContainerProperties containerProperties = new PulsarContainerProperties();
		containerProperties.setBatchTimeoutMillis(60_000);
		containerProperties.setMaxNumMessages(120);
		containerProperties.setMaxNumBytes(32000);
		containerProperties.setConcurrency(1);
		ConcurrentPulsarListenerContainerFactory<String> containerFactory = new ConcurrentPulsarListenerContainerFactory<>(
				consumerFactory, containerProperties);
		PulsarListenerEndpoint pulsarListenerEndpoint = mock(PulsarListenerEndpoint.class);
		when(pulsarListenerEndpoint.getConcurrency()).thenReturn(1);

		AbstractPulsarMessageListenerContainer<String> concurrentContainer = containerFactory
			.createRegisteredContainer(pulsarListenerEndpoint);

		PulsarContainerProperties pulsarContainerProperties = concurrentContainer.getContainerProperties();
		assertThat(pulsarContainerProperties.getBatchTimeoutMillis()).isEqualTo(60_000);
		assertThat(pulsarContainerProperties.getMaxNumMessages()).isEqualTo(120);
		assertThat(pulsarContainerProperties.getMaxNumBytes()).isEqualTo(32_000);
	}

	@Test
	void deadLetterPolicyAppliedOnChildContainer() throws Exception {
		PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
		ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
		DeadLetterPolicy deadLetterPolicy = DeadLetterPolicy.builder()
			.maxRedeliverCount(5)
			.deadLetterTopic("dlq-topic")
			.retryLetterTopic("retry-topic")
			.build();
		concurrentContainer.setDeadLetterPolicy(deadLetterPolicy);

		concurrentContainer.start();

		DefaultPulsarMessageListenerContainer<String> childContainer = concurrentContainer.getContainers().get(0);
		assertThat(childContainer.getDeadLetterPolicy()).isEqualTo(deadLetterPolicy);
	}

	@Test
	void nackRedeliveryBackoffAppliedOnChildContainer() throws Exception {
		PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
		ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
		RedeliveryBackoff redeliveryBackoff = MultiplierRedeliveryBackoff.builder()
			.minDelayMs(1000)
			.maxDelayMs(5 * 1000)
			.build();
		concurrentContainer.setNegativeAckRedeliveryBackoff(redeliveryBackoff);

		concurrentContainer.start();

		DefaultPulsarMessageListenerContainer<String> childContainer = concurrentContainer.getContainers().get(0);
		assertThat(childContainer.getNegativeAckRedeliveryBackoff()).isEqualTo(redeliveryBackoff);
	}

	@Test
	void ackTimeoutRedeliveryBackoffAppliedOnChildContainer() throws Exception {
		PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Exclusive);
		ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
		RedeliveryBackoff redeliveryBackoff = MultiplierRedeliveryBackoff.builder()
			.minDelayMs(1000)
			.maxDelayMs(5 * 1000)
			.build();
		concurrentContainer.setAckTimeoutRedeliveryBackoff(redeliveryBackoff);

		concurrentContainer.start();

		DefaultPulsarMessageListenerContainer<String> childContainer = concurrentContainer.getContainers().get(0);
		assertThat(childContainer.getAckTimeoutkRedeliveryBackoff()).isEqualTo(redeliveryBackoff);
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void pulsarConsumerErrorHandlerAppliedOnChildContainer() throws Exception {
		PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
		ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
		PulsarConsumerErrorHandler<String> pulsarConsumerErrorHandler = new DefaultPulsarConsumerErrorHandler(
				mock(PulsarMessageRecovererFactory.class), mock(BackOff.class));
		concurrentContainer.setPulsarConsumerErrorHandler(pulsarConsumerErrorHandler);

		concurrentContainer.start();

		DefaultPulsarMessageListenerContainer<String> childContainer = concurrentContainer.getContainers().get(0);
		assertThat(childContainer.getPulsarConsumerErrorHandler()).isEqualTo(pulsarConsumerErrorHandler);
	}

	@Test
	void observationConfigAppliedOnChildContainer() throws Exception {
		PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
		ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
		ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
		PulsarListenerObservationConvention observationConvention = mock(PulsarListenerObservationConvention.class);
		concurrentContainer.getContainerProperties().setObservationEnabled(true);
		concurrentContainer.getContainerProperties().setObservationRegistry(observationRegistry);
		concurrentContainer.getContainerProperties().setObservationConvention(observationConvention);
		concurrentContainer.start();

		DefaultPulsarMessageListenerContainer<String> childContainer = concurrentContainer.getContainers().get(0);
		assertThat(childContainer.getContainerProperties().isObservationEnabled()).isTrue();
		assertThat(childContainer.getContainerProperties().getObservationRegistry()).isSameAs(observationRegistry);
		assertThat(childContainer.getContainerProperties().getObservationConvention()).isSameAs(observationConvention);
	}

	@Test
	@SuppressWarnings("unchecked")
	void basicConcurrencyTesting() throws Exception {
		PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Failover);
		PulsarConsumerFactory<String> pulsarConsumerFactory = env.consumerFactory();
		Consumer<String> consumer = env.consumer();
		ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
		concurrentContainer.setConcurrency(3);

		concurrentContainer.start();

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> verify(pulsarConsumerFactory, times(3)).createConsumer(any(Schema.class), isNull(),
					isNull(), isNull(), anyList()));
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(consumer, times(3)).batchReceive());
	}

	@Test
	void exclusiveSubscriptionMustUseSingleThread() throws Exception {
		PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Exclusive);
		ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();

		concurrentContainer.setConcurrency(3);

		assertThatThrownBy(concurrentContainer::start).isInstanceOf(IllegalStateException.class)
			.hasMessage("concurrency > 1 is not allowed on Exclusive subscription type");
	}

	@SuppressWarnings("unchecked")
	private PulsarListenerMockComponents setupListenerMockComponents(SubscriptionType subscriptionType)
			throws Exception {
		PulsarConsumerFactory<String> consumerFactory = mock(PulsarConsumerFactory.class);
		Consumer<String> consumer = mock(Consumer.class);
		when(consumerFactory.createConsumer(any(Schema.class), isNull(), isNull(), isNull(), anyList()))
			.thenReturn(consumer);
		when(consumer.batchReceive()).thenReturn(mock(Messages.class));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		pulsarContainerProperties.setSchema(Schema.STRING);
		pulsarContainerProperties.setSubscriptionType(subscriptionType);
		pulsarContainerProperties.setMessageListener((PulsarRecordMessageListener<?>) (cons, msg) -> {
		});

		ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = new ConcurrentPulsarMessageListenerContainer<>(
				consumerFactory, pulsarContainerProperties);

		return new PulsarListenerMockComponents(consumerFactory, consumer, concurrentContainer);
	}

	private record PulsarListenerMockComponents(PulsarConsumerFactory<String> consumerFactory,
			Consumer<String> consumer, ConcurrentPulsarMessageListenerContainer<String> concurrentContainer) {
	}

	@Nested
	class ObservationConfigurationTests {

		@Test
		void uniqueRegistryAndConventionBeansAvailable() throws Exception {
			PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
			ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
			GenericApplicationContext appContext = new GenericApplicationContext();
			ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
			PulsarListenerObservationConvention observationConvention = mock(PulsarListenerObservationConvention.class);
			appContext.registerBean("obsReg", ObservationRegistry.class, () -> observationRegistry);
			appContext.registerBean("obsConv", PulsarListenerObservationConvention.class, () -> observationConvention);
			appContext.refresh();
			concurrentContainer.setApplicationContext(appContext);
			concurrentContainer.getContainerProperties().setObservationEnabled(true);
			concurrentContainer.start();
			PulsarContainerProperties containerProps = concurrentContainer.getContainerProperties();
			assertThat(containerProps.isObservationEnabled()).isTrue();
			assertThat(containerProps.getObservationRegistry()).isSameAs(observationRegistry);
			assertThat(containerProps.getObservationConvention()).isSameAs(observationConvention);
		}

		@Test
		void enabledPropertySetToFalse() throws Exception {
			PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
			ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
			GenericApplicationContext appContext = new GenericApplicationContext();
			ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
			PulsarListenerObservationConvention observationConvention = mock(PulsarListenerObservationConvention.class);
			appContext.registerBean("obsReg", ObservationRegistry.class, () -> observationRegistry);
			appContext.registerBean("obsConv", PulsarListenerObservationConvention.class, () -> observationConvention);
			appContext.refresh();
			concurrentContainer.setApplicationContext(appContext);
			concurrentContainer.getContainerProperties().setObservationEnabled(false);
			concurrentContainer.start();
			PulsarContainerProperties containerProps = concurrentContainer.getContainerProperties();
			assertThat(containerProps.isObservationEnabled()).isFalse();
			assertThat(containerProps.getObservationConvention()).isNull();
			assertThat(containerProps.getObservationRegistry()).isNull();
		}

		@Test
		void noAppContextAvailable() throws Exception {
			PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
			ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
			concurrentContainer.getContainerProperties().setObservationEnabled(true);
			concurrentContainer.start();
			PulsarContainerProperties containerProps = concurrentContainer.getContainerProperties();
			assertThat(containerProps.isObservationEnabled()).isTrue();
			assertThat(containerProps.getObservationConvention()).isNull();
			assertThat(containerProps.getObservationRegistry()).isNull();
		}

		@Test
		void noRegistryOrConventionBeansAvailable() throws Exception {
			PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
			ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
			GenericApplicationContext appContext = new GenericApplicationContext();
			appContext.refresh();
			concurrentContainer.setApplicationContext(appContext);
			concurrentContainer.getContainerProperties().setObservationEnabled(true);
			concurrentContainer.start();
			PulsarContainerProperties containerProps = concurrentContainer.getContainerProperties();
			assertThat(containerProps.isObservationEnabled()).isTrue();
			assertThat(containerProps.getObservationConvention()).isNull();
			assertThat(containerProps.getObservationRegistry()).isNull();
		}

		@Test
		void noUniqueRegistryOrConventionBeansAvailable() throws Exception {
			PulsarListenerMockComponents env = setupListenerMockComponents(SubscriptionType.Shared);
			ConcurrentPulsarMessageListenerContainer<String> concurrentContainer = env.concurrentContainer();
			GenericApplicationContext appContext = new GenericApplicationContext();
			appContext.registerBean("obsReg1", ObservationRegistry.class, () -> mock(ObservationRegistry.class));
			appContext.registerBean("obsReg2", ObservationRegistry.class, () -> mock(ObservationRegistry.class));
			appContext.registerBean("obsConv1", PulsarListenerObservationConvention.class,
					() -> mock(PulsarListenerObservationConvention.class));
			appContext.registerBean("obsConv2", PulsarListenerObservationConvention.class,
					() -> mock(PulsarListenerObservationConvention.class));
			appContext.refresh();
			concurrentContainer.setApplicationContext(appContext);
			concurrentContainer.getContainerProperties().setObservationEnabled(true);
			concurrentContainer.start();
			PulsarContainerProperties containerProps = concurrentContainer.getContainerProperties();
			assertThat(containerProps.isObservationEnabled()).isTrue();
			assertThat(containerProps.getObservationRegistry()).isNull();
			assertThat(containerProps.getObservationConvention()).isNull();
		}

	}

}
