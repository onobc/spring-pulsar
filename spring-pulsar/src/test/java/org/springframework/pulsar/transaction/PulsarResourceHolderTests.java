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

package org.springframework.pulsar.transaction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.pulsar.client.api.transaction.Transaction;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PulsarResourceHolder}.
 *
 * @author Chris Bono
 */
class PulsarResourceHolderTests {

	@Test
	void rollbackAbortsTransaction() {
		var txn = mock(Transaction.class);
		when(txn.abort()).thenReturn(CompletableFuture.completedFuture(null));
		var holder = new PulsarResourceHolder(txn);
		holder.rollback();
		verify(txn).abort();
	}

	@Test
	void multipleCommitCallsCommitsTransactionOnce() {
		var txn = mock(Transaction.class);
		when(txn.commit()).thenReturn(CompletableFuture.completedFuture(null));
		var holder = new PulsarResourceHolder(txn);
		holder.commit();
		holder.commit();
		holder.commit();
		verify(txn).commit();
	}

}
