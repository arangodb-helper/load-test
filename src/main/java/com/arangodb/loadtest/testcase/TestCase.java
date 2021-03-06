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

package com.arangodb.loadtest.testcase;

/**
 * @author Mark Vollmary
 *
 */
public enum TestCase {
	VERSION,
	DOCUMENT_GET,
	DOCUMENT_INSERT,
	DOCUMENT_IMPORT,
	DOCUMENT_UPDATE,
	DOCUMENT_REPLACE,
	AQL_CUSTOM,
	AQL_GET,
	AQL_INSERT,
	AQL_REPLACE,
	VERTEX_GET,
	VERTEX_INSERT,
	VERTEX_UPDATE,
	VERTEX_REPLACE,
	EDGE_GET,
	EDGE_INSERT,
	EDGE_UPDATE,
	EDGE_REPLACE
}
