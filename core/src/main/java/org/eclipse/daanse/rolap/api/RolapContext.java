/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.api;

import java.util.Optional;

import org.eclipse.daanse.jdbc.datasource.pools.api.ConnectionPool;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.relational.RelationalContext;
import org.eclipse.daanse.rolap.api.aggmatch.AggregationMatchRulesSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;

public interface RolapContext extends RelationalContext<Connection> {

	Catalog getCatalogMapping();

	/**
	 * SHA-256 content identity of this context (mapping hash + database
	 * identity); catalog cache keys and SegmentHeader.schemaChecksum build on
	 * it, so only instances serving the same catalog content against the same
	 * database share segments.
	 */
	byte[] getContentIdentitySha256();

	Optional<AggregationMatchRulesSupplier> getAggMatchRulesSupplier();

	/**
	 * The pool the context draws its JDBC connections from.
	 * <p>
	 * Declared here and deliberately not on {@link RelationalContext}: the olap
	 * api names the data source only, while {@link ConnectionPool} is a type of
	 * the pool implementation. Callers that only need to open a connection keep
	 * using {@link RelationalContext#getDataSource()},
	 * which already returns the pool's own DataSource. This getter is for the ones
	 * that have to bound their wait.
	 */
	ConnectionPool getConnectionPool();
}
