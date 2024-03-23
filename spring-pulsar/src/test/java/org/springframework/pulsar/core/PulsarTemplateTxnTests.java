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
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		// Make sure the producer was closed by the template (albeit indirectly as
		// client removes closed producers)
		await().atMost(Duration.ofSeconds(3))
			.untilAsserted(() -> assertThat(client).extracting("producers")
				.asInstanceOf(InstanceOfAssertFactories.COLLECTION)
				.isEmpty());
		client.close();
	}

	@Test
	@SuppressWarnings("unchecked")
	void sendMessagesWithLocalTransaction() {
		String topic = "pttt-local-txn-topic";
		PulsarProducerFactory<String> senderFactory = new DefaultPulsarProducerFactory<>(client, null);
		PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(senderFactory);
		Map<String, MessageId> results = pulsarTemplate.executeInTransaction((template) -> {
			var rv = new HashMap<String, MessageId>();
			rv.put("msg1", template.send(topic, "msg1"));
			rv.put("msg2", template.send(topic, "msg2"));
			rv.put("msg3", template.send(topic, "msg3"));
			return rv;
		});
		assertThat(results)
				.containsOnlyKeys("msg1", "msg2", "msg3")
				.allSatisfy((__, v) -> assertThat(v).isNotNull());
	}

}
