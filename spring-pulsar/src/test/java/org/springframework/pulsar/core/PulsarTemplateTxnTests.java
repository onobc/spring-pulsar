/*
 * Copyright 2022-2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.pulsar.PulsarException;
import org.springframework.pulsar.test.support.PulsarConsumerTestUtil;
import org.springframework.pulsar.test.support.PulsarTestContainerSupport;

/**
 * Tests for {@link PulsarTemplate}.
 *
 * @author Soby Chacko
 * @author Chris Bono
 * @author Alexander Preuß
 * @author Christophe Bornet
 * @author Jonas Geiregat
 */
class PulsarTemplateTxnTests implements PulsarTestContainerSupport {

	private PulsarClient client;

	@BeforeEach
	void setup() throws PulsarClientException {
		client = PulsarClient.builder()
				.enableTransaction(true)
				.serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl())
				.build();
	}

	@AfterEach
	void tearDown() throws PulsarClientException {
		client.close();
	}

	@Test
	void sendMessagesWithLocalTransactionHandlesCommit() {
		String topic = "pttt-send-commit-topic";
		PulsarProducerFactory<String> senderFactory = new DefaultPulsarProducerFactory<>(client, null);
		PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(senderFactory);
		Map<String, MessageId> results = pulsarTemplate.executeInTransaction((template) -> {
			var rv = new HashMap<String, MessageId>();
			rv.put("msg1", template.send(topic, "msg1"));
			rv.put("msg2", template.send(topic, "msg2"));
			rv.put("msg3", template.send(topic, "msg3"));
			return rv;
		});
		assertThat(results).containsOnlyKeys("msg1", "msg2", "msg3")
				.allSatisfy((__, v) -> assertThat(v).isNotNull());
		assertMessagesCommitted(topic, List.of("msg1", "msg2", "msg3"));
	}

	@Test
	void sendMessagesWithLocalTransactionHandlesRollback() {
		String topic = "pttt-send-rollback-topic";
		PulsarProducerFactory<String> senderFactory = new DefaultPulsarProducerFactory<>(client, null);
		PulsarTemplate<String> pulsarTemplate = spy(new PulsarTemplate<>(senderFactory));
		doThrow(new PulsarException("5150")).when(pulsarTemplate).send(topic, "msg2");
		assertThatExceptionOfType(PulsarException.class).isThrownBy(() ->
				pulsarTemplate.executeInTransaction((template) -> {
					var rv = new HashMap<String, MessageId>();
					rv.put("msg1", template.send(topic, "msg1"));
					rv.put("msg2", template.send(topic, "msg2"));
					rv.put("msg3", template.send(topic, "msg3"));
					return rv;
				})).withMessage("5150");
		assertMessagesCommitted(topic, Collections.emptyList());
	}

	@Test
	void sendMessagesWithLocalTransactionIsolatedByThreads() throws Exception {
		String topic = "pttt-send-threads-topic";
		PulsarProducerFactory<String> senderFactory = new DefaultPulsarProducerFactory<>(client, null);
		PulsarTemplate<String> pulsarTemplate = spy(new PulsarTemplate<>(senderFactory));
		doThrow(new PulsarException("5150")).when(pulsarTemplate).send(topic, "msg2");
		var latch = new CountDownLatch(2);
		var t1 = new Thread(() -> {
			pulsarTemplate.executeInTransaction((template) -> template.send(topic, "msg1"));
			latch.countDown();
		});
		var t2 = new Thread(() -> {
			try {
				pulsarTemplate.executeInTransaction((template) -> template.send(topic, "msg2"));
			} finally {
				latch.countDown();
			}
		});
		t1.start();
		t2.start();
		latch.await(3, TimeUnit.SECONDS);
		assertMessagesCommitted(topic, List.of("msg1"));
	}

	@Test
	void nestedLocalTransactionsNotAllowed() {
		String topic = "pttt-nested-topic";
		PulsarProducerFactory<String> senderFactory = new DefaultPulsarProducerFactory<>(client, null);
		PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(senderFactory);
		assertThatIllegalStateException().isThrownBy(() -> pulsarTemplate.executeInTransaction((template) -> {
			template.send(topic, "msg1");
			return template.executeInTransaction((innerTemplate) -> innerTemplate.send(topic, "msg2"));
		})).withMessage("Nested calls to 'executeInTransaction' are not allowed");
		assertMessagesCommitted(topic, Collections.emptyList());
	}

	@Test
	void localTransactionsNotAllowedOnNonTransactionalTemplate() {
		PulsarProducerFactory<String> senderFactory = new DefaultPulsarProducerFactory<>(client, null);
		PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(senderFactory);
		pulsarTemplate.setTransactional(false);
		assertThatIllegalStateException().isThrownBy(() -> pulsarTemplate.executeInTransaction((template) -> null))
				.withMessage("This template does not support transactions");
	}

	private void assertMessagesCommitted(String topic, List<String> expectedMsgs) {
		assertThat(PulsarConsumerTestUtil.<String>consumeMessages(client)
				.fromTopic(topic)
				.withSchema(Schema.STRING)
				.awaitAtMost(Duration.ofSeconds(3))
				.get())
				.map(Message::getValue)
				.containsExactlyInAnyOrderElementsOf(expectedMsgs);
	}
}
