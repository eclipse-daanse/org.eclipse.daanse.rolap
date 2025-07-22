/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common.sql;

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.getTableAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.getLeftAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.getRightAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.left;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.right;
import static org.eclipse.daanse.rolap.common.util.TableUtil.getHintMap;
import static org.eclipse.daanse.rolap.common.util.ViewUtil.getCodeSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.Utils;
import org.eclipse.daanse.rolap.common.util.RelationUtil;
import org.eclipse.daanse.rolap.mapping.api.model.ColumnMapping;
import org.eclipse.daanse.rolap.mapping.api.model.DatabaseSchemaMapping;
import org.eclipse.daanse.rolap.mapping.api.model.InlineTableQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.JoinQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.QueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RelationalQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.SqlSelectQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TableQueryMapping;

/**
 * SqlQuery allows us to build a select
 * statement and generate it in database-specific SQL syntax.
 *
 *  Notable differences in database syntax are:
 *
 *  Identifier quoting
 *  Oracle (and all JDBC-compliant drivers) uses double-quotes,
 * for example select * from "emp". Access prefers brackets,
 * for example select * from [emp]. mySQL allows single- and
 * double-quotes for string literals, and therefore does not allow
 * identifiers to be quoted, for example select 'foo', "bar" from
 * emp.
 *
 *  AS in from clause
 *  Oracle doesn't like AS in the from * clause, for example
 * select from emp as e vs. select * from emp
 * e.
 *
 *  Column aliases
 *  Some databases require that every column in the select list
 * has a valid alias. If the expression is an expression containing
 * non-alphanumeric characters, an explicit alias is needed. For example,
 * Oracle will barfs at select empno + 1 from emp.
 *
 *  Parentheses around table names
 *  Oracle doesn't like select * from (emp)
 *
 *  Queries in FROM clause
 *  PostgreSQL and hsqldb don't allow, for example, select * from
 * (select * from emp) as e.
 *
 *  Uniqueness of index names
 *  In PostgreSQL and Oracle, index names must be unique within the
 * database; in Access and hsqldb, they must merely be unique within their
 * table
 *
 *  Datatypes
 *  In Oracle, BIT is CHAR(1), TIMESTAMP is DATE.
 *      In PostgreSQL, DOUBLE is DOUBLE PRECISION, BIT is BOOL.
 *
 *
 *
 * NOTE: Instances of this class are NOT thread safe so the user must make
 * sure this is accessed by only one thread at a time.
 *
 * @author jhyde
 */
public class SqlQuery {
    /** Controls the formatting of the sql string. */
    private final boolean generateFormattedSql;

    private boolean distinct;

    private final ClauseList select;
    private final FromClauseList from;
    private final ClauseList where;
    private final ClauseList groupBy;
    private final ClauseList having;
    private final ClauseList orderBy;
    private final List<ClauseList> groupingSets;
    private final ClauseList groupingFunctions;
    private final ClauseList rowLimit;

    private final List<BestFitColumnType> types =
        new ArrayList<>();

    /** Controls whether table optimization hints are used */
    private boolean allowHints;

    /** Is query supported by database vendor. Default is true*/
    private boolean isSupported = true;

    /**
     * This list is used to keep track of what aliases have been  used in the
     * FROM clause. One might think that a java.util.Set would be a more
     * appropriate Collection type, but if you only have a couple of "from
     * aliases", then iterating over a list is faster than doing a hash lookup
     * (as is used in java.util.HashSet).
     */
    private final List<String> fromAliases;

    /** The SQL dialect this query is to be generated in. */
    private final Dialect dialect;

    /** Scratch buffer. Clear it before use. */
    private final StringBuilder buf;

    private final Set<RelationalQueryMapping> relations =
        new HashSet<>();

    private final Map<RelationalQueryMapping, QueryMapping>
        mapRelationToRoot =
        new HashMap<>();

    private final Map<QueryMapping, List<RelInfo>>
        mapRootToRelations =
        new HashMap<>();

    private final Map<String, String> columnAliases =
        new HashMap<>();

    private static final String INDENT = "    ";

    /**
     * Base constructor used by all other constructors to create an empty
     * instance.
     *
     * @param dialect Dialect
     * @param formatted Whether to generate SQL formatted on multiple lines
     */
    public SqlQuery(Dialect dialect, boolean formatted) {
        assert dialect != null;
        this.generateFormattedSql = formatted;

        // both select and from allow duplications
        this.select = new ClauseList(true);
        this.from = new FromClauseList(true);

        this.groupingFunctions = new ClauseList(false);
        this.where = new ClauseList(false);
        this.groupBy = new ClauseList(false);
        this.having = new ClauseList(false);
        this.orderBy = new ClauseList(false);
        this.fromAliases = new ArrayList<>();
        this.buf = new StringBuilder(128);
        this.groupingSets = new ArrayList<>();
        this.dialect = dialect;
        this.rowLimit = new ClauseList(false);


        // REVIEW emcdermid 10-Jul-2009: It might be okay to allow
        // hints in all cases, but for initial implementation this
        // allows us to them on selectively in specific situations.
        // Usage will likely expand with experimentation.
        this.allowHints = false;
    }


    /**
     * Creates an empty SqlQuery with the same environment as this
     * one. (As per the Gang of Four 'prototype' pattern.)
     */
    public SqlQuery cloneEmpty()
    {
        return new SqlQuery(dialect, generateFormattedSql);
    }

    public void setDistinct(final boolean distinct) {
        this.distinct = distinct;
    }

    /**
     * Chooses whether table optimization hints may be used
     * (assuming the dialect supports it).
     *
     * @param t True to allow hints to be used, false otherwise
     */
    public void setAllowHints(boolean t) {
        this.allowHints = t;
    }

    /**
     * Adds a subquery to the FROM clause of this Query with a given alias.
     * If the query already exists it either, depending on
     * failIfExists, throws an exception or does not add the query
     * and returns false.
     *
     * @param query Subquery
     * @param alias (if not null, must not be zero length).
     * @param failIfExists if true, throws exception if alias already exists
     * @return true if query *was* added
     *
     *  alias != null
     */
    public boolean addFromQuery(
        final String query,
        final String alias,
        final boolean failIfExists)
    {
        assert alias != null;
        assert alias.length() > 0;

        if (fromAliases.contains(alias)) {
            if (failIfExists) {
                throw Util.newInternal(
                    new StringBuilder("query already contains alias '").append(alias).append("'").toString());
            } else {
                return false;
            }
        }

        buf.setLength(0);

        buf.append('(');
        buf.append(query);
        buf.append(')');
        if (dialect.allowsAs()) {
            buf.append(" as ");
        } else {
            buf.append(' ');
        }
        dialect.quoteIdentifier(alias, buf);
        fromAliases.add(alias);

        from.add(buf.toString());
        return true;
    }

    /**
     * Adds [schema.]table AS alias to the FROM clause.
     *
     * @param schema schema name; may be null
     * @param name table name
     * @param alias table alias, may not be null
     *              (if not null, must not be zero length).
     * @param filter Extra filter condition, or null
     * @param hints table optimization hints, if any
     * @param failIfExists Whether to throw a RuntimeException if from clause
     *   already contains this alias
     *
     *  alias != null
     * @return true if table was added
     */
    public boolean addFromTable(
        final String schema,
        final String name,
        final String alias,
        final String filter,
        final Map hints,
        final boolean failIfExists)
    {
        if (fromAliases.contains(alias)) {
            if (failIfExists) {
                throw Util.newInternal(
                    new StringBuilder("query already contains alias '").append(alias).append("'").toString());
            } else {
                return false;
            }
        }

        buf.setLength(0);
        dialect.quoteIdentifier(buf, schema, name);
        if (alias != null) {
            Util.assertTrue(alias.length() > 0);

            if (dialect.allowsAs()) {
                buf.append(" as ");
            } else {
                buf.append(' ');
            }
            dialect.quoteIdentifier(alias, buf);
            fromAliases.add(alias);
        }

        if (this.allowHints) {
            dialect.appendHintsAfterFromClause(buf, hints);
        }

        from.add(buf.toString());

        if (filter != null) {
            // append filter condition to where clause
            addWhere("(", filter, ")");
        }
        return true;
    }

    public void addFrom(
        final SqlQuery sqlQuery,
        final String alias,
        final boolean failIfExists)
    {
        addFromQuery(sqlQuery.toString(), alias, failIfExists);
    }

    /**
     * Adds a relation to a query, adding appropriate join conditions, unless
     * it is already present.
     *
     * Returns whether the relation was added to the query.
     *
     * @param relation Relation to add
     * @param alias Alias of relation. If null, uses relation's alias.
     * @param failIfExists Whether to fail if relation is already present
     * @return true, if relation *was* added to query
     */
    public boolean addFrom(
        final QueryMapping relation,
        final String alias,
        final boolean failIfExists)
    {
        registerRootRelation(relation);

        if (relation instanceof RelationalQueryMapping relation1) {
            if (relations.add(relation1)
                && !SystemWideProperties.instance()
                .FilterChildlessSnowflakeMembers)
            {
                // This relation is new to this query. Add a join to any other
                // relation in the same dimension.
                //
                // (If FilterChildlessSnowflakeMembers were false,
                // this would be unnecessary. Adding a relation automatically
                // adds all relations between it and the fact table.)
                QueryMapping root =
                    mapRelationToRoot.get(relation1);
                List<RelationalQueryMapping> relationsCopy =
                    new ArrayList<>(relations);
                for (RelationalQueryMapping relation2 : relationsCopy) {
                    if (relation2 != relation1
                        && mapRelationToRoot.get(relation2) == root)
                    {
                        addJoinBetween(root, relation1, relation2);
                    }
                }
            }
        }

        if (relation instanceof SqlSelectQueryMapping view) {
            final String viewAlias =
                (alias == null)
                ? RelationUtil.getAlias(view)
                : alias;
            final String sqlString = getCodeSet(view).chooseQuery(dialect);
            return addFromQuery(sqlString, viewAlias, false);

        } else if (relation instanceof InlineTableQueryMapping inlineTableQueryMapping) {
            final RelationalQueryMapping relation1 =
                RolapUtil.convertInlineTableToRelation(
                		inlineTableQueryMapping, dialect);
            return addFrom(relation1, alias, failIfExists);

        } else if (relation instanceof TableQueryMapping table) {
            final String tableAlias =
                (alias == null)
                ? RelationUtil.getAlias(table)
                : alias;
            return addFromTable(
                getSchemaName(table.getTable().getSchema()),
                table.getTable().getName(),
                tableAlias,
                table.getSqlWhereExpression() == null ? null : table.getSqlWhereExpression().getSql(),
                getHintMap(table),
                failIfExists);

        } else if (relation instanceof JoinQueryMapping join) {
            return addJoin(
                left(join),
                getLeftAlias(join),
                join.getLeft().getKey(),
                right(join),
                getRightAlias(join),
                join.getRight().getKey(),
                failIfExists);
        } else {
            throw Util.newInternal("bad relation type " + relation);
        }
    }

    private String getSchemaName(DatabaseSchemaMapping schema) {
        if (schema != null) {
            return schema.getName();
        }
        return null;
	}

	private boolean addJoin(
        QueryMapping left,
        String leftAlias,
        ColumnMapping leftKey,
        QueryMapping right,
        String rightAlias,
        ColumnMapping rightKey,
        boolean failIfExists)
    {
        boolean addLeft = addFrom(left, leftAlias, failIfExists);
        boolean addRight = addFrom(right, rightAlias, failIfExists);

        boolean added = addLeft || addRight;
        if (added) {
            buf.setLength(0);

            dialect.quoteIdentifier(buf, leftAlias, leftKey != null ? leftKey.getName() : null);
            buf.append(" = ");
            dialect.quoteIdentifier(buf, rightAlias, rightKey != null ? rightKey.getName() : null);
            final String condition = buf.toString();
            if (dialect.allowsJoinOn()) {
                from.addOn(
                    leftAlias, leftKey != null ? leftKey.getName() : null, rightAlias, rightKey != null ? rightKey.getName() : null,
                    condition);
            } else {
                addWhere(condition);
            }
        }
        return added;
    }

    private void addJoinBetween(
        QueryMapping root,
        RelationalQueryMapping relation1,
        RelationalQueryMapping relation2)
    {
        List<RelInfo> relations = mapRootToRelations.get(root);
        int index1 = find(relations, relation1);
        int index2 = find(relations, relation2);
        assert index1 != -1;
        assert index2 != -1;
        int min = Math.min(index1, index2);
        int max = Math.max(index1, index2);
        for (int i = max - 1; i >= min; i--) {
            RelInfo relInfo = relations.get(i);
                addJoin(
                    relInfo.relation,
                    relInfo.leftAlias != null
                        ? relInfo.leftAlias
                        : RelationUtil.getAlias(relInfo.relation),
                    relInfo.leftKey,
                    relations.get(i + 1).relation,
                    relInfo.rightAlias != null
                        ? relInfo.rightAlias
                        : RelationUtil.getAlias(relations.get(i + 1).relation),
                    relInfo.rightKey,
                    false);
        }
    }

    private int find(List<RelInfo> relations, RelationalQueryMapping relation) {
        for (int i = 0, n = relations.size(); i < n; i++) {
            RelInfo relInfo = relations.get(i);
            if (Utils.equalsQuery(relInfo.relation, relation)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Adds an expression to the select clause, automatically creating a
     * column alias.
     */
    public String addSelect(final CharSequence expression, BestFitColumnType type) {
        // Some DB2 versions (AS/400) throw an error if a column alias is
        //  *not* used in a subsequent order by (Group by).
        // Derby fails on 'SELECT... HAVING' if column has alias.
        return addSelect(expression, type, dialect.allowsFieldAs() ? nextColumnAlias() : null);
    }

    /**
     * Adds an expression to the SELECT and GROUP BY clauses. Uses the alias in
     * the GROUP BY clause, if the dialect requires it.
     *
     * @param expression Expression
     * @return Alias of expression
     */
    public String addSelectGroupBy(
        final String expression,
        BestFitColumnType type)
    {
        final String alias = addSelect(expression, type);
        addGroupBy(expression, alias);
        return alias;
    }

    public int getCurrentSelectListSize()
    {
        return select.size();
    }

    public String nextColumnAlias() {
        return "c" + select.size();
    }

    /**
     * Adds an expression to the select clause, with a specified type and
     * column alias.
     *
     * @param expression Expression
     * @param type Java type to be used to hold cursor column
     * @param alias Column alias (or null for no alias)
     * @return Column alias
     */
    public String addSelect(
        final CharSequence expression,
        final BestFitColumnType type,
        String alias)
    {
        buf.setLength(0);

        buf.append(expression);
        if (alias != null) {
            buf.append(" as ");
            dialect.quoteIdentifier(alias, buf);
        }

        select.add(buf.toString());
        addType(type);
        columnAliases.put(expression.toString(), alias);
        return alias;
    }

    public String getAlias(String expression) {
        return columnAliases.get(expression);
    }

    public void addWhere(
        final String exprLeft,
        final String exprMid,
        final String exprRight)
    {
        int len = exprLeft.length() + exprMid.length() + exprRight.length();
        StringBuilder buf = new StringBuilder(len);

        buf.append(exprLeft);
        buf.append(exprMid);
        buf.append(exprRight);

        addWhere(buf.toString());
    }

    public void addWhere(RolapStar.Condition joinCondition) {
        String left = getTableAlias(joinCondition.getLeft());
        String right = getTableAlias(joinCondition.getRight());
        if (fromAliases.contains(left) && fromAliases.contains(right)) {
            addWhere(
                joinCondition.getLeft(this),
                " = ",
                joinCondition.getRight(this));
        }
    }

    public void addWhere(final String expression)
    {
        assert expression != null && !expression.equals("");
        where.add(expression);
    }

    public void addGroupBy(final String expression)
    {
        assert expression != null && !expression.equals("");
        groupBy.add(expression);
    }

    public void addGroupBy(final String expression, final String alias) {
        if (dialect.requiresGroupByAlias()) {
            addGroupBy(dialect.quoteIdentifier(alias).toString());
        } else {
            addGroupBy(expression);
        }
    }

    public void addHaving(final String expression)
    {
        assert expression != null && !expression.equals("");
        having.add(expression);
    }

    /**
     * Adds an item to the ORDER BY clause.
     *
     * @param expr the expr to order by
     * @param ascending sort direction
     * @param prepend whether to prepend to the current list of items
     * @param nullable whether the expression might be null
     */
    public void addOrderBy(
        CharSequence expr,
        boolean ascending,
        boolean prepend,
        boolean nullable)
    {
        this.addOrderBy(expr, expr, ascending, prepend, nullable, true);
    }

    /**
     * Adds an item to the ORDER BY clause.
     *
     * @param expr the expr to order by
     * @param alias the alias of the column, as returned by addSelect
     * @param ascending sort direction
     * @param prepend whether to prepend to the current list of items
     * @param nullable whether the expression might be null
     * @param collateNullsLast whether null values should appear first or last.
     */
    public void addOrderBy(
        CharSequence expr,
        CharSequence alias,
        boolean ascending,
        boolean prepend,
        boolean nullable,
        boolean collateNullsLast)
    {
        String orderExpr =
            dialect.generateOrderItem(
                dialect.requiresOrderByAlias() && alias != null
                    ? dialect.quoteIdentifier(alias)
                    : expr,
                nullable,
                ascending,
                collateNullsLast).toString();
        if (prepend) {
            orderBy.add(0, orderExpr);
        } else {
            orderBy.add(orderExpr);
        }
    }

    public void addOrderBy(String expr, String alias, boolean ascending, boolean prepend, String nullParentValue,
            org.eclipse.daanse.jdbc.db.dialect.api.Datatype type, boolean collateNullsLast) {
        String orderExpr =
                dialect.generateOrderItemForOrderValue(
                    dialect.requiresOrderByAlias() && alias != null
                        ? dialect.quoteIdentifier(alias)
                        : expr,
                    nullParentValue,
                    type,
                    ascending,
                    collateNullsLast).toString();
            if (prepend) {
                orderBy.add(0, orderExpr);
            } else {
                orderBy.add(orderExpr);
            }

    }

    @Override
	public String toString()
    {
        buf.setLength(0);
        toBuffer(buf, "");
        return buf.toString();
    }

    /**
     * Writes this SqlQuery to a StringBuilder with each clause on a separate
     * line, and with the specified indentation prefix.
     *
     * @param buf String builder
     * @param prefix Prefix for each line
     */
    public void toBuffer(StringBuilder buf, String prefix) {
        final String first = distinct ? "select distinct " : "select ";
        select.toBuffer(buf, generateFormattedSql, prefix, first, ", ", "", "");
        groupingFunctionsToBuffer(buf, prefix);
        from.toBuffer(
            buf, generateFormattedSql, prefix, " from ", ", ", "", "");
        where.toBuffer(
            buf, generateFormattedSql, prefix, " where ", " and ", "", "");
        if (groupingSets.isEmpty()) {
            groupBy.toBuffer(
                buf, generateFormattedSql, prefix, " group by ", ", ", "", "");
        } else {
            ClauseList.listToBuffer(
                buf,
                groupingSets,
                generateFormattedSql,
                prefix,
                " group by grouping sets (",
                ", ",
                ")");
        }
        having.toBuffer(
            buf, generateFormattedSql, prefix, " having ", " and ", "", "");
        orderBy.toBuffer(
            buf, generateFormattedSql, prefix, " order by ", ", ", "", "");
        rowLimit.toBuffer(
                buf, generateFormattedSql, prefix, " ", ", ", "", "");
    }

    private void groupingFunctionsToBuffer(StringBuilder buf, String prefix) {
        if (groupingSets.isEmpty()) {
            return;
        }
        int n = 0;
        for (String groupingFunction : groupingFunctions) {
            if (generateFormattedSql) {
                buf.append(",")
                    .append(Util.NL)
                    .append(INDENT)
                    .append(prefix);
            } else {
                buf.append(", ");
            }
            buf.append("grouping(")
                .append(groupingFunction)
                .append(") as ");
            dialect.quoteIdentifier("g" + n++, buf);
        }
    }

    public Dialect getDialect() {
        return dialect;
    }

    public static SqlQuery newQuery(Context<?> context, String err) {

        return new SqlQuery(context.getDialect(), context.getConfigValue(ConfigConstants.GENERATE_FORMATTED_SQL, ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class));
    }

    public void addGroupingSet(List<String> groupingColumnsExpr) {
        ClauseList groupingList = new ClauseList(false);
        for (String columnExp : groupingColumnsExpr) {
            groupingList.add(columnExp);
        }
        groupingSets.add(groupingList);
    }

    public void addGroupingFunction(String columnExpr) {
        groupingFunctions.add(columnExpr);

        // A grouping function will end up in the select clause implicitly. It
        // needs a corresponding type.
        types.add(null);
    }

    private void addType(BestFitColumnType type) {
        types.add(type);
    }

    public Pair<String, List<BestFitColumnType>> toSqlAndTypes() {
        assert types.size() == select.size() + groupingFunctions.size()
            : new StringBuilder(types.size()).append(" types, ")
            .append((select.size() + groupingFunctions.size()))
            .append(" select items in query ").append(this).toString();
        return Pair.of(toString(), types);
    }

    public void registerRootRelation(QueryMapping root) {
        // REVIEW: In this method, we are building data structures about the
        // structure of a star schema. These should be built into the schema,
        // not constructed afresh for each SqlQuery. In mondrian-4.0,
        // these methods and the data structures 'mapRootToRelations',
        // 'relations', 'mapRelationToRoot' will disappear.
        if (mapRelationToRoot.containsKey(root)) {
            return;
        }
        if (mapRootToRelations.containsKey(root)) {
            return;
        }
        List<RelInfo> relations = new ArrayList<>();
        flatten(relations, root, null, null, null, null);
        for (RelInfo relation : relations) {
            mapRelationToRoot.put(relation.relation, root);
        }
        mapRootToRelations.put(root, relations);
    }

    private void flatten(
        List<RelInfo> relations,
        QueryMapping root,
        ColumnMapping leftKey,
        String leftAlias,
        ColumnMapping rightKey,
        String rightAlias)
    {
        if (root instanceof JoinQueryMapping join) {
            flatten(
                relations, left(join), join.getLeft().getKey(), getLeftAlias(join),
                join.getRight().getKey(), getRightAlias(join));
            flatten(
                relations, right(join), leftKey, leftAlias, rightKey,
                rightAlias);
        } else {
            relations.add(
                new RelInfo(
                    (RelationalQueryMapping) root,
                    leftKey,
                    leftAlias,
                    rightKey,
                    rightAlias));
        }
    }

    public boolean isSupported() {
        return isSupported;
    }

    public void setSupported(boolean supported) {
        isSupported = supported;
    }

    private static class JoinOnClause {
        private final String condition;
        private final String left;
        private final String right;

        JoinOnClause(String condition, String left, String right) {
            this.condition = condition;
            this.left = left;
            this.right = right;
        }
    }

    static class FromClauseList extends ClauseList {
        private final List<JoinOnClause> joinOnClauses =
            new ArrayList<>();

        FromClauseList(boolean allowsDups) {
            super(allowsDups);
        }

        public void addOn(
            String leftAlias,
            String leftKey,
            String rightAlias,
            String rightKey,
            String condition)
        {
            if (leftAlias == null && rightAlias == null) {
                // do nothing
            } else if (leftAlias == null) {
                // left is the form of 'Table'.'column'
                leftAlias = rightAlias;
            } else if (rightAlias == null) {
                // Only left contains table name, Table.Column = abc
                // store the same name for right tables
                rightAlias = leftAlias;
            }
            joinOnClauses.add(
                new JoinOnClause(condition, leftAlias, rightAlias));
        }

        public void toBuffer(StringBuilder buf, List<String> fromAliases) {
            int n = 0;
            for (int i = 0; i < size(); i++) {
                final String s = get(i);
                final String alias = fromAliases.get(i);
                if (n++ == 0) {
                    buf.append(" from ");
                    buf.append(s);
                } else {
                    // Add "JOIN t ON (a = b ,...)" to the FROM clause. If there
                    // is no JOIN clause matching this alias (or no JOIN clauses
                    // at all), append just ", t".
                    appendJoin(fromAliases.subList(0, i), s, alias, buf);
                }
            }
        }

        void appendJoin(
            final List<String> addedTables,
            final String from,
            final String alias,
            final StringBuilder buf)
        {
            int n = 0;
            // first check when the current table is on the left side
            for (JoinOnClause joinOnClause : joinOnClauses) {
                // the first table was added before join, it has to be handled
                // specially: Table.column = expression
                if ((addedTables.size() == 1
                     && addedTables.get(0).equals(joinOnClause.left)
                     && joinOnClause.left.equals(joinOnClause.right))
                    || (alias.equals(joinOnClause.left)
                        && addedTables.contains(joinOnClause.right))
                    || (alias.equals(joinOnClause.right)
                        && addedTables.contains(joinOnClause.left)))
                {
                    if (n++ == 0) {
                        buf.append(" join ").append(from).append(" on ");
                    } else {
                        buf.append(" and ");
                    }
                    buf.append(joinOnClause.condition);
                }
            }
            if (n == 0) {
                // No "JOIN ... ON" clause matching this alias (or maybe no
                // JOIN ... ON clauses at all, if this is a database that
                // doesn't support ANSI-join syntax). Append an old-style FROM
                // item separated by a comma.
                buf.append(joinOnClauses.isEmpty() ? ", " : " cross join ")
                    .append(from);
            }
        }
    }

    static class ClauseList extends ArrayList<String> {
        protected final boolean allowDups;

        ClauseList(final boolean allowDups) {
            this.allowDups = allowDups;
        }

        /**
         * Adds an element to this ClauseList if either duplicates are allowed
         * or if it has not already been added.
         *
         * @param element Element to add
         * @return whether element was added, per
         * {@link java.util.Collection#add(Object)}
         */
        @Override
		public boolean add(final String element) {
            if (allowDups || !contains(element)) {
                return super.add(element);
            }
            return false;
        }

        final void toBuffer(
            StringBuilder buf,
            boolean generateFormattedSql,
            String prefix,
            String first,
            String sep,
            String last,
            String empty)
        {
            if (isEmpty()) {
                buf.append(empty);
                return;
            }
            first = foo(generateFormattedSql, prefix, first);
            sep = foo(generateFormattedSql, prefix, sep);
            toBuffer(buf, first, sep, last);
        }

        static String foo(
            boolean generateFormattedSql,
            String prefix,
            String s)
        {
            if (generateFormattedSql) {
                if (s.startsWith(" ")) {
                    // E.g. " and "
                    s = Util.NL + prefix + s.substring(1);
                }
                if (s.endsWith(" ")) {
                    // E.g. ", "
                    s =
                        s.substring(0, s.length() - 1) + Util.NL + prefix
                        +  INDENT;
                } else if (s.endsWith("(")) {
                    // E.g. "("
                    s = s + Util.NL + prefix + INDENT;
                }
            }
            return s;
        }

        final void toBuffer(
            final StringBuilder buf,
            final String first,
            final String sep,
            final String last)
        {
            int n = 0;
            buf.append(first);
            for (String s : this) {
                if (n++ > 0) {
                    buf.append(sep);
                }
                buf.append(s);
            }
            buf.append(last);
        }

        static void listToBuffer(
            StringBuilder buf,
            List<ClauseList> clauseListList,
            boolean generateFormattedSql,
            String prefix,
            String first,
            String sep,
            String last)
        {
            first = foo(generateFormattedSql, prefix, first);
            sep = foo(generateFormattedSql, prefix, sep);
            buf.append(first);
            int n = 0;
            for (ClauseList clauseList : clauseListList) {
                if (n++ > 0) {
                    buf.append(sep);
                }
                clauseList.toBuffer(
                    buf, false, prefix, "(", ", ", ")", "()");
            }
            buf.append(last);
        }
    }

    /**
     * Collection of alternative code for alternative dialects.
     */
    public static class CodeSet {
        private final Map<String, String> dialectCodes =
            new HashMap<>();

        public String put(String dialect, String code) {
            return dialectCodes.put(dialect, code);
        }

        /**
         * Chooses the code variant which best matches the given Dialect.
         */
        public String chooseQuery(Dialect dialect) {
            String best = dialect.getDialectName();
            String bestCode = dialectCodes.get(best);
            if (bestCode != null) {
                return bestCode;
            }
            String genericCode = dialectCodes.get("generic");
            if (genericCode == null) {
                throw Util.newError("View has no 'generic' variant");
            }
            return genericCode;
        }
    }

    private static class RelInfo {
        final RelationalQueryMapping relation;
        final ColumnMapping leftKey;
        final String leftAlias;
        final ColumnMapping rightKey;
        final String rightAlias;

        public RelInfo(
            RelationalQueryMapping relation,
            ColumnMapping leftKey,
            String leftAlias,
            ColumnMapping rightKey,
            String rightAlias)
        {
            this.relation = relation;
            this.leftKey = leftKey;
            this.leftAlias = leftAlias;
            this.rightKey = rightKey;
            this.rightAlias = rightAlias;
        }
    }

    public void addRowLimit(int maxRowCount) {
        if(this.dialect.requiresDrillthroughMaxRowsInLimit()) {
            this.rowLimit.add("LIMIT " + Integer.toString(maxRowCount));
        }
    }
}
