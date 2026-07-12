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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.sql;

import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;

/**
 * A built SELECT: the executable {@link RenderedSql} together with the {@link SelectStatement} it
 * was rendered from. Produced by
 * {@code org.eclipse.daanse.rolap.common.sqlbuild.QueryBuildContext#build} on the builder paths, and by
 * {@link QueryRecorder} on the recorder fallback paths.
 *
 * <p>{@link #render()} is THE executable text — authoritative; consumers execute it as-is.
 * {@link #statement()} carries the statement for composition (the union-arm SetOperation wrapper in
 * {@code SqlTupleReader} recombines arm statements); a composed statement becomes executable only
 * through its own render into a new {@code BuiltSql}.
 *
 * <p>Lives in this exported package next to {@link QueryRecorder} so the recorder's public API
 * does not reference the bundle-private {@code sqlbuild} package.
 *
 * @param statement the dialect-free statement the SQL was rendered from (for composition, not execution)
 * @param render    the executable SQL text and column types
 */
public record BuiltSql(SelectStatement statement, RenderedSql render) {
}
