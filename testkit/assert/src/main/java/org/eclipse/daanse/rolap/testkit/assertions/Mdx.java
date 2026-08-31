/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.testkit.assertions;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.execution.ExecutionImpl;

/**
 * Plain MDX execution - no assertion attached. Replaces the legacy {@code TestUtil.executeQuery}
 * for callers that need the raw {@link Result} to inspect or process themselves; {@link MdxAssert}
 * (built on {@link #execute}) is for callers that want a pass/fail check instead.
 */
public final class Mdx {

    private Mdx() {
    }

    /**
     * Runs {@code mdx} over {@code connection} with a 5-minute execution timeout and returns the raw
     * {@link Result} - replaces the legacy {@code TestUtil.executeQuery(Connection, String)}.
     */
    public static Result executeQuery(Connection connection, String mdx) {
        return executeQuery(connection, mdx, Duration.ofMinutes(5));
    }

    /**
     * Runs {@code mdx} over {@code connection}, bounded by {@code timeout}, and returns the raw
     * {@link Result} - replaces the legacy {@code TestUtil.executeQuery(Connection, String, long)}.
     */
    public static Result executeQuery(Connection connection, String mdx, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        return execute(connection, mdx, Optional.of(timeout));
    }

    /** Shared primitive behind {@link #executeQuery} and {@link MdxAssert}'s builders: parse, then run. */
    static Result execute(Connection connection, String mdx, Optional<Duration> timeout) {
        Query query = connection.parseQuery(mdx);
        Statement statement = query.getStatement();
        return statement.getDaanseConnection().execute(new ExecutionImpl(statement, timeout));
    }
}
