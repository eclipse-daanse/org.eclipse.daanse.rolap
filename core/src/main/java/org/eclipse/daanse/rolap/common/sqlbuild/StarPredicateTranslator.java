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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.LiteralStarPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * Translates a ROLAP {@link StarPredicate} into the generic builder's dialect-free
 * {@link Predicate}. This is the constraint subsystem's contribution contract: instead of
 * appending SQL strings, a constraint contributes a {@link Predicate} the renderer spells.
 * <p>
 * Handles the common impls: {@link ValueColumnPredicate} → {@code col = value} (or {@code IS
 * NULL}); {@link ListColumnPredicate} → {@code col IN (...)} when every child is a value on the
 * same column, else an {@code OR}; {@link AndPredicate}/{@link OrPredicate} → {@code AND}/{@code
 * OR}; {@link LiteralStarPredicate} → always-true/always-false (the renderer emits {@code 1=1} /
 * {@code 1=0}). Columns are quoted via {@link JoinPlanner#expressionFor(RolapStar.Column)}; literals carry the star
 * column's {@code Datatype}, so the renderer quotes each value per its type.
 */
public final class StarPredicateTranslator {

    private StarPredicateTranslator() {
    }

    /**
     * Whether the predicate is the always-true literal (an unconstrained column). It adds no
     * restriction, so callers should skip it rather than emit {@code 1 = 1}.
     */
    public static boolean isAlwaysTrue(StarPredicate predicate) {
        return predicate instanceof LiteralStarPredicate literal && literal.getValue();
    }

    public static Predicate toPredicate(StarPredicate predicate) {
        if (predicate instanceof ValueColumnPredicate value) {
            return valuePredicate(value);
        }
        if (predicate instanceof ListColumnPredicate list) {
            return listPredicate(list);
        }
        if (predicate instanceof AndPredicate and) {
            return Predicates.and(translateChildren(and.getChildren()));
        }
        if (predicate instanceof OrPredicate or) {
            return orPredicate(or);
        }
        if (predicate instanceof LiteralStarPredicate literal) {
            // true → always-true (renders 1=1); false → always-false (renders 1=0).
            return literal.getValue() ? Predicates.and(List.of()) : Predicates.or(List.of());
        }
        throw new IllegalArgumentException("unsupported StarPredicate: " + predicate.getClass().getName());
    }

    /**
     * Like {@link #toPredicate} for a single-column {@link org.eclipse.daanse.rolap.common.star.StarColumnPredicate},
     * but renders the column with the supplied {@code col} expression instead of the predicate's own column —
     * used to substitute an aggregate-table column for the base column. Handles the shapes
     * {@code getColumnPredicates} produces (a {@link ValueColumnPredicate}, a {@link ListColumnPredicate} of
     * value predicates, or a {@link LiteralStarPredicate}); falls back to {@link #toPredicate} (the predicate's
     * own column) for anything else.
     */
    public static Predicate toColumnPredicate(
            org.eclipse.daanse.rolap.common.star.StarColumnPredicate predicate, SqlExpression col) {
        if (predicate instanceof ValueColumnPredicate value) {
            Object v = value.getValue();
            if (v == null || v == Util.sqlNullValue) {
                return Predicates.isNull(col);
            }
            return Predicates.comparison(col, ComparisonOperator.EQ,
                    Expressions.literal(v, value.getConstrainedColumn().getDatatype()));
        }
        if (predicate instanceof ListColumnPredicate list) {
            List<org.eclipse.daanse.rolap.common.star.StarColumnPredicate> children = list.getPredicates();
            boolean allValues = !children.isEmpty()
                    && children.stream().allMatch(ValueColumnPredicate.class::isInstance);
            if (allValues) {
                List<SqlExpression> values = new ArrayList<>();
                boolean hasNull = false;
                for (org.eclipse.daanse.rolap.common.star.StarColumnPredicate child : children) {
                    ValueColumnPredicate v = (ValueColumnPredicate) child;
                    Object val = v.getValue();
                    if (val == null || val == Util.sqlNullValue) {
                        hasNull = true;
                        continue;
                    }
                    values.add(Expressions.literal(val, v.getConstrainedColumn().getDatatype()));
                }
                Predicate base;
                if (values.isEmpty()) {
                    return Predicates.isNull(col);
                } else if (values.size() == 1) {
                    base = Predicates.comparison(col, ComparisonOperator.EQ, values.get(0));
                } else {
                    base = Predicates.in(col, values);
                }
                return hasNull ? Predicates.or(List.of(base, Predicates.isNull(col))) : base;
            }
        }
        if (predicate instanceof LiteralStarPredicate literal) {
            return literal.getValue() ? Predicates.and(List.of()) : Predicates.or(List.of());
        }
        return toPredicate(predicate);
    }

    /** {@code col = value}, or {@code col IS NULL} when the value is null. */
    private static Predicate valuePredicate(ValueColumnPredicate value) {
        RolapStar.Column column = value.getConstrainedColumn();
        SqlExpression col = JoinPlanner.expressionFor(column);
        Object v = value.getValue();
        // The null sentinel Util.sqlNullValue is NOT Java null; compare by reference (its toString() is
        // "#null", which must never leak into SQL).
        if (v == null || v == Util.sqlNullValue) {
            return Predicates.isNull(col);
        }
        return Predicates.comparison(col, ComparisonOperator.EQ,
                Expressions.literal(v, column.getDatatype()));
    }

    /**
     * {@code col = v} for a single value, {@code col IN (v1, v2, …)} for several (a one-element
     * list renders as equality, not {@code IN}), else an {@code OR} of the translated children.
     */
    private static Predicate listPredicate(ListColumnPredicate list) {
        List<org.eclipse.daanse.rolap.common.star.StarColumnPredicate> children = list.getPredicates();
        boolean allValues = !children.isEmpty()
                && children.stream().allMatch(ValueColumnPredicate.class::isInstance);
        if (allValues) {
            SqlExpression col = JoinPlanner.expressionFor(list.getConstrainedColumn());
            // Split the null sentinel out of the value list — it must render as IS NULL, not as the
            // literal "#null". Three cases:
            //   col IS NULL  /  (col = v or col is null)  /  (col in (...) or col is null).
            List<SqlExpression> values = new ArrayList<>();
            boolean hasNull = false;
            for (org.eclipse.daanse.rolap.common.star.StarColumnPredicate child : children) {
                ValueColumnPredicate v = (ValueColumnPredicate) child;
                Object val = v.getValue();
                if (val == null || val == Util.sqlNullValue) {
                    hasNull = true;
                    continue;
                }
                values.add(Expressions.literal(val, v.getConstrainedColumn().getDatatype()));
            }
            Predicate base;
            if (values.isEmpty()) {
                return Predicates.isNull(col);
            } else if (values.size() == 1) {
                base = Predicates.comparison(col, ComparisonOperator.EQ, values.get(0));
            } else {
                base = Predicates.in(col, values);
            }
            return hasNull ? Predicates.or(List.of(base, Predicates.isNull(col))) : base;
        }
        List<Predicate> parts = new ArrayList<>();
        for (org.eclipse.daanse.rolap.common.star.StarColumnPredicate child : children) {
            parts.add(toPredicate(child));
        }
        return Predicates.or(parts);
    }

    /**
     * An {@code OR} of predicates, collapsing a run of same-column value predicates into a single
     * {@code IN}. When every child is a non-null {@link ValueColumnPredicate} on one column
     * the result is {@code col IN (...)} (wrapped in the {@code OR}'s parentheses);
     * otherwise the children are {@code OR}-ed as-is.
     */
    private static Predicate orPredicate(OrPredicate or) {
        List<StarPredicate> children = or.getChildren();
        // Each child must reduce to one value per column (a bare ValueColumnPredicate, or an
        // AndPredicate of them — a slicer tuple member). Otherwise fall back to a plain OR.
        List<List<ValueColumnPredicate>> rows = new ArrayList<>();
        for (StarPredicate child : children) {
            List<ValueColumnPredicate> row = columnValues(child);
            if (row == null) {
                return Predicates.or(translateChildren(children));
            }
            rows.add(row);
        }
        if (rows.size() <= 1) {
            return Predicates.or(translateChildren(children));
        }
        // The constrained columns, ordered by bit position (ascending BitKey order — the canonical
        // column order); every row must constrain exactly this column set.
        List<RolapStar.Column> columns = new ArrayList<>();
        for (ValueColumnPredicate v : rows.get(0)) {
            columns.add(v.getConstrainedColumn());
        }
        columns.sort(Comparator.comparingInt(RolapStar.Column::getBitPosition));
        // Each row's column->value map; every row must constrain exactly the row-0 column set.
        List<Map<Integer, ValueColumnPredicate>> rowMaps = new ArrayList<>();
        for (List<ValueColumnPredicate> row : rows) {
            Map<Integer, ValueColumnPredicate> byColumn = new HashMap<>();
            for (ValueColumnPredicate v : row) {
                byColumn.put(v.getConstrainedColumn().getBitPosition(), v);
            }
            if (byColumn.size() != columns.size()) {
                return Predicates.or(translateChildren(children));
            }
            for (RolapStar.Column col : columns) {
                if (!byColumn.containsKey(col.getBitPosition())) {
                    return Predicates.or(translateChildren(children));
                }
            }
            rowMaps.add(byColumn);
        }
        // FACTORING: a column NULL in EVERY row is hoisted out as `col IS NULL`
        // (it can't be an IN-list value); a column NON-NULL in every row is an IN-tuple column. A column
        // null in some rows but not others can't be factored cleanly — fall back to a plain OR.
        List<RolapStar.Column> nullColumns = new ArrayList<>();
        List<RolapStar.Column> inListColumns = new ArrayList<>();
        for (RolapStar.Column col : columns) {
            int nulls = 0;
            for (Map<Integer, ValueColumnPredicate> m : rowMaps) {
                if (isNullValue(m.get(col.getBitPosition()))) {
                    nulls++;
                }
            }
            if (nulls == rowMaps.size()) {
                nullColumns.add(col);
            } else if (nulls == 0) {
                inListColumns.add(col);
            } else {
                return Predicates.or(translateChildren(children));
            }
        }
        List<Predicate> conj = new ArrayList<>();
        for (RolapStar.Column col : nullColumns) {
            conj.add(Predicates.isNull(JoinPlanner.expressionFor(col)));
        }
        if (!inListColumns.isEmpty()) {
            List<List<SqlExpression>> valueRows = new ArrayList<>();
            for (Map<Integer, ValueColumnPredicate> m : rowMaps) {
                List<SqlExpression> vals = new ArrayList<>();
                for (RolapStar.Column col : inListColumns) {
                    ValueColumnPredicate v = m.get(col.getBitPosition());
                    vals.add(Expressions.literal(v.getValue(), col.getDatatype()));
                }
                valueRows.add(vals);
            }
            if (inListColumns.size() == 1) {
                List<SqlExpression> flat = new ArrayList<>();
                for (List<SqlExpression> r : valueRows) {
                    flat.add(r.get(0));
                }
                conj.add(Predicates.in(JoinPlanner.expressionFor(inListColumns.get(0)), flat));
            } else {
                List<SqlExpression> colExprs = new ArrayList<>();
                for (RolapStar.Column col : inListColumns) {
                    colExprs.add(JoinPlanner.expressionFor(col));
                }
                conj.add(Predicates.inTuple(colExprs, valueRows));
            }
        }
        // Parenthesisation must match the reference SQL (the guard byte-compares): the OR's outer "("
        // plus the factored group's "(" — and, for a multi-column list, the extra tuple "(". The
        // factored null columns are AND-ed BEFORE the IN-list. or([ and([ nulls.., inList ]) ])
        // renders exactly that pair.
        return Predicates.or(List.of(Predicates.and(conj)));
    }

    /** The one-value-per-column predicates a child contributes to an {@code IN}/tuple-{@code IN}: a
     *  bare {@link ValueColumnPredicate}, or the values of an {@link AndPredicate} of them — INCLUDING
     *  null-valued predicates (a column null across every row is factored to {@code col IS NULL} in
     *  {@link #orPredicate}). {@code null} if the child is anything else. */
    private static List<ValueColumnPredicate> columnValues(StarPredicate child) {
        if (child instanceof ValueColumnPredicate v) {
            return List.of(v);
        }
        if (child instanceof AndPredicate and) {
            List<ValueColumnPredicate> values = new ArrayList<>();
            for (StarPredicate c : and.getChildren()) {
                if (c instanceof ValueColumnPredicate v) {
                    values.add(v);
                } else {
                    return null;
                }
            }
            return values.isEmpty() ? null : values;
        }
        return null;
    }

    /** Whether a per-column predicate carries the SQL null value (rendered {@code col IS NULL}). */
    private static boolean isNullValue(ValueColumnPredicate v) {
        return v == null || v.getValue() == null || v.getValue() == Util.sqlNullValue;
    }

    private static List<Predicate> translateChildren(List<StarPredicate> children) {
        List<Predicate> parts = new ArrayList<>();
        for (StarPredicate child : children) {
            parts.add(toPredicate(child));
        }
        return parts;
    }
}
