/*
 * DISCLAIMER
 *
 * Copyright 2017 ArangoDB GmbH, Cologne, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright holder is ArangoDB GmbH, Cologne, Germany
 */

package com.arangodb.loadtest.worker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arangodb.ArangoDB;
import com.arangodb.loadtest.cli.CliOptions;
import com.arangodb.loadtest.testcase.DocumentWriteTestCase;
import com.arangodb.loadtest.util.DocumentCreator;
import com.arangodb.loadtest.util.KeyGen;

/**
 * @author Mark Vollmary
 *
 */
public class DocumentWriteWorker extends ArangoThreadWorker {

	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentWriteWorker.class);

	private final DocumentWriteTestCase writer;

	public DocumentWriteWorker(final ArangoDB.Builder builder, final CliOptions options, final int num,
		final Map<String, Collection<Long>> times, final DocumentCreator documentCreator) {
		super(options);
		final ArrayList<Long> l = new ArrayList<>();
		times.put("thread" + num, l);
		this.writer = new DocumentWriteTestCase(builder, options, new KeyGen(options, num), documentCreator, num, l);
	}

	@Override
	public void run() {
		try {
			for (int i = 0; i < options.getRuns(); i++) {
				writer.run();
			}
		} catch (final Exception e) {
			LOGGER.error("Failed to upload documents", e);
		}
	}

	@Override
	public void close() throws IOException {
		writer.close();
	}
}
