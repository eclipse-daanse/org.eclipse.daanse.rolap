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
*/
package org.eclipse.daanse.rolap.aggregator.extra;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.eclipse.daanse.sql.model.sql.OrderedColumn;
import org.eclipse.daanse.sql.model.sql.SortDirection;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

public class ListAggAggregator implements Aggregator, org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator {

    private boolean distinct;
    private String separator;
    private List<RolapColumn> columns;
    private String coalesce;
    private String onOverflowTruncate;

    //
    public ListAggAggregator(boolean distinct, String separator, List<RolapColumn> columns, String coalesce, String onOverflowTruncate) {
        this.distinct = distinct;
        this.separator = separator;
        this.columns = columns;
        this.coalesce = coalesce;
        this.onOverflowTruncate = onOverflowTruncate;
    }

    @Override
    public SqlExpression toNode(SqlExpression operand) {
        List<OrderedColumn> columnsList = List.of();
        if (columns != null) {
            columnsList = columns.stream()
                .map(c -> new OrderedColumn(c.getName(), c.getTable(),
                    org.eclipse.daanse.olap.api.sql.SortingDirection.NONE.equals(c.getSortingDirection()) ? Optional.empty() : Optional.of(SortDirection.valueOf(c.getSortingDirection().name())),
                    Optional.empty()))
                .toList();
        }
        return new SqlExpression.ExtraAggregate(Optional.ofNullable(operand),
                new SqlExpression.ExtraAggregate.Spec.ListAgg(distinct, separator, coalesce, onOverflowTruncate, columnsList));
    }

    @Override
    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> exp) {
        // MDX-set aggregation: evaluate the expression for every tuple, then
        // join the values with the configured separator (deduped if DISTINCT).
        // Same algorithm as the in-memory segment rollup below.
        java.util.List<Object> raw = new java.util.ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            evaluator.setContext(members.get(i));
            raw.add(exp.evaluate(evaluator));
        }
        return aggregate(raw, DataTypeJdbc.VARCHAR);
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        return new StringBuilder(getName()).append('(').append(operand).append(')');
    }

    @Override
    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        // Enable in-memory rollup of LISTAGG segment results so parent-level
        // cells (e.g. an "All Categories" total) actually aggregate the
        // child-level concatenated strings, in parallel with SumAggregator's
        // rollup of numeric segments. Without this, only leaf-level segments
        // populate the LISTAGG measure and Excel/MDX parent rows come out
        // empty.
        return DataTypeJdbc.VARCHAR.equals(dataType);
    }

    @Override
    public String getName() {
        return "LISTAGG";
    }

    @Override
    public Aggregator getRollup() {
        return this;
    }

    @Override
    public Object aggregate(List<Object> rawData, DataTypeJdbc datatype) {
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }
        String sep = separator != null ? separator : ",";
        if (distinct) {
            // Each input may itself be the result of a leaf-level LISTAGG
            // (e.g. "a|b" rolling up with "c|d"); split by separator so
            // DISTINCT deduplicates individual elements rather than whole
            // pre-aggregated strings.
            LinkedHashSet<String> uniq = new LinkedHashSet<>();
            for (Object o : rawData) {
                String s = coalesceValue(o);
                if (s == null) {
                    continue;
                }
                for (String elem : s.split(Pattern.quote(sep))) {
                    if (!elem.isEmpty()) {
                        uniq.add(elem);
                    }
                }
            }
            return uniq.isEmpty() ? null : String.join(sep, uniq);
        }
        StringJoiner joiner = new StringJoiner(sep);
        boolean any = false;
        for (Object o : rawData) {
            String s = coalesceValue(o);
            if (s == null) {
                continue;
            }
            joiner.add(s);
            any = true;
        }
        return any ? joiner.toString() : null;
    }

    private String coalesceValue(Object o) {
        if (o != null) {
            return o.toString();
        }
        // SQL LISTAGG ... ON OVERFLOW / NULL ON ... lets the caller substitute
        // a placeholder for null cells. If no coalesce is configured, skip.
        return coalesce;
    }

    @Override
    public boolean isDistinct() {
        return distinct;
    }

    @Override
    public Aggregator getNonDistinctAggregator() {
        return null;
    }
}