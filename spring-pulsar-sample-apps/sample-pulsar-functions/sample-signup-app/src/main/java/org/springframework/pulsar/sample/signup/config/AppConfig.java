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

package org.springframework.pulsar.sample.signup.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pulsar.common.io.SinkConfig;
import org.apache.pulsar.common.io.SourceConfig;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.pulsar.function.PulsarFunctionOperations.FunctionStopPolicy;
import org.springframework.pulsar.function.PulsarSink;
import org.springframework.pulsar.function.PulsarSource;
import org.springframework.pulsar.sample.signup.model.SignupGenerator;

@Configuration(proxyBeanMethods = false)
class AppConfig {

	@Bean
	SignupGenerator signupGenerator() {
		return new SignupGenerator();
	}

	// Needed for RabbitTemplate sending
	@Bean
	Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	PulsarSource userSignupRabbitSource() {
		Map<String, Object> configs = new HashMap<>();
		configs.put("host", "rabbitmq");
		configs.put("port", 5672);
		configs.put("virtualHost", "/");
		configs.put("username", "guest");
		configs.put("password", "guest");
		configs.put("queueName", "user_signup_queue");
		configs.put("connectionName", "user_signup_pulsar_source");
		SourceConfig sourceConfig = SourceConfig.builder()
				.tenant("public")
				.namespace("default")
				.name("UserSignupRabbitSource")
				.archive("builtin://rabbitmq")
				.topicName("user-signup-topic")
				.configs(configs)
				.build();
		return new PulsarSource(sourceConfig, FunctionStopPolicy.DELETE, null);
	}

	@Bean
	PulsarSink customerOnboardHttpSink() {
		Map<String, Object> configs = new HashMap<>();
		configs.put("url", "https://hooks.slack.com/services/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");
		//configs.put("url", "http://docker.for.mac.localhost:9090/slackProxy");
		SinkConfig sinkConfig = SinkConfig.builder()
				.tenant("public")
				.namespace("default")
				.name("CustomerOnboardHttpSink")
				.archive("builtin://http")
				.inputs(List.of("customer-onboard-topic"))
				.configs(configs).build();
		return new PulsarSink(sinkConfig, FunctionStopPolicy.DELETE, null);
	}

}
