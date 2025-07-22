/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2019 Hitachi Vantara and others
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
package org.eclipse.daanse.rolap.common.aggmatcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.RolapColumn;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.mapping.api.model.DatabaseSchemaMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to create "lost" and "collapsed" aggregate table
 * creation sql (creates the rdbms table and inserts into it from the base
 * fact table).
 *
 * @author Richard M. Emberson
 */
public class AggGen {

    private static final String COULD_NOT_GET_COLUMN_NAME_FOR_ROLAP_STAR_COLUMN = "\", could not get column name for RolapStar.Column: ";
	private static final String INIT = "Init: ";
	private static final String FACT_TABLE = "FactTable:";
	private static final String FOR_ROLAP_STAR_TABLE_WITH_ALIAS = "for RolapStar.Table with alias \"";
	private static final String FOR_FACT_TABLE = "For fact table \"";
	private static final String ADDING_SPECIAL_COLLAPSED_COLUMN = "Adding Special Collapsed Column: ";
	private static final String ADDING_COLLAPSED_COLUMN = "Adding Collapsed Column: ";
	private static final Logger LOGGER = LoggerFactory.getLogger(AggGen.class);
    public static final String FACT_COUNT = "fact_count";

    private final String cubeName;
    private final RolapStar star;
    private final RolapStar.Column[] columns;

    /** map RolapStar.Table to list of JdbcSchema Column Usages */
    private final Map<RolapStar.Table, List<JdbcSchema.Table.Column.Usage>>
        collapsedColumnUsages =
        new HashMap<>();

    /** set of JdbcSchema Column Usages */
    private final Set<JdbcSchema.Table.Column.Usage> notLostColumnUsages =
        new HashSet<>();

    /** list of JdbcSchema Column Usages */
    private final List<JdbcSchema.Table.Column.Usage> measures =
        new ArrayList<>();

    private boolean isReady;

    public AggGen(
        String cubeName,
        RolapStar star,
        RolapStar.Column[] columns)
    {
        this.cubeName = cubeName;
        this.star = star;
        this.columns = columns;
        init();
    }

    private Logger getLogger() {
        return LOGGER;
    }

    /**
     * Return true if this instance is ready to generate the sql. If false,
     * then something went wrong as it was trying to understand the columns.
     */
    public boolean isReady() {
        return isReady;
    }

    protected RolapStar.Table getFactTable() {
        return star.getFactTable();
    }

    protected String getFactTableName() {
        return getFactTable().getAlias();
    }

    protected SqlQuery getSqlQuery() {
        return star.getSqlQuery();
    }

    protected String getFactCount() {
        return FACT_COUNT;
    }

    protected JdbcSchema.Table getTable(JdbcSchema db, RolapStar.Table rt) {
        JdbcSchema.Table jt = getTable(db, rt.getAlias());
        return (jt == null)
            ? getTable(db, rt.getTableName())
            : jt;
    }

    protected JdbcSchema.Table getTable(JdbcSchema db, String name) {
        return db.getTable(name);
    }

    protected JdbcSchema.Table.Column getColumn(
        JdbcSchema.Table table,
        String name)
    {
        return table.getColumn(name);
    }

    protected String getRolapStarColumnName(RolapStar.Column rColumn) {
    	SqlExpression expr = rColumn.getExpression();
        if (expr instanceof RolapColumn cx) {
            return cx.getName();
        }
        return null;
    }

    protected void addForeignKeyToNotLostColumnUsages(
        JdbcSchema.Table.Column column)
    {
        // first make sure its not already in
        String cname = column.getName();
        for (JdbcSchema.Table.Column.Usage usage : notLostColumnUsages) {
            JdbcSchema.Table.Column c = usage.getColumn();
            if (cname.equals(c.getName())) {
                return;
            }
        }
        JdbcSchema.Table.Column.Usage usage = null;
        if (column.hasUsage(JdbcSchema.UsageType.FOREIGN_KEY)) {
            Iterator<JdbcSchema.Table.Column.Usage> it =
                column.getUsages(JdbcSchema.UsageType.FOREIGN_KEY);
            if (it.hasNext()) {
                usage = it.next();
            }
        } else {
            usage = column.newUsage(JdbcSchema.UsageType.FOREIGN_KEY);
            usage.setSymbolicName(JdbcSchema.UsageType.FOREIGN_KEY.name());
        }
        notLostColumnUsages.add(usage);
    }

    /**
     * The columns are the RolapStar columns taking part in an aggregation
     * request. This is what happens.
     * First, for each column, walk up the column's table until one level below
     * the base fact table. The left join condition contains the base fact table
     * and the foreign key column name. This column should not be lost.
     * Get the base fact table's measure columns.
     * With a list of columns that should not be lost and measure, one can
     * create lost create and insert commands.
     */
    private void init() {
    DatabaseSchemaMapping dbschema=	((RolapContext) star.getContext()).getCatalogMapping().getDbschemas().getFirst();
        JdbcSchema db =   new JdbcSchema(dbschema);

        JdbcSchema.Table factTable = getTable(db, getFactTableName());
        if (factTable == null) {
            String msg = new StringBuilder(INIT)
                .append("No fact table with name \"")
                .append(getFactTableName())
                .append("\"").toString();
            getLogger().warn(msg);
            return;
        }

        if (getLogger().isDebugEnabled()) {
            getLogger().debug(
                new StringBuilder(INIT)
                    .append("RolapStar:")
                    .append(Util.NL)
                    .append(getFactTable())
                    .append(Util.NL)
                    .append(FACT_TABLE)
                    .append(Util.NL)
                    .append(factTable).toString());
        }

        // do foreign keys
        for (RolapStar.Column column : columns) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug(
                    new StringBuilder(INIT)
                        .append("Column: ")
                        .append(column).toString());
            }
            RolapStar.Table table = column.getTable();

            if (table.getParentTable() == null) {
                // this is for those crazy dimensions which are in the
                // fact table, you know, non-shared with no table element

                // How the firetruck to enter information for the
                // collapsed case. This column is in the base fact table
                // and can be part of a dimension hierarchy but no where
                // in the RolapStar is this hiearchy captured - ugg.
                if (!addSpecialCollapsedColumn(db, column)) {
                    return;
                }


                SqlExpression expr = column.getExpression();
                if (expr instanceof RolapColumn exprColumn) {
                    String name = exprColumn.getName();
                    JdbcSchema.Table.Column c = getColumn(factTable, name);
                    if (c == null) {
                        String msg = new StringBuilder(INIT)
                            .append(FACT_TABLE)
                            .append(getFactTableName())
                            .append(Util.NL)
                            .append("No Column with name \"")
                            .append(name)
                            .append("\"").toString();
                        getLogger().warn(msg);
                        return;
                    }
                    if (getLogger().isDebugEnabled()) {
                        getLogger().debug("  Jdbc Column: c={}", c);
                    }
                    addForeignKeyToNotLostColumnUsages(c);
                }

            } else {
                if (!addCollapsedColumn(db, column)) {
                    return;
                }

                while (table.getParentTable().getParentTable() != null) {
                    table = table.getParentTable();
                }
                RolapStar.Condition cond = table.getJoinCondition();
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("  RolapStar.Condition: cond={}", cond);
                }
                SqlExpression left = cond.getLeft();
                if (left instanceof RolapColumn leftColumn) {
                    String name = leftColumn.getName();
                    JdbcSchema.Table.Column c = getColumn(factTable, name);
                    if (c == null) {
                        String msg = new StringBuilder(INIT)
                            .append(FACT_TABLE)
                            .append(getFactTableName())
                            .append(Util.NL)
                            .append("No Column with name \"")
                            .append(name)
                            .append("\"").toString();
                        getLogger().warn(msg);
                        return;
                    }
                    if (getLogger().isDebugEnabled()) {
                        getLogger().debug("  Jdbc Column: c={}", c);
                    }
                    addForeignKeyToNotLostColumnUsages(c);
                }
            }
        }

        // do measures
        for (RolapStar.Column rColumn : getFactTable().getColumns()) {
            String name = getRolapStarColumnName(rColumn);
            if (name == null) {
                String msg = new StringBuilder(INIT)
                    .append(FOR_FACT_TABLE)
                    .append(getFactTableName())
                    .append(COULD_NOT_GET_COLUMN_NAME_FOR_ROLAP_STAR_COLUMN)
                    .append(rColumn).toString();
                getLogger().warn(msg);
                return;
            }
            if (!(rColumn instanceof RolapStar.Measure rMeasure)) {
                // TODO: whats the solution to this?
                // its a funky dimension column in the fact table!!!
                getLogger().warn("not a measure: {}", name);
                continue;
            }
            if (!rMeasure.getCubeName().equals(cubeName)) {
                continue;
            }
            final Aggregator aggregator = rMeasure.getAggregator();
            JdbcSchema.Table.Column c = getColumn(factTable, name);
            if (c == null) {
                String msg = new StringBuilder("For RolapStar: \"")
                    .append(getFactTable().getAlias())
                    .append("\" measure with name, ")
                    .append(name)
                    .append(", is not a column name. ")
                    .append("The measure's column name may be an expression")
                    .append(" and currently AggGen does not handle expressions.")
                    .append(" You will have to add this measure to the")
                    .append(" aggregate table definition by hand.").toString();
                getLogger().warn(msg);
                continue;
            }
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("  Jdbc Column m={}", c);
            }

            JdbcSchema.Table.Column.Usage usage = null;
            if (c.hasUsage(JdbcSchema.UsageType.MEASURE)) {
                for (Iterator<JdbcSchema.Table.Column.Usage> uit =
                         c.getUsages(JdbcSchema.UsageType.MEASURE);
                     uit.hasNext();)
                {
                    JdbcSchema.Table.Column.Usage tmpUsage = uit.next();
                    if ((tmpUsage.getAggregator() == aggregator)
                        && tmpUsage.getSymbolicName().equals(rColumn.getName()))
                    {
                        usage = tmpUsage;
                        break;
                    }
                }
            }
            if (usage == null) {
                usage = c.newUsage(JdbcSchema.UsageType.MEASURE);
                usage.setAggregator(aggregator);
                usage.setSymbolicName(rColumn.getName());
            }
            measures.add(usage);
        }

        // If we got to here, then everything is ok.
        isReady = true;
    }

    private boolean addSpecialCollapsedColumn(
        final JdbcSchema db,
        final RolapStar.Column rColumn)
    {
        String rname = getRolapStarColumnName(rColumn);
        if (rname == null) {
            String msg = new StringBuilder(ADDING_SPECIAL_COLLAPSED_COLUMN)
                .append(FOR_FACT_TABLE)
                .append(getFactTableName())
                .append(COULD_NOT_GET_COLUMN_NAME_FOR_ROLAP_STAR_COLUMN)
                .append(rColumn).toString();
            getLogger().warn(msg);
            return false;
        }
        // this is in fact the fact table.
        RolapStar.Table rt = rColumn.getTable();

        JdbcSchema.Table jt = getTable(db, rt);
        if (jt == null) {
            String msg = new StringBuilder(ADDING_SPECIAL_COLLAPSED_COLUMN)
                .append(FOR_FACT_TABLE)
                .append(getFactTableName())
                .append("\", could not get jdbc schema table ")
                .append(FOR_ROLAP_STAR_TABLE_WITH_ALIAS)
                .append(rt.getAlias())
                .append("\"").toString();
            getLogger().warn(msg);
            return false;
        }

        List<JdbcSchema.Table.Column.Usage> list = collapsedColumnUsages.computeIfAbsent(rt, k -> new ArrayList<JdbcSchema.Table.Column.Usage>());


        JdbcSchema.Table.Column c = getColumn(jt, rname);
        if (c == null) {
            String msg = new StringBuilder(ADDING_SPECIAL_COLLAPSED_COLUMN)
                .append(FOR_FACT_TABLE)
                .append(getFactTableName())
                .append("\", could not get jdbc schema column ")
                .append(FOR_ROLAP_STAR_TABLE_WITH_ALIAS)
                .append(rt.getAlias())
                .append("\" and column name \"")
                .append(rname)
                .append("\"").toString();
            getLogger().warn(msg);
            return false;
        }
        // NOTE: this creates a new usage for the fact table
        // I do not know if this is a problem is AggGen is run before
        // Mondrian uses aggregate tables.
        list.add(c.newUsage(JdbcSchema.UsageType.FOREIGN_KEY));

        RolapStar.Column prColumn = rColumn;
        while (prColumn.getParentColumn() != null) {
            prColumn = prColumn.getParentColumn();
            rname = getRolapStarColumnName(prColumn);
            if (rname == null) {
                String msg = new StringBuilder(ADDING_SPECIAL_COLLAPSED_COLUMN)
                    .append(FOR_FACT_TABLE)
                    .append(getFactTableName())
                    .append("\", could not get parent column name")
                    .append("for RolapStar.Column \"")
                    .append(prColumn)
                    .append("\" for RolapStar.Table with alias \"")
                    .append(rt.getAlias())
                    .append("\"").toString();
                getLogger().warn(msg);
                return false;
            }
            c = getColumn(jt, rname);
            if (c == null) {
                getLogger().warn("Can not find column: {}", rname);
                break;
            }
            // NOTE: this creates a new usage for the fact table
            // I do not know if this is a problem is AggGen is run before
            // Mondrian uses aggregate tables.
            list.add(c.newUsage(JdbcSchema.UsageType.FOREIGN_KEY));
        }

        return true;
    }

    private boolean addCollapsedColumn(
        final JdbcSchema db,
        final RolapStar.Column rColumn)
    {
        // TODO: if column is "id" column, then there is no collapse
        String rname = getRolapStarColumnName(rColumn);
        if (rname == null) {
            String msg = new StringBuilder(ADDING_COLLAPSED_COLUMN)
                .append(FOR_FACT_TABLE)
                .append(getFactTableName())
                .append(COULD_NOT_GET_COLUMN_NAME_FOR_ROLAP_STAR_COLUMN)
                .append(rColumn).toString();
            getLogger().warn(msg);
            return false;
        }

        RolapStar.Table rt = rColumn.getTable();

        JdbcSchema.Table jt = getTable(db, rt);
        if (jt == null) {
            String msg = new StringBuilder(ADDING_COLLAPSED_COLUMN)
                .append(FOR_FACT_TABLE).append(getFactTableName())
                .append("\", could not get jdbc schema table ")
                .append(FOR_ROLAP_STAR_TABLE_WITH_ALIAS).append(rt.getAlias()).append("\"").toString();
            getLogger().warn(msg);
            return false;
        }

        // if this is a dimension table, then walk down the levels until
        // we hit the current column
        List<JdbcSchema.Table.Column.Usage> list =
            new ArrayList<>();
        for (RolapStar.Column rc : rt.getColumns()) {
            // do not include name columns
            if (rc.isNameColumn()) {
                continue;
            }
            String name = getRolapStarColumnName(rc);
            if (name == null) {
                String msg = new StringBuilder(ADDING_COLLAPSED_COLUMN)
                    .append(FOR_FACT_TABLE)
                    .append(getFactTableName())
                    .append("\", could not get column name")
                    .append(" for RolapStar.Column \"")
                    .append(rc)
                    .append("\" for RolapStar.Table with alias \"")
                    .append(rt.getAlias())
                    .append("\"").toString();
                getLogger().warn(msg);
                return false;
            }
            JdbcSchema.Table.Column c = getColumn(jt, name);
            if (c == null) {
                String msg = new StringBuilder("Can not find column: ").append(name).toString();
                getLogger().warn(msg);
                break;
            }

            JdbcSchema.Table.Column.Usage usage =
                c.newUsage(JdbcSchema.UsageType.FOREIGN_KEY);
            usage.usagePrefix = rc.getUsagePrefix();

            list.add(usage);

            if (rname.equals(name)) {
                break;
            }
        }
        // may already be there so only enter if new list is bigger
        List<JdbcSchema.Table.Column.Usage> l = collapsedColumnUsages.get(rt);
        if ((l == null) || (l.size() < list.size())) {
            collapsedColumnUsages.put(rt, list);
        }

        return true;
    }

    private static final String AGG_LOST_PREFIX = "agg_l_XXX_";

    String makeLostAggregateTableName(String factTableName) {
        return AGG_LOST_PREFIX
               + factTableName;
    }

    private static final String AGG_COLLAPSED_PREFIX = "agg_c_XXX_";

    String makeCollapsedAggregateTableName(String factTableName) {
        return AGG_COLLAPSED_PREFIX
               + factTableName;
    }



    /**
     * Return a String containing the sql code to create a lost dimension
     * table.
     *
     * @return lost dimension sql code
     */
    public String createLost() {
        StringWriter sw = new StringWriter(512);
        PrintWriter pw = new PrintWriter(sw);
        String prefix = "    ";

        pw.print("CREATE TABLE ");
        pw.print(makeLostAggregateTableName(getFactTableName()));
        pw.println(" (");

        // do foreign keys
        for (JdbcSchema.Table.Column.Usage usage : notLostColumnUsages) {
            addColumnCreate(pw, prefix, usage);
        }

        // do measures
        for (JdbcSchema.Table.Column.Usage usage : measures) {
            addColumnCreate(pw, prefix, usage);
        }
        // do fact_count
        pw.print(prefix);
        pw.print(getFactCount());
        pw.println(" INTEGER NOT NULL");

        pw.println(");");
        return sw.toString();
    }

    /**
     * Return the sql code to populate a lost dimension table from the fact
     * table.
     */
    public String insertIntoLost() {
        StringWriter sw = new StringWriter(512);
        PrintWriter pw = new PrintWriter(sw);
        String prefix = "    ";
        String factTableName = getFactTableName();
        SqlQuery sqlQuery = getSqlQuery();

        pw.print("INSERT INTO ");
        pw.print(makeLostAggregateTableName(getFactTableName()));
        pw.println(" (");

        for (JdbcSchema.Table.Column.Usage usage : notLostColumnUsages) {
            JdbcSchema.Table.Column c = usage.getColumn();

            pw.print(prefix);
            pw.print(c.getName());
            pw.println(',');
        }

        for (JdbcSchema.Table.Column.Usage usage : measures) {
            pw.print(prefix);
            String name = getUsageName(usage);
            pw.print(name);
            pw.println(',');
        }
        // do fact_count
        pw.print(prefix);
        pw.print(getFactCount());
        pw.println(")");

        pw.println("SELECT");
        for (JdbcSchema.Table.Column.Usage usage : notLostColumnUsages) {
            JdbcSchema.Table.Column c = usage.getColumn();

            pw.print(prefix);
            pw.print(
                sqlQuery.getDialect().quoteIdentifier(
                    factTableName,
                    c.getName()));
            pw.print(" AS ");
            pw.print(sqlQuery.getDialect().quoteIdentifier(c.getName()));
            pw.println(',');
        }
        for (JdbcSchema.Table.Column.Usage usage : measures) {
            JdbcSchema.Table.Column c = usage.getColumn();
            Aggregator agg = usage.getAggregator();

            pw.print(prefix);
            pw.print(
                agg.getExpression(sqlQuery.getDialect().quoteIdentifier(
                    factTableName, c.getName())));
            pw.print(" AS ");
            pw.print(sqlQuery.getDialect().quoteIdentifier(c.getName()));
            pw.println(',');
        }

        // do fact_count
        pw.print(prefix);
        pw.print("COUNT(*) AS ");
        pw.println(sqlQuery.getDialect().quoteIdentifier(getFactCount()));

        pw.println("FROM ");
        pw.print(prefix);
        pw.print(sqlQuery.getDialect().quoteIdentifier(factTableName));
        pw.print(" ");
        pw.println(sqlQuery.getDialect().quoteIdentifier(factTableName));

        pw.println("GROUP BY ");
        int k = 0;
        for (JdbcSchema.Table.Column.Usage notLostColumnUsage
            : notLostColumnUsages)
        {
            if (k++ > 0) {
                pw.println(",");
            }
            JdbcSchema.Table.Column.Usage usage = notLostColumnUsage;
            JdbcSchema.Table.Column c = usage.getColumn();

            pw.print(prefix);
            pw.print(
                sqlQuery.getDialect().quoteIdentifier(
                    factTableName,
                    c.getName()));
        }

        pw.println(';');
        return sw.toString();
    }
    /**
     * Return a String containing the sql code to create a collapsed dimension
     * table.
     *
     * @return collapsed dimension sql code
     */
    public String createCollapsed() {
        StringWriter sw = new StringWriter(512);
        PrintWriter pw = new PrintWriter(sw);
        String prefix = "    ";

        pw.print("CREATE TABLE ");
        pw.print(makeCollapsedAggregateTableName(getFactTableName()));
        pw.println(" (");

        // do foreign keys
        for (List<JdbcSchema.Table.Column.Usage> list
            : collapsedColumnUsages.values())
        {
            for (JdbcSchema.Table.Column.Usage usage : list) {
                addColumnCreate(pw, prefix, usage);
            }
        }

        // do measures
        for (JdbcSchema.Table.Column.Usage usage : measures) {
            addColumnCreate(pw, prefix, usage);
        }
        // do fact_count
        pw.print(prefix);
        pw.print(getFactCount());
        pw.println(" INTEGER NOT NULL");

        pw.println(");");
        return sw.toString();
    }

    /**
     * Return the sql code to populate a collapsed dimension table from
     * the fact table.
     */
    public String insertIntoCollapsed() {
        StringWriter sw = new StringWriter(512);
        PrintWriter pw = new PrintWriter(sw);
        String prefix = "    ";
        String factTableName = getFactTableName();
        SqlQuery sqlQuery = getSqlQuery();

        pw.print("INSERT INTO ");
        pw.print(makeCollapsedAggregateTableName(getFactTableName()));
        pw.println(" (");


        for (List<JdbcSchema.Table.Column.Usage> list
            : collapsedColumnUsages.values())
        {
            for (JdbcSchema.Table.Column.Usage usage : list) {
                JdbcSchema.Table.Column c = usage.getColumn();
                pw.print(prefix);
                if (usage.usagePrefix != null) {
                    pw.print(usage.usagePrefix);
                }
                pw.print(c.getName());
                pw.println(',');
            }
        }

        for (JdbcSchema.Table.Column.Usage usage : measures) {
            pw.print(prefix);
            String name = getUsageName(usage);
            pw.print(name);
            pw.println(',');
        }
        // do fact_count
        pw.print(prefix);
        pw.print(getFactCount());
        pw.println(")");

        pw.println("SELECT");
        for (List<JdbcSchema.Table.Column.Usage> list
            : collapsedColumnUsages.values())
        {
            for (JdbcSchema.Table.Column.Usage usage : list) {
                JdbcSchema.Table.Column c = usage.getColumn();
                JdbcSchema.Table t = c.getTable();

                pw.print(prefix);
                pw.print(
                    sqlQuery.getDialect().quoteIdentifier(
                        t.getName(),
                        c.getName()));
                pw.print(" AS ");
                String n = (usage.usagePrefix == null)
                    ? c.getName() : usage.usagePrefix + c.getName();
                pw.print(sqlQuery.getDialect().quoteIdentifier(n));
                pw.println(',');
            }
        }
        for (JdbcSchema.Table.Column.Usage usage : measures) {
            JdbcSchema.Table.Column c = usage.getColumn();
            JdbcSchema.Table t = c.getTable();
            Aggregator agg = usage.getAggregator();

            pw.print(prefix);
            pw.print(
                agg.getExpression(sqlQuery.getDialect().quoteIdentifier(
                    t.getName(), c.getName())));
            pw.print(" AS ");
            pw.print(sqlQuery.getDialect().quoteIdentifier(c.getName()));
            pw.println(',');
        }

        // do fact_count
        pw.print(prefix);
        pw.print("COUNT(*) AS ");
        pw.println(sqlQuery.getDialect().quoteIdentifier(getFactCount()));

        pw.println("FROM ");
        pw.print(prefix);
        pw.print(sqlQuery.getDialect().quoteIdentifier(factTableName));
        pw.print(" ");
        pw.print(sqlQuery.getDialect().quoteIdentifier(factTableName));
        pw.println(',');

        // add dimension tables
        int k = 0;
        for (RolapStar.Table rt : collapsedColumnUsages.keySet()) {
            if (k++ > 0) {
                pw.println(',');
            }
            pw.print(prefix);
            pw.print(sqlQuery.getDialect().quoteIdentifier(rt.getAlias()));
            pw.print(" AS ");
            pw.print(sqlQuery.getDialect().quoteIdentifier(rt.getAlias()));

            // walk up tables
            if (rt.getParentTable() != null) {
                while (rt.getParentTable().getParentTable() != null) {
                    rt = rt.getParentTable();

                    pw.println(',');

                    pw.print(prefix);
                    pw.print(
                        sqlQuery.getDialect().quoteIdentifier(rt.getAlias()));
                    pw.print(" AS ");
                    pw.print(
                        sqlQuery.getDialect().quoteIdentifier(rt.getAlias()));
                }
            }
        }

        pw.println();
        pw.println("WHERE ");
        k = 0;
        for (RolapStar.Table rt : collapsedColumnUsages.keySet()) {
            if (k++ > 0) {
                pw.println(" and");
            }

            RolapStar.Condition cond = rt.getJoinCondition();
            if (cond == null) {
                continue;
            }
            pw.print(prefix);
            pw.print(cond.toString(sqlQuery));

            if (rt.getParentTable() != null) {
                while (rt.getParentTable().getParentTable() != null) {
                    rt = rt.getParentTable();
                    cond = rt.getJoinCondition();

                    pw.println(" and");

                    pw.print(prefix);
                    pw.print(cond.toString(sqlQuery));
                }
            }
        }

        pw.println();
        pw.println("GROUP BY ");
        k = 0;
        for (List<JdbcSchema.Table.Column.Usage> list
            : collapsedColumnUsages.values())
        {
            for (JdbcSchema.Table.Column.Usage usage : list) {
                if (k++ > 0) {
                    pw.println(",");
                }
                JdbcSchema.Table.Column c = usage.getColumn();
                JdbcSchema.Table t = c.getTable();

                String n = (usage.usagePrefix == null)
                    ? c.getName() : usage.usagePrefix + c.getName();
                pw.print(prefix);
                pw.print(sqlQuery.getDialect().quoteIdentifier(t.getName(), n));
            }
        }
        pw.println(';');

        return sw.toString();
    }



    private String getUsageName(final JdbcSchema.Table.Column.Usage usage) {
        JdbcSchema.Table.Column c = usage.getColumn();
        String name = c.getName();
        // if its a measure which is based upon a foreign key, then
        // the foreign key column name is already used (for the foreign key
        // column) so we must choose a different name.
        if (usage.getUsageType() == JdbcSchema.UsageType.MEASURE && c.hasUsage(JdbcSchema.UsageType.FOREIGN_KEY)) {
            name = usage.getSymbolicName().replace(' ', '_').toUpperCase();
        }
        return name;
    }

    private void addColumnCreate(
        final PrintWriter pw,
        final String prefix,
        final JdbcSchema.Table.Column.Usage usage)
    {
        JdbcSchema.Table.Column c = usage.getColumn();
        String name = getUsageName(usage);

        pw.print(prefix);
        if (usage.usagePrefix != null) {
            pw.print(usage.usagePrefix);
        }
        pw.print(name);
        pw.print(' ');
        pw.print(c.getTypeName().toUpperCase());
        switch (c.getType()) {
        case Types.NUMERIC, Types.DECIMAL:
            pw.print('(');
            pw.print(c.getNumPrecRadix());
            pw.print(",");
            pw.print(c.getDecimalDigits());
            pw.print(')');
            break;
        case Types.CHAR, Types.VARCHAR:
            pw.print('(');
            pw.print(c.getCharOctetLength());
            pw.print(')');
            break;
        default:
        }
        if (! c.isNullable()) {
            pw.print(" NOT NULL");
        }
        pw.println(',');
    }
}
