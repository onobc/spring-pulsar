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

package org.springframework.pulsar.sample.signup.model;

public record CustomerCard(String text, Block... blocks) {

	public static CustomerCard from(Signup signup) {
		String fullName = "%s %s".formatted(signup.firstName(), signup.lastName());
		String text = "New customer onboarded (%s)".formatted(fullName);
		String markdown = ":tada: *New customer onboarded* :tada:\n\t\t%s (%s)".formatted(fullName, signup.email());
		return new CustomerCard(text, Block.withMarkdown(markdown));
	}

	public record Block(String type, Text text) {

		public static Block withMarkdown(String markdown) {
			return new Block("section", new Text("mrkdwn", markdown));
		}
	}

	public record Text(String type, String text) {
	}
}
