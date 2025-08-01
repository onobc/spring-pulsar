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

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.junit.jupiter.api.Test;

import org.springframework.core.log.LogAccessor;
import org.springframework.pulsar.core.DefaultPulsarConsumerFactory;
import org.springframework.pulsar.core.DefaultPulsarProducerFactory;
import org.springframework.pulsar.core.PulsarOperations;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.pulsar.core.TypedMessageBuilderCustomizer;
import org.springframework.pulsar.test.support.PulsarTestContainerSupport;
import org.springframework.util.backoff.FixedBackOff;

/**
 * @author Soby Chacko
 */
public class DefaultPulsarConsumerErrorHandlerTests implements PulsarTestContainerSupport {

	private final LogAccessor logger = new LogAccessor(this.getClass());

	@Test
	@SuppressWarnings("unchecked")
	void happyPathErrorHandlingForRecordMessageListener() throws Exception {
		PulsarClient pulsarClient = PulsarClient.builder()
			.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
			.build();
		DefaultPulsarConsumerFactory<String> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic("default-error-handler-tests-1");
					consumerBuilder.subscriptionName("default-error-handler-tests-sub-1");
				}));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		PulsarRecordMessageListener<?> messageListener = mock(PulsarRecordMessageListener.class);

		doAnswer(invocation -> {
			throw new RuntimeException();
		}).when(messageListener).received(any(Consumer.class), any(Message.class));

		pulsarContainerProperties.setMessageListener(messageListener);
		pulsarContainerProperties.setSchema(Schema.STRING);

		DefaultPulsarProducerFactory<String> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				"default-error-handler-tests-1");
		PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		PulsarTemplate<String> mockPulsarTemplate = mock(PulsarTemplate.class, RETURNS_DEEP_STUBS);

		DefaultPulsarMessageListenerContainer<String> container = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties);
		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 10)));
		container.start();

		pulsarTemplate.sendAsync("hello john doe");

		PulsarOperations.SendMessageBuilder<String> sendMessageBuilderMock = mock(
				PulsarOperations.SendMessageBuilder.class);

		when(mockPulsarTemplate.newMessage("hello john doe")
			.withTopic(any(String.class))
			.withMessageCustomizer(any(TypedMessageBuilderCustomizer.class))).thenReturn(sendMessageBuilderMock);

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> verify(messageListener, times(11)).received(any(Consumer.class), any(Message.class)));
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(sendMessageBuilderMock).sendAsync());

		container.stop();
		pulsarClient.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void errorHandlingForRecordMessageListenerWithTransientError() throws Exception {
		PulsarClient pulsarClient = PulsarClient.builder()
			.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
			.build();
		DefaultPulsarConsumerFactory<String> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic("default-error-handler-tests-2");
					consumerBuilder.subscriptionName("default-error-handler-tests-sub-2");
				}));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		PulsarRecordMessageListener<?> messageListener = mock(PulsarRecordMessageListener.class);
		AtomicInteger count = new AtomicInteger(0);
		doAnswer(invocation -> {
			int currentCount = count.incrementAndGet();
			if (currentCount <= 3) {
				throw new RuntimeException();
			}
			return new Object();
		}).when(messageListener).received(any(Consumer.class), any(Message.class));

		pulsarContainerProperties.setMessageListener(messageListener);
		pulsarContainerProperties.setSchema(Schema.STRING);

		DefaultPulsarProducerFactory<String> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				"default-error-handler-tests-2");
		PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		PulsarTemplate<String> mockPulsarTemplate = mock(PulsarTemplate.class);

		DefaultPulsarMessageListenerContainer<String> container = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties);
		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 10)));
		container.start();

		pulsarTemplate.sendAsync("hello john doe");

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> verify(messageListener, times(4)).received(any(Consumer.class), any(Message.class)));
		verifyNoInteractions(mockPulsarTemplate);

		container.stop();
		pulsarClient.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void everyOtherRecordThrowsNonTransientExceptionsRecordMessageListener() throws Exception {
		PulsarClient pulsarClient = PulsarClient.builder()
			.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
			.build();
		DefaultPulsarConsumerFactory<Integer> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic("default-error-handler-tests-3");
					consumerBuilder.subscriptionName("default-error-handler-tests-sub-3");
				}));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		PulsarRecordMessageListener<?> messageListener = mock(PulsarRecordMessageListener.class);
		doAnswer(invocation -> {
			Message<Integer> message = invocation.getArgument(1);
			Integer value = message.getValue();
			if (value % 2 == 0) {
				throw new RuntimeException();
			}
			return new Object();
		}).when(messageListener).received(any(Consumer.class), any(Message.class));

		pulsarContainerProperties.setMessageListener(messageListener);
		pulsarContainerProperties.setSchema(Schema.INT32);

		DefaultPulsarProducerFactory<Integer> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				"default-error-handler-tests-3");
		PulsarTemplate<Integer> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		PulsarTemplate<Integer> mockPulsarTemplate = mock(PulsarTemplate.class, RETURNS_DEEP_STUBS);

		DefaultPulsarMessageListenerContainer<Integer> container = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties);
		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 5)));
		container.start();

		for (int i = 0; i < 10; i++) {
			pulsarTemplate.sendAsync(i);
		}

		PulsarOperations.SendMessageBuilder<Integer> sendMessageBuilderMock = mock(
				PulsarOperations.SendMessageBuilder.class);

		when(mockPulsarTemplate.newMessage(any(Integer.class))
			.withTopic(any(String.class))
			.withMessageCustomizer(any(TypedMessageBuilderCustomizer.class))).thenReturn(sendMessageBuilderMock);

		// 5 records fail - 5 * (1 + 5 max retry) = 30 + 5 records don't fail = 35
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(messageListener, times(35)).received(any(Consumer.class), any(Message.class)));
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(sendMessageBuilderMock, times(5)).sendAsync());

		container.stop();
		pulsarClient.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void batchRecordListenerFirstOneOnlyErrorAndRecover() throws Exception {
		PulsarClient pulsarClient = PulsarClient.builder()
			.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
			.build();
		DefaultPulsarConsumerFactory<Integer> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic("default-error-handler-tests-4");
					consumerBuilder.subscriptionName("default-error-handler-tests-sub-4");
				}));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		pulsarContainerProperties.setMaxNumMessages(10);
		pulsarContainerProperties.setBatchTimeoutMillis(60_000);
		pulsarContainerProperties.setBatchListener(true);
		PulsarBatchAcknowledgingMessageListener<?> pulsarBatchMessageListener = mock(
				PulsarBatchAcknowledgingMessageListener.class);

		doAnswer(invocation -> {
			List<Message<Integer>> message = invocation.getArgument(1);
			Message<Integer> integerMessage = message.get(0);
			Integer value = integerMessage.getValue();
			if (value == 0) {
				throw new PulsarBatchListenerFailedException("failed", integerMessage);
			}
			Acknowledgement acknowledgment = invocation.getArgument(2);
			List<MessageId> messageIds = new ArrayList<>();
			for (Message<Integer> integerMessage1 : message) {
				messageIds.add(integerMessage1.getMessageId());
			}
			acknowledgment.acknowledge(messageIds);
			return new Object();
		}).when(pulsarBatchMessageListener).received(any(Consumer.class), any(List.class), any(Acknowledgement.class));

		pulsarContainerProperties.setMessageListener(pulsarBatchMessageListener);
		pulsarContainerProperties.setSchema(Schema.INT32);
		pulsarContainerProperties.setAckMode(AckMode.MANUAL);
		DefaultPulsarMessageListenerContainer<Integer> container = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties);
		PulsarTemplate<Integer> mockPulsarTemplate = mock(PulsarTemplate.class, RETURNS_DEEP_STUBS);

		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 10)));

		container.start();

		DefaultPulsarProducerFactory<Integer> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				"default-error-handler-tests-4");
		PulsarTemplate<Integer> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		for (int i = 0; i < 10; i++) {
			pulsarTemplate.sendAsync(i);
		}

		PulsarOperations.SendMessageBuilder<Integer> sendMessageBuilderMock = mock(
				PulsarOperations.SendMessageBuilder.class);

		when(mockPulsarTemplate.newMessage(any(Integer.class))
			.withTopic(any(String.class))
			.withMessageCustomizer(any(TypedMessageBuilderCustomizer.class))).thenReturn(sendMessageBuilderMock);

		// 1 + 10 + 1 = 12 calls altogether
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(pulsarBatchMessageListener, times(12)).received(any(Consumer.class),
					any(List.class), any(Acknowledgement.class)));
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(sendMessageBuilderMock, times(1)).sendAsync());

		container.stop();
		pulsarClient.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void batchRecordListenerRecordFailsInTheMiddle() throws Exception {
		PulsarClient pulsarClient = PulsarClient.builder()
			.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
			.build();
		DefaultPulsarConsumerFactory<Integer> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic("default-error-handler-tests-5");
					consumerBuilder.subscriptionName("default-error-handler-tests-sub-5");
				}));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		pulsarContainerProperties.setMaxNumMessages(10);
		pulsarContainerProperties.setBatchTimeoutMillis(60_000);
		pulsarContainerProperties.setBatchListener(true);
		PulsarBatchAcknowledgingMessageListener<?> pulsarBatchMessageListener = mock(
				PulsarBatchAcknowledgingMessageListener.class);

		doAnswer(invocation -> {
			List<Message<Integer>> messages = invocation.getArgument(1);

			for (Message<Integer> message : messages) {
				if (message.getValue() == 5) {
					throw new PulsarBatchListenerFailedException("failed", message);
				}
				else {
					Acknowledgement acknowledgment = invocation.getArgument(2);
					acknowledgment.acknowledge(message.getMessageId());
				}
			}
			return new Object();
		}).when(pulsarBatchMessageListener).received(any(Consumer.class), any(List.class), any(Acknowledgement.class));

		pulsarContainerProperties.setMessageListener(pulsarBatchMessageListener);
		pulsarContainerProperties.setSchema(Schema.INT32);
		pulsarContainerProperties.setAckMode(AckMode.MANUAL);
		DefaultPulsarMessageListenerContainer<Integer> container = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties);
		PulsarTemplate<Integer> mockPulsarTemplate = mock(PulsarTemplate.class, RETURNS_DEEP_STUBS);

		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 10)));

		container.start();

		DefaultPulsarProducerFactory<Integer> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				"default-error-handler-tests-5");
		PulsarTemplate<Integer> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		for (int i = 0; i < 10; i++) {
			pulsarTemplate.sendAsync(i);
		}
		PulsarOperations.SendMessageBuilder<Integer> sendMessageBuilderMock = mock(
				PulsarOperations.SendMessageBuilder.class);

		when(mockPulsarTemplate.newMessage(any(Integer.class))
			.withTopic(any(String.class))
			.withMessageCustomizer(any(TypedMessageBuilderCustomizer.class))).thenReturn(sendMessageBuilderMock);

		// 1 + 10 + 1 = 12 calls altogether
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(pulsarBatchMessageListener, times(12)).received(any(Consumer.class),
					any(List.class), any(Acknowledgement.class)));
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(sendMessageBuilderMock, times(1)).sendAsync());

		container.stop();
		pulsarClient.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void batchRecordListenerRecordFailsTwiceInTheMiddle() throws Exception {
		PulsarClient pulsarClient = PulsarClient.builder()
			.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
			.build();
		DefaultPulsarConsumerFactory<Integer> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic("default-error-handler-tests-6");
					consumerBuilder.subscriptionName("default-error-handler-tests-sub-6");
				}));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		pulsarContainerProperties.setMaxNumMessages(10);
		pulsarContainerProperties.setBatchTimeoutMillis(60_000);
		pulsarContainerProperties.setBatchListener(true);
		PulsarBatchAcknowledgingMessageListener<?> pulsarBatchMessageListener = mock(
				PulsarBatchAcknowledgingMessageListener.class);

		doAnswer(invocation -> {
			List<Message<Integer>> messages = invocation.getArgument(1);

			for (Message<Integer> message : messages) {
				if (message.getValue() == 2 || message.getValue() == 5) {
					throw new PulsarBatchListenerFailedException("failed", message);
				}
				else {
					Acknowledgement acknowledgment = invocation.getArgument(2);
					acknowledgment.acknowledge(message.getMessageId());
				}
			}
			return new Object();
		}).when(pulsarBatchMessageListener).received(any(Consumer.class), any(List.class), any(Acknowledgement.class));

		pulsarContainerProperties.setMessageListener(pulsarBatchMessageListener);
		pulsarContainerProperties.setSchema(Schema.INT32);
		pulsarContainerProperties.setAckMode(AckMode.MANUAL);
		DefaultPulsarMessageListenerContainer<Integer> container = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties);
		PulsarTemplate<Integer> mockPulsarTemplate = mock(PulsarTemplate.class, RETURNS_DEEP_STUBS);

		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 10)));

		container.start();

		DefaultPulsarProducerFactory<Integer> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				"default-error-handler-tests-6");
		PulsarTemplate<Integer> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		for (int i = 0; i < 10; i++) {
			pulsarTemplate.sendAsync(i);
		}
		PulsarOperations.SendMessageBuilder<Integer> sendMessageBuilderMock = mock(
				PulsarOperations.SendMessageBuilder.class);

		when(mockPulsarTemplate.newMessage(any(Integer.class))
			.withTopic(any(String.class))
			.withMessageCustomizer(any(TypedMessageBuilderCustomizer.class))).thenReturn(sendMessageBuilderMock);

		// 1 + 10 + 1 + 10 + 1 = 23 calls altogether
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(pulsarBatchMessageListener, times(23)).received(any(Consumer.class),
					any(List.class), any(Acknowledgement.class)));
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(sendMessageBuilderMock, times(2)).sendAsync());

		container.stop();
		pulsarClient.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void batchRecordListenerRecordFailsInTheMiddleButTransientError() throws Exception {
		PulsarClient pulsarClient = PulsarClient.builder()
			.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
			.build();
		DefaultPulsarConsumerFactory<Integer> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic("default-error-handler-tests-7");
					consumerBuilder.subscriptionName("default-error-handler-tests-sub-7");
				}));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		pulsarContainerProperties.setMaxNumMessages(10);
		pulsarContainerProperties.setBatchTimeoutMillis(60_000);
		pulsarContainerProperties.setBatchListener(true);
		PulsarBatchAcknowledgingMessageListener<?> pulsarBatchMessageListener = mock(
				PulsarBatchAcknowledgingMessageListener.class);

		AtomicInteger count = new AtomicInteger(0);
		doAnswer(invocation -> {
			List<Message<Integer>> messages = invocation.getArgument(1);
			Acknowledgement acknowledgment = invocation.getArgument(2);
			for (Message<Integer> message : messages) {
				if (message.getValue() == 5) {
					int currentCount = count.getAndIncrement();
					if (currentCount < 3) {
						throw new PulsarBatchListenerFailedException("failed", message);
					}
					else {
						acknowledgment.acknowledge(message.getMessageId());
					}
				}
				else {
					acknowledgment.acknowledge(message.getMessageId());
				}
			}
			return new Object();
		}).when(pulsarBatchMessageListener).received(any(Consumer.class), any(List.class), any(Acknowledgement.class));

		pulsarContainerProperties.setMessageListener(pulsarBatchMessageListener);
		pulsarContainerProperties.setSchema(Schema.INT32);
		pulsarContainerProperties.setAckMode(AckMode.MANUAL);
		DefaultPulsarMessageListenerContainer<Integer> container = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties);
		PulsarTemplate<Integer> mockPulsarTemplate = mock(PulsarTemplate.class, RETURNS_DEEP_STUBS);

		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 10)));

		container.start();

		DefaultPulsarProducerFactory<Integer> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				"default-error-handler-tests-7");
		PulsarTemplate<Integer> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		for (int i = 0; i < 10; i++) {
			pulsarTemplate.sendAsync(i);
		}
		// 1 + 3 + 1 = 5 calls altogether
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(pulsarBatchMessageListener, times(4)).received(any(Consumer.class),
					any(List.class), any(Acknowledgement.class)));
		verifyNoInteractions(mockPulsarTemplate);

		container.stop();
		pulsarClient.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void batchListenerFailsTransientErrorFollowedByNonTransient() throws Exception {
		PulsarClient pulsarClient = PulsarClient.builder()
			.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
			.build();
		DefaultPulsarConsumerFactory<Integer> pulsarConsumerFactory = new DefaultPulsarConsumerFactory<>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic("default-error-handler-tests-8");
					consumerBuilder.subscriptionName("default-error-handler-tests-sub-8");
				}));

		PulsarContainerProperties pulsarContainerProperties = new PulsarContainerProperties();
		pulsarContainerProperties.setMaxNumMessages(10);
		pulsarContainerProperties.setBatchTimeoutMillis(60_000);
		pulsarContainerProperties.setBatchListener(true);
		PulsarBatchAcknowledgingMessageListener<?> pulsarBatchMessageListener = mock(
				PulsarBatchAcknowledgingMessageListener.class);

		AtomicInteger count = new AtomicInteger(0);
		doAnswer(invocation -> {
			List<Message<Integer>> messages = invocation.getArgument(1);
			Acknowledgement acknowledgment = invocation.getArgument(2);
			for (Message<Integer> message : messages) {
				if (message.getValue() == 5) {
					int currentCount = count.getAndIncrement();
					if (currentCount < 3) {
						throw new PulsarBatchListenerFailedException("failed", message);
					}
					else {
						acknowledgment.acknowledge(message.getMessageId());
					}
				}
				else if (message.getValue() == 7) {
					throw new PulsarBatchListenerFailedException("failed", message);
				}
				else {
					acknowledgment.acknowledge(message.getMessageId());
				}
			}
			return new Object();
		}).when(pulsarBatchMessageListener).received(any(Consumer.class), any(List.class), any(Acknowledgement.class));

		pulsarContainerProperties.setMessageListener(pulsarBatchMessageListener);
		pulsarContainerProperties.setSchema(Schema.INT32);
		pulsarContainerProperties.setAckMode(AckMode.MANUAL);
		DefaultPulsarMessageListenerContainer<Integer> container = new DefaultPulsarMessageListenerContainer<>(
				pulsarConsumerFactory, pulsarContainerProperties);
		PulsarTemplate<Integer> mockPulsarTemplate = mock(PulsarTemplate.class, RETURNS_DEEP_STUBS);

		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 10)));

		container.start();

		DefaultPulsarProducerFactory<Integer> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				"default-error-handler-tests-8");
		PulsarTemplate<Integer> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		for (int i = 0; i < 10; i++) {
			pulsarTemplate.sendAsync(i);
		}
		PulsarOperations.SendMessageBuilder<Integer> sendMessageBuilderMock = mock(
				PulsarOperations.SendMessageBuilder.class);

		when(mockPulsarTemplate.newMessage(any(Integer.class))
			.withTopic(any(String.class))
			.withMessageCustomizer(any(TypedMessageBuilderCustomizer.class))).thenReturn(sendMessageBuilderMock);
		// 1 + 2 + 1 + 10 + 1 = 15 calls altogether
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(pulsarBatchMessageListener, times(15)).received(any(Consumer.class),
					any(List.class), any(Acknowledgement.class)));
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> verify(sendMessageBuilderMock, times(1)).sendAsync());

		container.stop();
		pulsarClient.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void whenBatchRecordListenerOneMessageBatchFailsThenSentToDltProperly() throws Exception {
		var topicName = "default-error-handler-tests-9";
		var pulsarClient = PulsarClient.builder().serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl()).build();
		var pulsarConsumerFactory = new DefaultPulsarConsumerFactory<Integer>(pulsarClient,
				List.of((consumerBuilder) -> {
					consumerBuilder.topic(topicName);
					consumerBuilder.subscriptionName("%s-sub".formatted(topicName));
				}));
		// Prepare container for batch consume
		var pulsarContainerProperties = new PulsarContainerProperties();
		pulsarContainerProperties.setSchema(Schema.INT32);
		pulsarContainerProperties.setAckMode(AckMode.MANUAL);
		pulsarContainerProperties.setBatchListener(true);
		pulsarContainerProperties.setMaxNumMessages(1);
		pulsarContainerProperties.setBatchTimeoutMillis(60_000);
		PulsarBatchAcknowledgingMessageListener<?> pulsarBatchMessageListener = mock();
		doAnswer(invocation -> {
			List<Message<Integer>> message = invocation.getArgument(1);
			Message<Integer> integerMessage = message.get(0);
			Integer value = integerMessage.getValue();
			if (value == 0) {
				throw new PulsarBatchListenerFailedException("failed", integerMessage);
			}
			Acknowledgement acknowledgment = invocation.getArgument(2);
			List<MessageId> messageIds = new ArrayList<>();
			for (Message<Integer> integerMessage1 : message) {
				messageIds.add(integerMessage1.getMessageId());
			}
			acknowledgment.acknowledge(messageIds);
			return new Object();
		}).when(pulsarBatchMessageListener).received(any(Consumer.class), any(List.class), any(Acknowledgement.class));
		pulsarContainerProperties.setMessageListener(pulsarBatchMessageListener);
		var container = new DefaultPulsarMessageListenerContainer<>(pulsarConsumerFactory, pulsarContainerProperties);

		// Set error handler to recover after 2 retries
		PulsarTemplate<Integer> mockPulsarTemplate = mock(RETURNS_DEEP_STUBS);
		PulsarOperations.SendMessageBuilder<Integer> sendMessageBuilderMock = mock();
		when(mockPulsarTemplate.newMessage(any(Integer.class))
			.withTopic(any(String.class))
			.withMessageCustomizer(any(TypedMessageBuilderCustomizer.class))).thenReturn(sendMessageBuilderMock);
		container.setPulsarConsumerErrorHandler(new DefaultPulsarConsumerErrorHandler<>(
				new PulsarDeadLetterPublishingRecoverer<>(mockPulsarTemplate), new FixedBackOff(100, 2)));
		try {
			container.start();
			// Send single message in batch
			var pulsarProducerFactory = new DefaultPulsarProducerFactory<Integer>(pulsarClient, topicName);
			var pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
			pulsarTemplate.sendAsync(0);
			// Initial call should fail
			// Next 2 calls should fail (retries 2)
			// No more calls after that - msg should go to DLT
			await().atMost(Duration.ofSeconds(30))
				.untilAsserted(() -> verify(pulsarBatchMessageListener, times(3)).received(any(Consumer.class),
						any(List.class), any(Acknowledgement.class)));
			await().atMost(Duration.ofSeconds(30))
				.untilAsserted(() -> verify(sendMessageBuilderMock, times(1)).sendAsync());
		}
		finally {
			safeStopContainer(container);
		}
		pulsarClient.close();
	}

	private void safeStopContainer(PulsarMessageListenerContainer container) {
		try {
			container.stop();
		}
		catch (Exception ex) {
			logger.warn(ex, "Failed to stop container %s: %s".formatted(container, ex.getMessage()));
		}
	}

}
