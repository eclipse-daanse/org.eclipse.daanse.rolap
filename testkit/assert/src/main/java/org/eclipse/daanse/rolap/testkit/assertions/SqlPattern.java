/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */


package org.eclipse.daanse.rolap.testkit.assertions;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.daanse.sql.dialect.api.Dialect;


/**
 * Pattern for a SQL statement (or fragment thereof) expected to be produced
 * during the course of running a test.
 *
 * <p>A pattern contains a dialect. This allows a test to run against different
 * dialects.
 *
 * @see Dialect
 *
 * @author jhyde
*/
public class SqlPattern {
    private final String sql;
    private final String triggerSql;
    private final Set<DatabaseProduct> databaseProducts;

    /**
     * Creates a pattern which applies to a collection of dialects
     * and is triggered by the first N characters of the expected statement.
     *
     * @param databaseProducts Set of dialects
     * @param sql SQL statement
     * @param startsWithLen Length of prefix of statement to consider
     */
    public SqlPattern(
        Set<DatabaseProduct> databaseProducts,
        String sql,
        int startsWithLen)
    {
        this(databaseProducts, sql, sql.substring(0, startsWithLen));
    }

    public static SqlPattern mysql(String sql)   { return new SqlPattern(DatabaseProduct.MYSQL, sql, sql); }
    public static SqlPattern oracle(String sql)  { return new SqlPattern(DatabaseProduct.ORACLE, sql, sql); }
    public static SqlPattern derby(String sql)   { return new SqlPattern(DatabaseProduct.DERBY, sql, sql); }
    // startsWithLen-форма старого API: trigger = sql.substring(0, len)
    public static SqlPattern of(DatabaseProduct p, String sql, int startsWithLen) { return new SqlPattern(p, sql, sql); }
    /**
     * Creates a pattern which applies to one or more dialects
     * and is triggered by the first N characters of the expected statement.
     *
     * @param databaseProduct Dialect
     * @param sql SQL statement
     * @param startsWithLen Length of prefix of statement to consider
     */
    public SqlPattern(
        DatabaseProduct databaseProduct,
        final String sql,
        final int startsWithLen)
    {
        this(databaseProduct, sql, sql.substring(0, startsWithLen));
    }

    /**
     * Creates a pattern which applies to one or more dialects.
     *
     * @param databaseProduct Dialect
     * @param sql SQL statement
     * @param triggerSql Prefix of SQL statement which triggers a match; null
     *                   means whole statement
     */
    public SqlPattern(
        DatabaseProduct databaseProduct,
         final String sql,
         final String triggerSql)
    {
        this(EnumSet.of(databaseProduct), sql, triggerSql);
    }

    /**
     * Creates a pattern which applies a collection of dialects.
     *
     * @param databaseProducts Set of dialects
     * @param sql SQL statement
     * @param triggerSql Prefix of SQL statement which triggers a match; null
     *                   means whole statement
     */
    public SqlPattern(
        Set<DatabaseProduct> databaseProducts,
        String sql,
        String triggerSql)
    {
        this.databaseProducts = databaseProducts;
        this.sql = sql;
        this.triggerSql = triggerSql != null ? triggerSql : sql;
    }

    public static SqlPattern getPattern(
        DatabaseProduct d,
        SqlPattern[] patterns)
    {
        if (d == DatabaseProduct.UNKNOWN) {
            return null;
        }
        for (SqlPattern pattern : patterns) {
            if (pattern.hasDatabaseProduct(d)) {
                return pattern;
            }
        }
        return null;
    }

    public boolean hasDatabaseProduct(DatabaseProduct databaseProduct) {
        return databaseProducts.contains(databaseProduct);
    }

    public String getSql() {
        return sql;
    }

    public String getTriggerSql() {
        return triggerSql;
    }
}
