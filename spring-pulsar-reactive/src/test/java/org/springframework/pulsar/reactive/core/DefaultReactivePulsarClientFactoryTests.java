/*
 * Copyright 2023-2023 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.lang.Nullable;
import org.springframework.pulsar.core.DefaultPulsarClientFactory;
import org.springframework.pulsar.core.PulsarClientBuilderCustomizer;
import org.springframework.pulsar.core.PulsarClientFactory;
import org.springframework.pulsar.test.support.PulsarTestContainerSupport;

/**
 * Tests for {@link DefaultReactivePulsarClientFactory}.
 *
 * @author Chris Bono
 */
class DefaultReactivePulsarClientFactoryTests {

	@Nullable
	protected PulsarClient pulsarClient;

	@BeforeEach
	void createPulsarClient() throws PulsarClientException {
		pulsarClient = PulsarClient.builder().serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl()).build();
	}

	@AfterEach
	void closePulsarClient() throws PulsarClientException {
		if (pulsarClient != null && !pulsarClient.isClosed()) {
			pulsarClient.close();
		}
	}

	@Test
	void createClientGetsPulsarClientFromImperativeFactory() throws PulsarClientException {
		var clientFactory = mock(PulsarClientFactory.class);
		when(clientFactory.createClient()).thenReturn(this.pulsarClient);
		var reactiveClientFactory = new DefaultReactivePulsarClientFactory(clientFactory);
		reactiveClientFactory.start();
		var reactiveClient = reactiveClientFactory.getInstance();
		assertThat(reactiveClient).isNotNull();
		verify(clientFactory).createClient();
	}


	@Nested
	class RestartClientFactory implements PulsarTestContainerSupport {

		@Test
		void leavesFactoryInUsableState() throws Exception {
			var serviceUrl = PulsarTestContainerSupport.getPulsarBrokerUrl();
			var clientFactory = spy(new DefaultPulsarClientFactory(serviceUrl));

			var reactiveClientFactory = new DefaultReactivePulsarClientFactory(clientFactory);
			clientFactory.afterPropertiesSet();
			clientFactory.start();
			reactiveClientFactory.start();

			var reactiveClient = reactiveClientFactory.getInstance();
			assertThat(reactiveClient).isNotNull();
			verify(clientFactory).createClient();

			// Stop and verify the client instance is null
			reactiveClientFactory.stop();
			assertThat(reactiveClientFactory.getInstance()).isNull();

			// Restart and verify the client is created again
			reactiveClientFactory.start();
			var newReactiveClient = reactiveClientFactory.getInstance();
			assertThat(newReactiveClient).isNotNull();
			verify(clientFactory).createClient();

			// Destroy the factory and verify the client is destroyed as well
			reactiveClientFactory.destroy();
			assertThat(reactiveClientFactory.getInstance()).isNull();
		}
	}
}
