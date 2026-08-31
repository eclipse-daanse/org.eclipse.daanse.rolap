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

import org.eclipse.daanse.olap.api.connection.Connection;

/**
 * SQL dialect lookup for a connection - replaces the legacy {@code TestUtil.getDialect}.
 *
 * <p>
 * Named {@code Dialect} rather than e.g. {@code Dialects} to mirror the legacy method name at the
 * call site ({@code import static ...Dialect.getDialect;}); callers that also need the SQL dialect
 * type itself keep using {@code org.eclipse.daanse.sql.dialect.api.Dialect} for that - the two
 * classes never need to be imported unqualified in the same file at once.
 */
public final class Dialect {

    private Dialect() {
    }

    /** The SQL dialect {@code connection}'s context is configured with. */
    public static org.eclipse.daanse.sql.dialect.api.Dialect getDialect(Connection connection) {
        return connection.getContext().getDialect();
    }
}
