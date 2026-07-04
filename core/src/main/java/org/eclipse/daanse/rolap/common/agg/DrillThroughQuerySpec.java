/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2018 Hitachi Vantara
 * Copyright (C) 2021 Sergei Semenkov
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

package org.eclipse.daanse.rolap.common.agg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Column;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper;
import org.eclipse.daanse.rolap.common.sqlbuild.SqlBuildGuard;
import org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;

/**
 * Provides the information necessary to generate SQL for a drill-through
 * request.
 *
 * @author jhyde
 * @author Richard M. Emberson
 */
public class DrillThroughQuerySpec extends AbstractQuerySpec {
    private final DrillThroughCellRequest request;
    private final List<StarPredicate> listOfStarPredicates;
    private final List<String> columnNames;
    private final int maxColumnNameLength;
    private final List<OlapElement> fields;

    public DrillThroughQuerySpec(
        DrillThroughCellRequest request,
        StarPredicate starPredicateSlicer,
        List<OlapElement> fields, boolean countOnly)
    {
        super(request.getMeasure().getStar(), countOnly);
        this.request = request;
        if (starPredicateSlicer != null) {
            this.listOfStarPredicates =
                Collections.singletonList(starPredicateSlicer);
        } else {
            this.listOfStarPredicates = Collections.emptyList();
        }
        int tmpMaxColumnNameLength =
            getStar().getDialect().getMaxColumnNameLength();
        if (tmpMaxColumnNameLength == 0) {
            // From java.sql.DatabaseMetaData: "a result of zero means that
            // there is no limit or the limit is not known"
            maxColumnNameLength = Integer.MAX_VALUE;
        } else {
            maxColumnNameLength = tmpMaxColumnNameLength;
        }
        this.columnNames = computeDistinctColumnNames();
        this.fields = fields;
    }

    private List<String> computeDistinctColumnNames() {
        final List<String> columnNamesInner = new ArrayList<>();
        final Set<String> columnNameSet = new HashSet<>();

        final RolapStar.Column[] columns = getColumns();
        for (RolapStar.Column column : columns) {
            addColumnName(column, columnNamesInner, columnNameSet);
        }

        addColumnName(request.getMeasure(), columnNamesInner, columnNameSet);

        return columnNamesInner;
    }

    private void addColumnName(
        final RolapStar.Column column,
        final List<String> columnNames,
        final Set<String> columnNameSet)
    {
        String columnName = makeAlias(column, columnNames, columnNameSet);
        columnNames.add(columnName);
    }

    private String makeAlias(
        final RolapStar.Column column,
        final List<String> columnNames,
        final Set<String> columnNameSet)
    {
        String columnName = column.getName();
        if (columnName != null) {
            // nothing
        } else if (column.getExpression() instanceof org.eclipse.daanse.rolap.element.RolapColumn col) {
            columnName = col.getName();
        } else {
            columnName = "c" + Integer.toString(columnNames.size());
        }
        // Register the column name, and if it's not unique, append numeric
        // suffixes until it is. Also make sure that it is within the
        // range allowed by this SQL dialect.
        String originalColumnName = columnName;
        if (columnName.length() > maxColumnNameLength) {
            columnName = columnName.substring(0, maxColumnNameLength);
        }
        for (int j = 0; !columnNameSet.add(columnName); j++) {
            final String suffix = "_" + Integer.toString(j);
            columnName = originalColumnName;
            if (originalColumnName.length() + suffix.length()
                > maxColumnNameLength)
            {
                columnName =
                    originalColumnName.substring(
                        0, maxColumnNameLength - suffix.length());
            }
            columnName += suffix;
        }

        return columnName;
    }

    @Override
    protected boolean isPartOfSelect(RolapStar.Column col) {
        return request.includeInSelect(col);
    }

    @Override
    protected boolean isPartOfSelect(RolapStar.Measure measure) {
        return request.includeInSelect(measure);
    }

    @Override
	public int getMeasureCount() {
        return !request.getDrillThroughMeasures().isEmpty()
            ? request.getDrillThroughMeasures().size()
            : 1;
    }

    @Override
	public RolapStar.Measure getMeasure(final int i) {
        return !request.getDrillThroughMeasures().isEmpty()
            ? request.getDrillThroughMeasures().get(i)
            : request.getMeasure();
    }

    private boolean isSelectAliasTaken(Column[] cols, String alias) {
        if (columnNames.contains(alias)) {
            // Possible conflict of alias.
            for (int j = 0; j < cols.length; j++) {
                if (getColumnAlias(j).equals(alias)
                    && isPartOfSelect(cols[j]))
                {
                    // Definite conflict.
                    return true;
                }
            }
        }
        return false;
    }

    @Override
	public String getMeasureAlias(final int i) {
        String alias =
            !request.getDrillThroughMeasures().isEmpty()
                ? request.getDrillThroughMeasures().get(i).getName()
                : columnNames.getLast();
        int j = 0;
        String maybe = alias;
        final Column[] cols = getColumns();
        while (isSelectAliasTaken(cols, maybe)) {
            maybe = alias.concat("_").concat("" + j);
            j++;
        }
        return maybe;
    }

    @Override
	public RolapStar.Column[] getColumns() {
        return request.getConstrainedColumns();
    }

    @Override
	public String getColumnAlias(final int i) {
        return columnNames.get(i);
    }

    @Override
	public StarColumnPredicate getColumnPredicate(final int i) {
        final StarColumnPredicate constr = request.getValueAt(i);
        return (constr == null)
            ? LiteralStarPredicate.TRUE
            : constr;
    }

    @Override
	public RenderedSql generateSql() {
        // Authoritative: the builder reproduces every drill-through shape (count + detail), verified
        // 46/46 corpus-wide; no legacy QueryRecorder is constructed. buildDrillThrough handles countOnly too.
        org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect = getStar().getDialect();
        return SqlBuildGuard.build(dialect,
            getStar().getContext().getConfigValue(
                org.eclipse.daanse.olap.common.ConfigConstants.GENERATE_FORMATTED_SQL,
                org.eclipse.daanse.olap.common.ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE,
                Boolean.class),
            () -> buildDrillThrough(dialect)).render();
    }

    /**
     * The drill-through SELECT via the generic {@link AggregateSqlMapper#drillThrough}: the detail-row
     * query (cut columns + measures + inapplicable-member NULL placeholders + row limit), or a
     * {@code count(*)} query when {@code countOnly}. This is the authoritative producer — no legacy
     * QueryRecorder is built.
     */
    private SelectStatement buildDrillThrough(org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
        boolean allowsFieldAlias = dialect.allowsFieldAlias();
        List<AggregateSqlMapper.DrillColumn> drillColumns = new ArrayList<>();
        RolapStar.Column[] columns = getColumns();
        for (int i = 0; i < columns.length; i++) {
            RolapStar.Column column = columns[i];
            if (column.getTable().isFunky()) {
                continue; // funky dimension — ignored, exactly like nonDistinctGenerateSql
            }
            StarColumnPredicate predicate = getColumnPredicate(i);
            Predicate filter = StarPredicateTranslator.isAlwaysTrue(predicate)
                ? null : StarPredicateTranslator.toPredicate(predicate);
            boolean selected = isPartOfSelect(column);
            String alias = (selected && allowsFieldAlias) ? getColumnAlias(i) : null;
            // The request's cut columns participate in ORDER BY (legacy addOrderBy when isOrdered()).
            drillColumns.add(
                new AggregateSqlMapper.DrillColumn(column, filter, selected, alias, isOrdered()));
        }

        // compound member predicates (slicer): add their predicate + ensure their tables are joined,
        // and — mirroring extraPredicates — SELECT each constrained column (deduped alias, no ORDER
        // BY) when the request includes it and it is not already projected.
        List<Predicate> extraFilters = new ArrayList<>();
        Set<String> columnNameSet = new HashSet<>(columnNames);
        for (StarPredicate sp : getPredicateList()) {
            extraFilters.add(StarPredicateTranslator.toPredicate(sp));
            for (RolapStar.Column c : sp.getConstrainedColumnList()) {
                boolean inSelect = isPartOfSelect(c) && !columnNameSet.contains(c.getName());
                String alias = inSelect ? makeAlias(c, columnNames, columnNameSet) : null;
                drillColumns.add(new AggregateSqlMapper.DrillColumn(
                    c, null, inSelect, allowsFieldAlias ? alias : null, false));
            }
        }

        // SELECT extras: measures (raw, named) then NULL placeholders for inapplicable members.
        // (Skipped for countOnly — the mapper projects count(*) over the same FROM+WHERE.)
        List<AggregateSqlMapper.RawProjection> raw = new ArrayList<>();
        if (!countOnly) {
            for (int i = 0, n = getMeasureCount(); i < n; i++) {
                RolapStar.Measure measure = getMeasure(i);
                if (!isPartOfSelect(measure)) {
                    continue;
                }
                // Dialect-free: the drill-through projects the raw measure column -> Column / computed
                // RawVariant (renderer resolves per dialect == the legacy generateExprString chooseQuery, at
                // render). A column-less measure keeps the legacy string (rare; never computed -> no chooseQuery).
                raw.add(new AggregateSqlMapper.RawProjection(
                    measure.getExpression() == null
                        ? Expressions.raw(measure.generateExprString(dialect))
                        : org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(
                            measure.getExpression()),
                    null, getMeasureAlias(i), "measure " + measure.getName()));
            }
            for (OlapElement member : request.getNonApplicableMembers()) {
                // Derby rejects a bare NULL in the select list (42X01) — it needs a typed
                // CAST(NULL AS ...). Everyone else (incl. ClickHouse, where a CAST to a
                // non-Nullable type would itself fail) keeps the generic bare NULL.
                raw.add(new AggregateSqlMapper.RawProjection(
                    Expressions.rawVariant(java.util.Map.of(
                        "generic", "NULL",
                        "derby", "CAST(NULL AS VARCHAR(255))")),
                    BestFitColumnType.STRING, member.getName(),
                    "inapplicable member"));
            }
        }

        return AggregateSqlMapper.drillThrough(getStar().getFactTable(), drillColumns, extraFilters, raw,
            countOnly, request.getMaxRowCount(), dialect);
    }

    @Override
	protected boolean isAggregate() {
        return false;
    }

    @Override
	protected boolean isOrdered() {
        return true;
    }

    @Override
	protected List<StarPredicate> getPredicateList() {
        return listOfStarPredicates;
    }

}
