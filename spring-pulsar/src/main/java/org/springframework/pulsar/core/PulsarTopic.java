/*
 * Copyright 2022-2023 the original author or authors.
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

import org.apache.pulsar.common.naming.TopicDomain;

import org.springframework.util.Assert;

/**
 * Model class for a Pulsar topic.
 *
 * Use the {@link PulsarTopicBuilder} to create instances like this: <pre>{@code
 * 	PulsarTopic topic = new PulsarTopicBuilder().name("topic-name").build();
 * }</pre>
 *
 * @param topicName the fully qualified topic name in the format
 * {@code 'domain://tenant/namespace/name'}
 * @param numberOfPartitions the number of partitions, or 0 for non-partitioned topics
 * @author Alexander Preuß
 * @author Chris Bono
 */
public record PulsarTopic(String topicName, int numberOfPartitions) {

	public PulsarTopic {
		Assert.state(topicName.split("/").length == 5,
				"topicName must be fully qualified (e.g. persistent://public/default/my-topic)");
	}

	/**
	 * Convenience method to create a topic builder with the specified topic name.
	 * @param topicName the name of the topic
	 * @return the topic builder instance
	 * @deprecated As of version 1.2.0 topic builder is a registered bean - instead use an
	 * injected instance where needed
	 */
	@Deprecated(since = "1.2.0", forRemoval = true)
	public static PulsarTopicBuilder builder(String topicName) {
		return new PulsarTopicBuilder().name(topicName);
	}

	/**
	 * Checks if the topic is partitioned.
	 * @return true if the topic is partitioned
	 */
	public boolean isPartitioned() {
		return this.numberOfPartitions != 0;
	}

	/**
	 * Get the individual identifying components of a Pulsar topic.
	 * @return {@link TopicComponents}
	 */
	public TopicComponents getComponents() {
		var splitTopic = this.topicName().split("/");
		var type = splitTopic[0].replace(":", "");
		return new TopicComponents(TopicDomain.getEnum(type), splitTopic[2], splitTopic[3], splitTopic[4]);
	}

	/**
	 * Get the fully-qualified name of this topic in the format
	 * {@code domain://tenant/namespace/name} where the components have the following
	 * defaults when not specified in the original topic name used to build this topic.
	 * <pre>
	 * - {@code domain} is one of ('persistent', 'non-persistent') with a default of 'persistent'
	 * - {@code tenant} has default of 'public'
	 * - {@code namespace} has default of 'default'
	 * </pre>
	 * @return the fully-qualified topic name
	 */
	public String getFullyQualifiedTopicName() {
		TopicComponents components = this.getComponents();
		return components.domain + "://" + components.tenant + "/" + components.namespace + "/" + components.name;
	}

	/**
	 * Model class for the individual identifying components of a Pulsar topic.
	 *
	 * @param domain the topic domain
	 * @param tenant the topic tenant
	 * @param namespace the topic namespace
	 * @param name the topic name
	 */
	record TopicComponents(TopicDomain domain, String tenant, String namespace, String name) {

	}
}
