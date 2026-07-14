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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/**
 * The one adapter from the full {@link Dialect} to the narrow {@link SqlQueryCapabilities}
 * producer view — together with the render/executor seam the only place allowed to hold a
 * {@code Dialect} (see {@code DialectConfinementInvariantTest}).
 */
final class DialectSqlQueryCapabilities implements SqlQueryCapabilities {

    private final Dialect dialect;

    DialectSqlQueryCapabilities(Dialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public boolean countDistinct() {
        return dialect.allowsCountDistinct();
    }

    @Override
    public boolean multipleCountDistinct() {
        return dialect.allowsMultipleCountDistinct();
    }

    @Override
    public boolean countDistinctWithOtherAggs() {
        return dialect.allowsCountDistinctWithOtherAggs();
    }

    @Override
    public boolean multipleDistinctSqlMeasures() {
        return dialect.allowsMultipleDistinctSqlMeasures();
    }

    @Override
    public boolean compoundCountDistinct() {
        return dialect.allowsCompoundCountDistinct();
    }

    @Override
    public boolean innerDistinct() {
        return dialect.allowsInnerDistinct();
    }

    @Override
    public boolean fromQuery() {
        return dialect.allowsFromQuery();
    }

    @Override
    public boolean fieldAlias() {
        return dialect.allowsFieldAlias();
    }

    @Override
    public boolean groupingSets() {
        return dialect.supportsGroupingSets();
    }

    @Override
    public boolean unlimitedValueList() {
        return dialect.supportsUnlimitedValueList();
    }

    @Override
    public boolean regexInWhere() {
        return dialect.allowsRegularExpressionInWhereClause();
    }

    @Override
    public boolean drillthroughMaxRowsInLimit() {
        return dialect.requiresDrillthroughMaxRowsInLimit();
    }

    @Override
    public int maxColumnNameLength() {
        return dialect.getMaxColumnNameLength();
    }
}
