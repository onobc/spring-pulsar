/*
 * Copyright 2022 the original author or authors.
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

package org.springframework.pulsar.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.pulsar.client.api.CompressionType;
import org.apache.pulsar.client.api.HashingScheme;
import org.apache.pulsar.client.api.MessageRoutingMode;
import org.apache.pulsar.client.api.ProducerAccessMode;
import org.apache.pulsar.client.api.ProducerCryptoFailureAction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Unit tests for {@link PulsarProperties}.
 *
 * @author Chris Bono
 * @author Christophe Bornet
 */
public class PulsarPropertiesTests {

	private final PulsarProperties properties = new PulsarProperties();

	private void bind(String name, String value) {
		bind(Collections.singletonMap(name, value));
	}

	private void bind(Map<String, String> map) {
		ConfigurationPropertySource source = new MapConfigurationPropertySource(map);
		new Binder(source).bind("spring.pulsar", Bindable.ofInstance(this.properties));
	}

	@Nested
	class AdminPropertiesTests {

		private String authPluginClassName = "org.apache.pulsar.client.impl.auth.AuthenticationToken";

		private String authParamsStr = "{\"token\":\"1234\"}";

		private String authToken = "1234";

		@Test
		void authenticationUsingAuthParamsString() {
			Map<String, String> props = new HashMap<>();
			props.put("spring.pulsar.administration.auth-plugin-class-name",
					"org.apache.pulsar.client.impl.auth.AuthenticationToken");
			props.put("spring.pulsar.administration.auth-params", authParamsStr);
			bind(props);
			assertThat(properties.getAdministration().getAuthParams()).isEqualTo(authParamsStr);
			assertThat(properties.getAdministration().getAuthPluginClassName()).isEqualTo(authPluginClassName);
			Map<String, Object> adminProps = properties.buildAdminProperties();
			assertThat(adminProps).containsEntry("authPluginClassName", authPluginClassName).containsEntry("authParams",
					authParamsStr);
		}

		@Test
		void authenticationUsingAuthenticationMap() {
			Map<String, String> props = new HashMap<>();
			props.put("spring.pulsar.administration.auth-plugin-class-name", authPluginClassName);
			props.put("spring.pulsar.administration.authentication.token", authToken);
			bind(props);
			assertThat(properties.getAdministration().getAuthentication()).containsEntry("token", authToken);
			assertThat(properties.getAdministration().getAuthPluginClassName()).isEqualTo(authPluginClassName);
			Map<String, Object> adminProps = properties.buildAdminProperties();
			assertThat(adminProps).containsEntry("authPluginClassName", authPluginClassName).containsEntry("authParams",
					authParamsStr);
		}

		@Test
		void authenticationNotAllowedUsingBothAuthParamsStringAndAuthenticationMap() {
			Map<String, String> props = new HashMap<>();
			props.put("spring.pulsar.administration.auth-plugin-class-name", authPluginClassName);
			props.put("spring.pulsar.administration.auth-params", authParamsStr);
			props.put("spring.pulsar.administration.authentication.token", authToken);
			bind(props);
			assertThatIllegalArgumentException().isThrownBy(() -> properties.buildAdminProperties())
					.withMessageContaining(
							"Cannot set both spring.pulsar.administration.authParams and spring.pulsar.administration.authentication.*");
		}

	}

	@Nested
	class ProducerPropertiesTests {

		@Test
		void producerProperties() {
			Map<String, String> props = new HashMap<>();
			props.put("spring.pulsar.producer.topic-name", "my-topic");
			props.put("spring.pulsar.producer.producer-name", "my-producer");
			props.put("spring.pulsar.producer.send-timeout", "2s");
			props.put("spring.pulsar.producer.block-if-queue-full", "true");
			props.put("spring.pulsar.producer.max-pending-messages", "3");
			props.put("spring.pulsar.producer.max-pending-messages-across-partitions", "4");
			props.put("spring.pulsar.producer.message-routing-mode", "CustomPartition");
			props.put("spring.pulsar.producer.hashing-scheme", "Murmur3_32Hash");
			props.put("spring.pulsar.producer.crypto-failure-action", "SEND");
			props.put("spring.pulsar.producer.batching-max-publish-delay", "5s");
			props.put("spring.pulsar.producer.batching-max-messages", "6");
			props.put("spring.pulsar.producer.batching-enabled", "false");
			props.put("spring.pulsar.producer.chunking-enabled", "true");
			props.put("spring.pulsar.producer.compression-type", "LZ4");
			props.put("spring.pulsar.producer.producer-access-mode", "Exclusive");

			bind(props);
			Map<String, Object> producerProps = properties.buildProducerProperties();

			assertThat(producerProps).containsEntry("topicName", "my-topic")
					.containsEntry("producerName", "my-producer").containsEntry("sendTimeoutMs", 2_000L)
					.containsEntry("blockIfQueueFull", true).containsEntry("maxPendingMessages", 3)
					.containsEntry("maxPendingMessagesAcrossPartitions", 4)
					.containsEntry("messageRoutingMode", MessageRoutingMode.CustomPartition)
					.containsEntry("hashingScheme", HashingScheme.Murmur3_32Hash)
					.containsEntry("cryptoFailureAction", ProducerCryptoFailureAction.SEND)
					.containsEntry("batchingMaxPublishDelayMicros", 5_000_000L).containsEntry("batchingMaxMessages", 6)
					.containsEntry("batchingEnabled", false).containsEntry("chunkingEnabled", true)
					.containsEntry("compressionType", CompressionType.LZ4)
					.containsEntry("accessMode", ProducerAccessMode.Exclusive);
		}

	}

}