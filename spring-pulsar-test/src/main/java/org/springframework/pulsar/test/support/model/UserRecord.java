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

package org.springframework.pulsar.test.support.model;

/**
 * Test object (user) defined via a Java record.
 *
 * @param name the user's name
 * @param age the user's age
 * @deprecated this class is replaced with Gradle test fixtures and is only meant to be
 * used internally.
 */
@Deprecated(since = "1.2.0", forRemoval = true)
public record UserRecord(String name, int age) {
}
