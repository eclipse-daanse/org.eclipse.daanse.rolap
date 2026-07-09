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
import java.util.function.Function;

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.LiteralStarPredicate;
import org.eclipse.daanse.rolap.common.agg.MemberTuplePredicate;
import org.eclipse.daanse.rolap.common.agg.MinusStarPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.agg.RangeColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
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

    /** The default column→node resolver: a plain star column via {@link JoinPlanner#expressionFor(RolapStar.Column)}. */
    private static final Function<RolapStar.Column, SqlExpression> DEFAULT_COLUMN_RESOLVER =
            JoinPlanner::expressionFor;

    public static Predicate toPredicate(StarPredicate predicate) {
        return toPredicate(predicate, DEFAULT_COLUMN_RESOLVER);
    }

    /**
     * As {@link #toPredicate(StarPredicate)} but resolves each constrained {@link RolapStar.Column} to its
     * SQL node through {@code columnResolver} instead of the star column's own expression. Used to substitute
     * aggregate-table column nodes for base columns when translating an agg-substituted slicer-tuple predicate
     * (the columns carry a delegate that only renders via {@code generateExprString}, so the default
     * {@link JoinPlanner#expressionFor} cannot reach their node). The factoring (IN / tuple-IN, null hoisting)
     * is the same as the base-star path bar the substituted column expressions.
     */
    public static Predicate toPredicate(
            StarPredicate predicate, Function<RolapStar.Column, SqlExpression> columnResolver) {
        if (predicate instanceof ValueColumnPredicate value) {
            return valuePredicate(value, columnResolver);
        }
        if (predicate instanceof ListColumnPredicate list) {
            return listPredicate(list, columnResolver);
        }
        if (predicate instanceof AndPredicate and) {
            return Predicates.and(translateChildren(and.getChildren(), columnResolver));
        }
        if (predicate instanceof OrPredicate or) {
            return orPredicate(or, columnResolver);
        }
        if (predicate instanceof LiteralStarPredicate literal) {
            // true → always-true (renders 1=1); false → always-false (renders 1=0).
            return literal.getValue() ? Predicates.and(List.of()) : Predicates.or(List.of());
        }
        if (predicate instanceof RangeColumnPredicate range) {
            return rangePredicate(range, columnResolver);
        }
        if (predicate instanceof MemberTuplePredicate tuple) {
            return memberTuplePredicate(tuple, columnResolver);
        }
        if (predicate instanceof MinusStarPredicate minus) {
            return minusPredicate(minus, columnResolver);
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
        return toColumnPredicate(predicate, col, null);
    }

    /**
     * As {@link #toColumnPredicate(org.eclipse.daanse.rolap.common.star.StarColumnPredicate, SqlExpression)}
     * but with an explicit literal datatype for predicates constructed WITHOUT a star column (a shared /
     * non-cube level constrained via its level key expression, where the literal datatype comes from the
     * level rather than a star column).
     */
    public static Predicate toColumnPredicate(
            org.eclipse.daanse.rolap.common.star.StarColumnPredicate predicate, SqlExpression col,
            org.eclipse.daanse.jdbc.db.api.type.Datatype fallbackType) {
        if (predicate instanceof ValueColumnPredicate value) {
            Object v = value.getValue();
            if (v == null || v == Util.sqlNullValue) {
                return Predicates.isNull(col);
            }
            return Predicates.comparison(col, ComparisonOperator.EQ,
                    Expressions.literal(v, literalType(value, fallbackType)));
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
                    values.add(Expressions.literal(val, literalType(v, fallbackType)));
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

    /** The literal datatype: the predicate's star column when present, else the caller's fallback. */
    private static org.eclipse.daanse.jdbc.db.api.type.Datatype literalType(
            ValueColumnPredicate value, org.eclipse.daanse.jdbc.db.api.type.Datatype fallbackType) {
        return value.getConstrainedColumn() != null ? value.getConstrainedColumn().getDatatype() : fallbackType;
    }

    /** {@code col = value}, or {@code col IS NULL} when the value is null. */
    private static Predicate valuePredicate(
            ValueColumnPredicate value, Function<RolapStar.Column, SqlExpression> columnResolver) {
        RolapStar.Column column = value.getConstrainedColumn();
        SqlExpression col = columnResolver.apply(column);
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
    private static Predicate listPredicate(
            ListColumnPredicate list, Function<RolapStar.Column, SqlExpression> columnResolver) {
        List<org.eclipse.daanse.rolap.common.star.StarColumnPredicate> children = list.getPredicates();
        boolean allValues = !children.isEmpty()
                && children.stream().allMatch(ValueColumnPredicate.class::isInstance);
        if (allValues) {
            SqlExpression col = columnResolver.apply(list.getConstrainedColumn());
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
            parts.add(toPredicate(child, columnResolver));
        }
        return Predicates.or(parts);
    }

    /**
     * An {@code OR} of predicates, collapsing a run of same-column value predicates into a single
     * {@code IN}. When every child is a non-null {@link ValueColumnPredicate} on one column
     * the result is {@code col IN (...)} (wrapped in the {@code OR}'s parentheses);
     * otherwise the children are {@code OR}-ed as-is.
     */
    private static Predicate orPredicate(
            OrPredicate or, Function<RolapStar.Column, SqlExpression> columnResolver) {
        List<StarPredicate> children = or.getChildren();
        // Each child must reduce to one value per column (a bare ValueColumnPredicate, or an
        // AndPredicate of them — a slicer tuple member). Otherwise fall back to a plain OR.
        List<List<ValueColumnPredicate>> rows = new ArrayList<>();
        for (StarPredicate child : children) {
            List<ValueColumnPredicate> row = columnValues(child);
            if (row == null) {
                return Predicates.or(translateChildren(children, columnResolver));
            }
            rows.add(row);
        }
        if (rows.size() <= 1) {
            return Predicates.or(translateChildren(children, columnResolver));
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
                return Predicates.or(translateChildren(children, columnResolver));
            }
            for (RolapStar.Column col : columns) {
                if (!byColumn.containsKey(col.getBitPosition())) {
                    return Predicates.or(translateChildren(children, columnResolver));
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
                return Predicates.or(translateChildren(children, columnResolver));
            }
        }
        List<Predicate> conj = new ArrayList<>();
        for (RolapStar.Column col : nullColumns) {
            conj.add(Predicates.isNull(columnResolver.apply(col)));
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
                conj.add(Predicates.in(columnResolver.apply(inListColumns.get(0)), flat));
            } else {
                List<SqlExpression> colExprs = new ArrayList<>();
                for (RolapStar.Column col : inListColumns) {
                    colExprs.add(columnResolver.apply(col));
                }
                conj.add(Predicates.inTuple(colExprs, valueRows));
            }
        }
        // Parenthesisation: the OR's outer "(" plus the factored group's "(" — and, for a
        // multi-column list, the extra tuple "(". The factored null columns are AND-ed BEFORE the
        // IN-list. or([ and([ nulls.., inList ]) ]) renders exactly that pair.
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

    private static List<Predicate> translateChildren(
            List<StarPredicate> children, Function<RolapStar.Column, SqlExpression> columnResolver) {
        List<Predicate> parts = new ArrayList<>();
        for (StarPredicate child : children) {
            parts.add(toPredicate(child, columnResolver));
        }
        return parts;
    }

    /**
     * A {@link RangeColumnPredicate} → a comparison, or an {@code AND} of two for a two-sided
     * range. A lower bound is {@code col >= v} (inclusive) or {@code col > v} (exclusive); an
     * upper bound {@code col <= v} / {@code col < v}. Uniform one-sided/two-sided form (never
     * {@code BETWEEN}). Declines (throws) a range whose bound value is the SQL null sentinel —
     * that can never render as a comparison.
     */
    private static Predicate rangePredicate(
            RangeColumnPredicate range, Function<RolapStar.Column, SqlExpression> columnResolver) {
        RolapStar.Column column = range.getConstrainedColumn();
        SqlExpression col = columnResolver.apply(column);
        List<Predicate> parts = new ArrayList<>();
        ValueColumnPredicate lower = range.getLowerBound();
        if (lower != null) {
            parts.add(Predicates.comparison(col,
                    range.getLowerInclusive() ? ComparisonOperator.GE : ComparisonOperator.GT,
                    boundLiteral(lower, column)));
        }
        ValueColumnPredicate upper = range.getUpperBound();
        if (upper != null) {
            parts.add(Predicates.comparison(col,
                    range.getUpperInclusive() ? ComparisonOperator.LE : ComparisonOperator.LT,
                    boundLiteral(upper, column)));
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("RangeColumnPredicate with no bounds");
        }
        return parts.size() == 1 ? parts.get(0) : Predicates.and(parts);
    }

    /** A range bound's literal; declines (throws) the SQL null sentinel — a range cannot be
     *  bounded by {@code IS NULL}. */
    private static SqlExpression boundLiteral(ValueColumnPredicate bound, RolapStar.Column column) {
        Object v = bound.getValue();
        if (v == null || v == Util.sqlNullValue) {
            throw new IllegalArgumentException("RangeColumnPredicate bound with null value");
        }
        return Expressions.literal(v, column.getDatatype());
    }

    /**
     * A {@link MemberTuplePredicate} → the SQL twin of its lexicographic {@code evaluate}: each
     * bound becomes an EQ-conjunction (EQ bound) or an {@code OR} of prefix-conjuncts (range
     * bound); multiple bounds are {@code AND}-ed. For a bound over columns c1..cn with values
     * v1..vn a {@code >}/{@code >=} bound is
     * {@code (c1>v1) OR (c1=v1 AND c2>v2) OR … OR (c1=v1 AND … AND cn-1=vn-1 AND cn ⊚ vn)} where
     * only the final tie-break uses the possibly-non-strict operator; {@code <}/{@code <=} is
     * the mirror image.
     * <p>
     * GUARD: {@link MemberTuplePredicate#computeColumnList} skips non-{@code RolapCubeLevel}
     * levels, so a bound's value count can exceed the column-list size. This method declines
     * (throws) on that misalignment rather than emit column-shifted SQL.
     */
    private static Predicate memberTuplePredicate(
            MemberTuplePredicate tuple, Function<RolapStar.Column, SqlExpression> columnResolver) {
        List<RolapStar.Column> columns = tuple.getConstrainedColumnList();
        List<Predicate> boundPreds = new ArrayList<>();
        for (MemberTuplePredicate.BoundSpec bound : tuple.getBoundSpecs()) {
            List<Object> values = bound.values();
            if (values.size() != columns.size()) {
                throw new IllegalArgumentException(
                        "MemberTuplePredicate bound/column misalignment: " + values.size()
                                + " values vs " + columns.size() + " columns");
            }
            boundPreds.add(boundPredicate(columns, values, bound.relation(), columnResolver));
        }
        return boundPreds.size() == 1 ? boundPreds.get(0) : Predicates.and(boundPreds);
    }

    /** One {@link MemberTuplePredicate.BoundSpec} → its lexicographic {@link Predicate}. */
    private static Predicate boundPredicate(
            List<RolapStar.Column> columns, List<Object> values,
            MemberTuplePredicate.BoundRelation relation,
            Function<RolapStar.Column, SqlExpression> columnResolver) {
        int n = columns.size();
        if (relation == MemberTuplePredicate.BoundRelation.EQ) {
            List<Predicate> eqs = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                eqs.add(memberComparison(columns.get(k), ComparisonOperator.EQ, values.get(k), columnResolver));
            }
            return eqs.size() == 1 ? eqs.get(0) : Predicates.and(eqs);
        }
        boolean greater = relation == MemberTuplePredicate.BoundRelation.GT
                || relation == MemberTuplePredicate.BoundRelation.GE;
        ComparisonOperator strict = greater ? ComparisonOperator.GT : ComparisonOperator.LT;
        ComparisonOperator finalOp = switch (relation) {
        case GT -> ComparisonOperator.GT;
        case GE -> ComparisonOperator.GE;
        case LT -> ComparisonOperator.LT;
        case LE -> ComparisonOperator.LE;
        case EQ -> ComparisonOperator.EQ;
        };
        List<Predicate> terms = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            List<Predicate> conj = new ArrayList<>();
            for (int j = 0; j < k; j++) {
                conj.add(memberComparison(columns.get(j), ComparisonOperator.EQ, values.get(j), columnResolver));
            }
            conj.add(memberComparison(columns.get(k), k == n - 1 ? finalOp : strict, values.get(k), columnResolver));
            terms.add(conj.size() == 1 ? conj.get(0) : Predicates.and(conj));
        }
        return terms.size() == 1 ? terms.get(0) : Predicates.or(terms);
    }

    /** {@code col <op> literal(value)} with the column's own datatype for the literal. */
    private static Predicate memberComparison(
            RolapStar.Column column, ComparisonOperator op, Object value,
            Function<RolapStar.Column, SqlExpression> columnResolver) {
        return Predicates.comparison(columnResolver.apply(column), op,
                Expressions.literal(value, column.getDatatype()));
    }

    /**
     * A {@link MinusStarPredicate} → {@code plus AND NOT(minus)}, recursing both sides. SQL
     * three-valued logic drops rows where {@code minus} is unknown (a null column makes
     * {@code NOT(minus)} evaluate to {@code NULL}); so when {@code minus} does not itself
     * constrain the null value we widen to {@code NOT(minus) OR col IS NULL}, mirroring the
     * exclude-path convention in {@code MemberConstraintWriter}.
     */
    private static Predicate minusPredicate(
            MinusStarPredicate minus, Function<RolapStar.Column, SqlExpression> columnResolver) {
        Predicate plusP = toPredicate(minus.getPlus(), columnResolver);
        Predicate minusP = toPredicate(minus.getMinus(), columnResolver);
        Predicate negated;
        if (constrainsNull(minus.getMinus())) {
            negated = Predicates.not(minusP);
        } else {
            SqlExpression col = columnResolver.apply(minus.getConstrainedColumn());
            negated = Predicates.or(List.of(Predicates.not(minusP), Predicates.isNull(col)));
        }
        return Predicates.and(List.of(plusP, negated));
    }

    /** Whether a predicate's value set includes the SQL null value (so {@code NOT(it)} already
     *  accounts for null rows). Predicates whose values can't be enumerated are treated as not
     *  covering null (the caller then adds the {@code IS NULL} protection). */
    private static boolean constrainsNull(StarColumnPredicate predicate) {
        List<Object> values = new ArrayList<>();
        try {
            predicate.values(values);
        } catch (UnsupportedOperationException e) {
            return false;
        }
        for (Object v : values) {
            if (v == null || v == Util.sqlNullValue) {
                return true;
            }
        }
        return false;
    }
}
