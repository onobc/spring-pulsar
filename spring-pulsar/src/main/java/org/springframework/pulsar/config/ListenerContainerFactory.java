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

package org.springframework.pulsar.config;

import org.springframework.pulsar.listener.MessageListenerContainer;

/**
 * Factory for Pulsar message listener containers.
 *
 * @param <C> message listener container type.
 * @param <E> listener endpoint type.
 * @author Soby Chacko
 * @author Christophe Bornet
 * @author Chris Bono
 */
public interface ListenerContainerFactory<C extends MessageListenerContainer, E extends ListenerEndpoint<C>>
		extends PulsarContainerFactory<C, E> {

	/**
	 * Create a {@link MessageListenerContainer} for the given {@link ListenerEndpoint}.
	 * Containers created using this method are added to the listener endpoint registry.
	 * @param endpoint the endpoint to configure
	 * @return the created container
	 * @deprecated since 1.2.0 for removal in 1.4.0 in favor of
	 * {@link PulsarContainerFactory#createRegisteredContainer}
	 */
	@Deprecated(since = "1.2.0", forRemoval = true)
	default C createListenerContainer(E endpoint) {
		return createRegisteredContainer(endpoint);
	}

}
