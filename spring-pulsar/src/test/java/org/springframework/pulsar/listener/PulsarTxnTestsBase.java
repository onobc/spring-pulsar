/*
 * Copyright 2023-present the original author or authors.
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

import org.jspecify.annotations.Nullable;

import org.springframework.context.annotation.Bean;
import org.springframework.pulsar.transaction.PulsarAwareTransactionManager;

/**
 * @author Chris Bono
 * @author Andrey Litvitski
 */
public class PulsarTxnTestsBase {

	@Bean
	PulsarContainerProperties pulsarContainerProperties(PulsarAwareTransactionManager pulsarTransactionManager,
			@Nullable TestPulsarContainerPropertiesCustomizer containerPropsCustomizer) {
		var containerProps = new PulsarContainerProperties();
		containerProps.transactions().setEnabled(true);
		containerProps.transactions().setRequired(false);
		containerProps.transactions().setTransactionManager(pulsarTransactionManager);
		if (containerPropsCustomizer != null) {
			containerPropsCustomizer.customize(containerProps);
		}
		return containerProps;
	}

	@FunctionalInterface
	public interface TestPulsarContainerPropertiesCustomizer {

		void customize(PulsarContainerProperties containerProps);

	}

}
