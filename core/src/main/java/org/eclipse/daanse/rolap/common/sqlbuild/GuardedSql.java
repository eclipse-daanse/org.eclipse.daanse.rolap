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
package org.eclipse.daanse.rolap.common.sqlbuild;

import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;

/**
 * The result of the {@link SqlBuildGuard} seam: the executable SQL together with the
 * {@link SelectStatement} it was decided against.
 *
 * <p>{@link #render()} is THE executable text — byte-authoritative. For the reference-win case it
 * is the reference's exact text, which is only whitespace-token-equal to a fresh render of
 * {@link #statement()}. {@link #statement()} is for composition; a composed statement's render must
 * pass its own byte gate before becoming authoritative.
 *
 * @param statement the statement the guard decided against (for composition, not execution)
 * @param render    the executable SQL text and column types (byte-authoritative)
 */
public record GuardedSql(SelectStatement statement, RenderedSql render) {
}
