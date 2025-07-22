/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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


package org.eclipse.daanse.rolap.common;

import java.util.List;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

/**
 * Condition which constrains a set of values of a single
 * {@link org.eclipse.daanse.rolap.common.RolapStar.Column} or a set of columns.
 *
 * For example, the predicate
 * Range([Time].[1997].[Q3], [Time].[1998].[Q2])
 * constrains the year and quarter columns:
 *
 *
 * &nbsp;&nbsp;((year = 1997 and quarter >= 'Q3')
 * &nbsp;&nbsp;&nbsp;&nbsp;or (year > 1997))
 * and ((year = 1998 and quarter less or equals 'Q2')
 * &nbsp;&nbsp;&nbsp;&nbsp;or (year less 1998))
 *
 * @author jhyde
  * @since Jan 15, 2007
 */
public interface StarPredicate {
    /**
     * Returns a list of constrained columns.
     *
     * @return List of constrained columns
     */
    public List<RolapStar.Column> getConstrainedColumnList();

    /**
     * Returns a bitmap of constrained columns to speed up comparison
     * @return bitmap representing all constraining columns.
     */
    public BitKey getConstrainedColumnBitKey();

    /**
     * Appends a description of this predicate to a StringBuilder.
     * For example:
     * =any
     * =5
     * in (2, 4, 6)
     *
     *
     * @param buf Builder to append to
     */
    public abstract void describe(StringBuilder buf);

    /**
     * Evaluates a constraint against a list of values.
     *
     * If one of the values is {@link #WILDCARD}, returns true if constraint is
     * true for all possible values of that column.
     *
     * @param valueList List of values, one for each constrained column
     * @return Whether constraint holds for given set of values
     */
    public boolean evaluate(List<Object> valueList);

    /**
     * Returns whether this Predicate has the same constraining effect as the
     * other constraint. This is weaker than {@link Object#equals(Object)}: it
     * is possible for two different members to constrain the same column in the
     * same way.
     *
     * @param that Other predicate
     * @return whether the other predicate is equivalent
     */
    boolean equalConstraint(StarPredicate that);

    /**
     * Returns the logical inverse of this Predicate. The result is a Predicate
     * which holds whenever this predicate holds but the other does not.
     *
     *  predicate != null
     * @param predicate Predicate
     * @return Combined predicate
     */
    StarPredicate minus(StarPredicate predicate);

    /**
     * Returns this union of this Predicate with another. The result is a
     * Predicate which holds whenever either predicate holds.
     *
     *  predicate != null
     * @param predicate Predicate
     * @return Combined predicate
     */
    StarPredicate or(StarPredicate predicate);

    /**
     * Returns this intersection of this Predicate with another. The result is a
     * Predicate which holds whenever both predicates hold.
     *
     *  predicate != null
     * @param predicate Predicate
     * @return Combined predicate
     */
    StarPredicate and(StarPredicate predicate);

    /**
     * Wildcard value for {@link #evaluate(java.util.List)}.
     */
    Object WILDCARD = new Object();

    void toSql(SqlQuery sqlQuery, StringBuilder buf);
}
