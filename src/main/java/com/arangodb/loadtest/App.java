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

package com.arangodb.loadtest;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDB.Builder;
import com.arangodb.ArangoDBException;
import com.arangodb.loadtest.cli.CliOptionUtils;
import com.arangodb.loadtest.cli.CliOptions;
import com.arangodb.loadtest.util.DocumentCreator;
import com.arangodb.loadtest.worker.ArangoThreadWorker;
import com.arangodb.loadtest.worker.DocumentReadWorker;
import com.arangodb.loadtest.worker.DocumentWriteWorker;
import com.arangodb.model.CollectionCreateOptions;

/**
 * 
 * @author Mark Vollmary
 *
 */
public class App {

	enum TestCase {
		READ, WRITE
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
	private static final String USAGE_INFO = "java -jar arangodb-load-test.jar";

	public static void main(final String[] args) {
		final App app = new App();
		final CommandLineParser parser = new BasicParser();
		final Options opts = CliOptionUtils.createOptions();
		CommandLine cmd;
		CliOptions options;
		try {
			cmd = parser.parse(opts, args);
			options = CliOptionUtils.readOptions(cmd);
		} catch (final ParseException e) {
			System.err.println(e);
			new HelpFormatter().printHelp(USAGE_INFO, opts);
			System.exit(1);
			return;
		}
		final ArangoDB.Builder builder = new ArangoDB.Builder().useProtocol(options.getProtocol())
				.user(options.getUser()).password(options.getPassword())
				.loadBalancingStrategy(options.getLoadBalancing()).acquireHostList(options.getAcquireHostList())
				.maxConnections(options.getConnections());

		Stream.of(options.getEndpoints().split(",")).map(e -> e.split(":")).filter(e -> e.length == 2)
				.forEach(e -> builder.host(e[0], Integer.valueOf(e[1])));
		try {
			final String caseString = options.getTest();
			if (caseString == null) {
				new HelpFormatter().printHelp(USAGE_INFO, opts);
				System.exit(1);
			}

			final List<TestCase> tests = Stream.of(caseString.split(",")).map(t -> TestCase.valueOf(t.toUpperCase()))
					.collect(Collectors.toList());
			run(app, options, builder, tests, System.out);
		} catch (final Exception e) {
			LOGGER.error("Failed", e);
		}
	}

	private static void run(
		final App app,
		final CliOptions options,
		final ArangoDB.Builder builder,
		final List<TestCase> tests,
		final PrintStream out) throws InterruptedException, IOException {
		for (int i = 0; i < options.getRuns(); i++) {
			out.println("RUN " + (i + 1));
			final boolean dropDB = (options.getDropDB() != null && options.getDropDB().booleanValue()) || i > 0;
			app.setup(builder, options, dropDB);
			for (final TestCase test : tests) {
				if (test == TestCase.READ) {
					app.run(options, test, (num, times) -> new DocumentReadWorker(builder, options, num, times), out);
				} else if (test == TestCase.WRITE) {
					app.run(options, test, (num, times) -> new DocumentWriteWorker(builder, options, num, times,
							new DocumentCreator(options)),
						out);
				}
			}
		}
	}

	private void setup(final Builder builder, final CliOptions options, final boolean dropDB) {
		final ArangoDB arangoDB = builder.build();
		final String database = options.getDatabase();
		if (dropDB) {
			try {
				arangoDB.db(database).drop();
			} catch (final ArangoDBException e) {
			}
		}
		try {
			arangoDB.createDatabase(database);
		} catch (final ArangoDBException e) {
			if (!arangoDB.db(database).exists()) {
				LOGGER.error(String.format("Failed to create database: %s", database));
			}
		}
		final String collection = options.getCollection();
		try {
			arangoDB.db(database).createCollection(collection,
				new CollectionCreateOptions().numberOfShards(options.getNumberOfShards())
						.replicationFactor(options.getReplicationFactor()).waitForSync(options.getWaitForSync()));
		} catch (final Exception e) {
			if (!arangoDB.db(database).collection(collection).exists()) {
				LOGGER.error(String.format("Failed to create collection %s", collection));
			}
		}
		arangoDB.shutdown();
	}

	public interface ThreadWorkerCreator {
		ArangoThreadWorker create(int num, Map<String, Collection<Long>> times);
	}

	private void run(
		final CliOptions options,
		final TestCase testCase,
		final ThreadWorkerCreator creator,
		final PrintStream out) throws InterruptedException, IOException {
		out.println(String.format("TEST CASE \"%s\". %s threads, %s connections/thread, %s protocol",
			testCase.toString().toLowerCase(), options.getThreads(), options.getConnections(),
			options.getProtocol().toString().toLowerCase()));

		final Map<String, Collection<Long>> times = new ConcurrentHashMap<>();
		final ArangoThreadWorker[] workers = new ArangoThreadWorker[options.getThreads()];
		for (int i = 0; i < workers.length; i++) {
			workers[i] = creator.create(i, times);
		}
		for (int i = 0; i < workers.length; i++) {
			workers[i].start();
		}
		collectData(options, times, testCase.toString().toLowerCase(), out);
		for (int i = 0; i < workers.length; i++) {
			workers[i].join();
		}
		for (int i = 0; i < workers.length; i++) {
			workers[i].close();
		}
	}

	private void collectData(
		final CliOptions options,
		final Map<String, Collection<Long>> times,
		final String type,
		final PrintStream out) {
		final Integer numThreads = options.getThreads();
		final int batchSize = options.getBatchSize();
		int currentOp = 0;
		final int sleep = options.getOutputInterval() * 1000;
		while (currentOp < options.getRequests()) {
			try {
				Thread.sleep(sleep);
			} catch (final InterruptedException e) {
			}
			final List<Long> requests = new ArrayList<>();
			times.values().forEach(requests::addAll);
			times.values().forEach(Collection::clear);
			currentOp += requests.size();
			Collections.sort(requests);
			final int numRequests = requests.size();
			final Long average, min, max, p50th, p95th, p99th;
			if (numRequests > 0) {
				average = requests.stream().reduce((a, b) -> a + b).map(e -> e / numRequests).orElse(0L);
				min = requests.get(0);
				max = requests.get(numRequests - 1);
				p50th = requests.get((int) ((numRequests * 0.5) - 1));
				p95th = requests.get((int) ((numRequests * 0.95) - 1));
				p99th = requests.get((int) ((numRequests * 0.99) - 1));
			} else {
				average = min = max = p50th = p95th = p99th = 0L;
			}
			out.println(String.format(
				"Within the last %s sec: Threads %s, %s requests %s, Documents %s, Latency[Average: %s ms, Min: %s ms, Max: %s ms, 50th: %s ms, 95th: %s ms, 99th: %s ms]",
				sleep / 1000, numThreads, type, numRequests, numRequests * batchSize, average, min, max, p50th, p95th,
				p99th));
		}
	}

}
