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
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.api.aggmatch.AggregationMatchRulesSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;

public interface RolapContext extends Context<Connection> {

	Catalog getCatalogMapping();

	Optional<AggregationMatchRulesSupplier> getAggMatchRulesSupplier();

	/**
	 * The pool the context draws its JDBC connections from.
	 * <p>
	 * Declared here and deliberately not on {@link Context}: the olap api is the
	 * logical OLAP view and stays free of JDBC types, while
	 * {@link ConnectionPool} hands out {@link java.sql.Connection}. Callers that
	 * only need to open a connection keep using {@link Context#getDataSource()},
	 * which already returns the pool's own DataSource. This getter is for the ones
	 * that have to bound their wait.
	 */
	ConnectionPool getConnectionPool();
}
