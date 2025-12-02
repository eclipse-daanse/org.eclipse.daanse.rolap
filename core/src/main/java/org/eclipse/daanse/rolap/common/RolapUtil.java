/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * Copyright (C) 2021 Sergei Semenkov
 * All Rights Reserved.
 *
 * jhyde, 22 December, 2001
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

import static org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Consumer;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.Quoting;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.common.NativeEvaluationUnsupportedException;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.exceptions.MdxCantFindMemberException;
import org.eclipse.daanse.olap.fun.FunUtil;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.connection.AbstractRolapConnection;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.util.ClassResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for classes in the mondrian.rolap package.
 *
 * @author jhyde
 * @since 22 December, 2001
 */
public class RolapUtil {

    private final static String nativeEvaluationUnsupported =
        "Native evaluation not supported for this usage of function ''{0}''";

    private RolapUtil() {
        // constructor
    }

    public static final Logger MDX_LOGGER = LoggerFactory.getLogger("daanse.mdx");
    public static final Logger SQL_LOGGER = LoggerFactory.getLogger("daanse.sql");
    public static final Logger MONITOR_LOGGER =
        LoggerFactory.getLogger("daanse.server.monitor");

    static final Logger LOGGER = LoggerFactory.getLogger(RolapUtil.class);

    /**
     * Hook to run when a query is executed. This should not be
     * used at runtime but only for testing.
     */
    private static ExecuteQueryHook queryHook = null;

    public static Consumer<java.sql.Statement> getDefaultCallback(final ExecutionContext executionContext) {
        return stmt -> executionContext.registerStatement(stmt);
    }

    /**
     * Sets the query-execution hook used by tests. This method and
     * {@link #setHook(org.eclipse.daanse.rolap.common.RolapUtil.ExecuteQueryHook)} are
     * synchronized to ensure a memory barrier.
     *
     * @return Query execution hook
     */
    public static synchronized ExecuteQueryHook getHook() {
        return queryHook;
    }

    public static synchronized void setHook(ExecuteQueryHook hook) {
        queryHook = hook;
    }

    /**
     * A {@link Comparator} implementation which can deal
     * correctly with RolapUtil#sqlNullValue.
     */
    public static class SqlNullSafeComparator
        implements Comparator<Comparable>
    {
        public static final SqlNullSafeComparator instance =
            new SqlNullSafeComparator();

        private SqlNullSafeComparator() {
        }

        @Override
        public int compare(Comparable o1, Comparable o2) {
            if (o1 == Util.sqlNullValue) {
                return -1;
            }
            if (o2 == Util.sqlNullValue) {
                return 1;
            }
            return o1.compareTo(o2);
        }
    }

    /**
     * A comparator singleton instance which can handle the presence of
     * RolapUtilComparable instances in a collection.
     */
    public static final Comparator ROLAP_COMPARATOR =
        new RolapUtilComparator();

    private static final class RolapUtilComparator<T extends Comparable<T>>
        implements Comparator<T>
    {
        @Override
		public int compare(T o1, T o2) {
            try {
                return o1.compareTo(o2);
            } catch (ClassCastException cce) {
                if (o2 == Util.sqlNullValue) {
                    return 1;
                }
                throw new OlapRuntimeException(cce);
            }
        }
    }

    /**
     * Runtime NullMemberRepresentation property change not taken into
     * consideration
     */
    private static String mdxNullLiteral = null;
    public static final String SQL_NULL_LITERAL = "null";

    public static String mdxNullLiteral() {
        return SystemWideProperties.instance().NullMemberRepresentation;
    }
    /**
     * Names of classes of drivers we've loaded (or have tried to load).
     *
     * NOTE: Synchronization policy: Lock the {@link AbstractRolapConnection} class
     * before modifying or using this member.
     */
    private static final Set<String> loadedDrivers = new HashSet<>();

    static RolapMember[] toArray(List<RolapMember> v) {
        return v.isEmpty()
            ? new RolapMember[0]
            : v.toArray(new RolapMember[v.size()]);
    }

    static RolapMember lookupMember(
        MemberReader reader,
        List<Segment> uniqueNameParts,
        boolean failIfNotFound)
    {
        RolapMember member =
            lookupMemberInternal(
                uniqueNameParts, null, reader, failIfNotFound);
        if (member != null) {
            return member;
        }

        // If this hierarchy has an 'all' member, we can omit it.
        // For example, '[Gender].[(All Gender)].[F]' can be abbreviated
        // '[Gender].[F]'.
        final List<RolapMember> rootMembers = reader.getRootMembers();
        if (rootMembers.size() == 1) {
            final RolapMember rootMember = rootMembers.get(0);
            if (rootMember.isAll()) {
                member =
                    lookupMemberInternal(
                        uniqueNameParts, rootMember, reader, failIfNotFound);
            }
        }
        return member;
    }

    private static RolapMember lookupMemberInternal(
        List<Segment> segments,
        RolapMember member,
        MemberReader reader,
        boolean failIfNotFound)
    {
        for (Segment segment : segments) {
            if (!(segment instanceof NameSegment nameSegment)) {
                break;
            }
            List<RolapMember> children;
            if (member == null) {
                children = reader.getRootMembers();
            } else {
                children = new ArrayList<>();
                reader.getMemberChildren(member, children);
                member = null;
            }
            for (RolapMember child : children) {
                if (child.getName().equals(nameSegment.getName())) {
                    member = child;
                    break;
                }
            }
            if (member == null) {
                break;
            }
        }
        if (member == null && failIfNotFound) {
            throw new MdxCantFindMemberException(Util.implode(segments));
        }
        return member;
    }

    /**
     * Executes a query, printing to the trace log if tracing is enabled.
     *
     * If the query fails, it wraps the {@link SQLException} in a runtime
     * exception with message as description, and closes the result
     * set.
     *
     * If it succeeds, the caller must call the {@link SqlStatement#close}
     * method of the returned {@link SqlStatement}.
     *
     * @param context context
     * @param sql SQL string
     * @param locus Locus of execution
     * @return SqlStatement
     */
    public static SqlStatement executeQuery(
        Context context,
        String sql,
        ExecutionContext executionContext)
    {
        return executeQuery(
                context, sql, null, 0, 0, executionContext, -1, -1,
            getDefaultCallback(executionContext));
    }

    /**
     * Executes a query.
     *
     * If the query fails, it wraps the {@link SQLException} in a runtime
     * exception with message as description, and closes the result
     * set.
     *
     * If it succeeds, the caller must call the {@link SqlStatement#close}
     * method of the returned {@link SqlStatement}.
     *
     *
     * @param context context
     * @param sql SQL string
     * @param types Suggested types of columns, or null;
     *     if present, must have one element for each SQL column;
     *     each not-null entry overrides deduced JDBC type of the column
     * @param maxRowCount Maximum number of rows to retrieve, less or = 0 if unlimited
     * @param firstRowOrdinal Ordinal of row to skip to (1-based), or 0 to
     *   start from beginning
     * @param locus Execution context of this statement
     * @param resultSetType Result set type, or -1 to use default
     * @param resultSetConcurrency Result set concurrency, or -1 to use default
     * @param callback callback
     * @return ResultSet
     */
    public static SqlStatement executeQuery(
        Context context,
        String sql,
        List<BestFitColumnType> types,
        int maxRowCount,
        int firstRowOrdinal,
        ExecutionContext executionContext,
        int resultSetType,
        int resultSetConcurrency,
        Consumer<java.sql.Statement> callback)
    {
        SqlStatement stmt =
            new SqlStatement(
                    context, sql, types, maxRowCount, firstRowOrdinal, executionContext,
                resultSetType, resultSetConcurrency,
                callback == null
                    ? getDefaultCallback(executionContext)
                    : callback);
        stmt.execute();
        return stmt;
    }

    /**
     * Raises an alert that native SQL evaluation could not be used
     * in a case where it might have been beneficial, but some
     * limitation in Mondrian's implementation prevented it.
     * (Do not call this in cases where native evaluation would
     * have been wasted effort.)
     *
     * @param functionName name of function for which native evaluation
     * was skipped
     *
     * @param reason reason why native evaluation was skipped
     */
    public static void alertNonNative(
        String functionName,
        String reason, String alertNativeEvaluationUnsupported)
        throws NativeEvaluationUnsupportedException
    {
        // No i18n for log message, but yes for excn
        String alertMsg =
            new StringBuilder("Unable to use native SQL evaluation for '").append(functionName)
            .append("'; reason:  ").append(reason).toString();


        if (alertNativeEvaluationUnsupported.equalsIgnoreCase(
                "WARN"))
        {
            LOGGER.warn(alertMsg);
        } else if (alertNativeEvaluationUnsupported.equalsIgnoreCase(
                "ERROR"))
        {
            LOGGER.error(alertMsg);
            throw new NativeEvaluationUnsupportedException(MessageFormat.format(nativeEvaluationUnsupported,
                functionName));
        }
    }

    /**
     * Loads a set of JDBC drivers.
     *
     * @param jdbcDrivers A string consisting of the comma-separated names
     *  of JDBC driver classes. For example
     *  "sun.jdbc.odbc.JdbcOdbcDriver,com.mysql.jdbc.Driver".
     */
    public static synchronized void loadDrivers(String jdbcDrivers) {
        StringTokenizer tok = new StringTokenizer(jdbcDrivers, ",");
        while (tok.hasMoreTokens()) {
            String jdbcDriver = tok.nextToken();
            if (loadedDrivers.add(jdbcDriver)) {
                try {
                    ClassResolver.INSTANCE.forName(jdbcDriver, true);
                    LOGGER.info(
                        "Daanse: JDBC driver {} loaded successfully", jdbcDriver);
                } catch (ClassNotFoundException e) {
                    LOGGER.warn(
                        "Daanse: Warning: JDBC driver {} not found", jdbcDriver);
                }
            }
        }
    }


    /**
     * Locates a member specified by its member name, from an array of
     * members.  If an exact match isn't found, but a matchType of BEFORE
     * or AFTER is specified, then the closest matching member is returned.
     *
     *
     * @param members array of members to search from
     * @param parent parent member corresponding to the member being searched
     * for
     * @param level level of the member
     * @param searchName member name
     * @param matchType match type
     * @return matching member (if it exists) or the closest matching one
     * in the case of a BEFORE or AFTER search
     */
    public static Member findBestMemberMatch(
        List<? extends Member> members,
        RolapMember parent,
        RolapLevel level,
        Segment searchName,
        MatchType matchType)
    {
        if (!(searchName instanceof NameSegment nameSegment)) {
            return null;
        }
        switch (matchType) {
        case FIRST:
            return members.get(0);
        case LAST:
            return members.get(members.size() - 1);
        default:
            // fall through
        }
        // create a member corresponding to the member we're trying
        // to locate so we can use it to hierarchically compare against
        // the members array
        Member searchMember =
            level.getHierarchy().createMember(
                parent, level, nameSegment.getName(), null);
        Member bestMatch = null;
        for (Member member : members) {
            int rc;
            if (searchName.getQuoting() == Quoting.KEY
                && member instanceof RolapMember rolapMember
                && rolapMember.getKey().toString().equals(
                nameSegment.getName()))
            {
                return member;
            }
            if (matchType.isExact()) {
                rc = Util.compareName(member.getName(), nameSegment.getName());
            } else {
                rc =
                    FunUtil.compareSiblingMembers(
                        member,
                        searchMember);
            }
            if (rc == 0) {
                return member;
            }
            if (matchType == MatchType.BEFORE) {
                if (rc < 0
                    && (bestMatch == null
                        || FunUtil.compareSiblingMembers(member, bestMatch)
                        > 0))
                {
                    bestMatch = member;
                }
            } else if (matchType == MatchType.AFTER && rc > 0
                    && (bestMatch == null
                        || FunUtil.compareSiblingMembers(member, bestMatch)
                        < 0)) {
                bestMatch = member;
            }
        }
        if (matchType.isExact()) {
            return null;
        }
        return bestMatch;
    }

    public static org.eclipse.daanse.rolap.mapping.model.RelationalQuery convertInlineTableToRelation(
    		org.eclipse.daanse.rolap.mapping.model.InlineTableQuery inlineTable,
        final Dialect dialect)
    {

        final int columnCount = inlineTable.getTable().getColumns().size();
        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            columnNames.add(inlineTable.getTable().getColumns().get(i).getName());
            columnTypes.add(inlineTable.getTable().getColumns().get(i).getType().getLiteral());
        }
        List<String[]> valueList = new ArrayList<>();
        for (org.eclipse.daanse.rolap.mapping.model.Row row : inlineTable.getTable().getRows()) {
            String[] values = new String[columnCount];
            for (org.eclipse.daanse.rolap.mapping.model.RowValue value : row.getRowValues()) {
                final int columnOrdinal = columnNames.indexOf(value.getColumn().getName());
                if (columnOrdinal < 0) {
                    throw Util.newError(
                        new StringBuilder("Unknown column '").append(value.getColumn().getName()).append("'").toString());
                }
                values[columnOrdinal] = value.getValue();
            }
            valueList.add(values);
        }
        org.eclipse.daanse.rolap.mapping.model.SqlSelectQuery view = RolapMappingFactory.eINSTANCE.createSqlSelectQuery();
        view.setAlias(getAlias(inlineTable));
        
        org.eclipse.daanse.rolap.mapping.model.SqlStatement sqlStatement = RolapMappingFactory.eINSTANCE.createSqlStatement();
        sqlStatement.getDialects().add("generic");
        sqlStatement.setSql(dialect.generateInline(columnNames, columnTypes, valueList).toString());
        
        org.eclipse.daanse.rolap.mapping.model.SqlView sqlView = RolapMappingFactory.eINSTANCE.createSqlView();
        sqlView.getSqlStatements().add(sqlStatement);

        view.setSql(sqlView);

        return view;
    }

    public static org.eclipse.daanse.rolap.mapping.model.RelationalQuery convertInlineTableToRelation(
    		org.eclipse.daanse.rolap.mapping.model.InlineTableQuery inlineTable,
            final Dialect dialect, List<String> orderColumns)
        {
            List<String> columnNames = new ArrayList<>();
            List<String> columnTypes = new ArrayList<>();
            
            List<? extends org.eclipse.daanse.rolap.mapping.model.Column> columns = inlineTable.getTable().getColumns().stream().sorted(Comparator.comparingInt(o -> orderColumns.indexOf(o.getName()))).toList();
            
            for (org.eclipse.daanse.rolap.mapping.model.Column columnMapping : columns) {
            		columnNames.add(columnMapping.getName());
            		columnTypes.add(columnMapping.getType().getLiteral());
            }
            List<String[]> valueList = new ArrayList<>();
            for (org.eclipse.daanse.rolap.mapping.model.Row row : inlineTable.getTable().getRows()) {
                List<String> values = new ArrayList<>();
                List<? extends org.eclipse.daanse.rolap.mapping.model.RowValue> rowValues = row.getRowValues().stream().sorted(Comparator.comparingInt(v -> orderColumns.indexOf(v.getColumn().getName()))).toList();
                for (org.eclipse.daanse.rolap.mapping.model.RowValue rowValue : rowValues) {
                	values.add(rowValue.getValue());
                }
                valueList.add(values.toArray(new String[0]));
            }
            
            org.eclipse.daanse.rolap.mapping.model.SqlStatement sqlStatement = RolapMappingFactory.eINSTANCE.createSqlStatement();
            sqlStatement.getDialects().add("generic");
            sqlStatement.setSql(dialect.generateInline(columnNames, columnTypes, valueList).toString());
            
            org.eclipse.daanse.rolap.mapping.model.SqlView sqlView = RolapMappingFactory.eINSTANCE.createSqlView();
            sqlView.getSqlStatements().add(sqlStatement);
            
            org.eclipse.daanse.rolap.mapping.model.SqlSelectQuery view = RolapMappingFactory.eINSTANCE.createSqlSelectQuery();
            view.setAlias(getAlias(inlineTable));
            view.setSql(sqlView);
            
            return view;
        }

    public static RolapMember strip(RolapMember member) {
        if (member instanceof RolapCubeMember rolapCubeMember) {
            return rolapCubeMember.getRolapMember();
        }
        return member;
    }

    public static interface ExecuteQueryHook {
        void onExecuteQuery(String sql);
    }

    /**
     * Modifies a bitkey so that it includes the proper bits
     * for members in an array which should be considered
     * as a limited rollup member.
     */
    public static void constraintBitkeyForLimitedMembers(
        Evaluator evaluator,
        Member[] members,
        RolapCube cube,
        BitKey levelBitKey)
    {
        // Limited Rollup Members have to be included in the bitkey
        // so that we can pick the correct agg table.
        for (Member curMember : members) {
            if (curMember instanceof LimitedRollupMember limitedRollupMember) {
                final int savepoint = evaluator.savepoint();
                try {
                    // set NonEmpty to false to avoid the possibility of
                    // constraining member retrieval by context, which itself
                    // requires determination of limited members, resulting
                    // in infinite loop.
                    evaluator.setNonEmpty(false);
                    List<Member> lowestMembers =
                        ((RolapHierarchy)curMember.getHierarchy())
                            .getLowestMembersForAccess(
                                evaluator,
                                limitedRollupMember
                                    .getHierarchyAccess(),
                                FunUtil.getNonEmptyMemberChildrenWithDetails(
                                    evaluator,
                                    curMember));

                    assert !lowestMembers.isEmpty();

                    Member lowMember = lowestMembers.get(0);

                    while (true) {
                        RolapStar.Column curColumn =
                            ((RolapCubeLevel)lowMember.getLevel())
                                .getBaseStarKeyColumn(cube);

                        if (curColumn != null) {
                            levelBitKey.set(curColumn.getBitPosition());
                        }

                        // If the level doesn't have unique members, we have to
                        // add the parent levels until the keys are unique,
                        // or all of them are added.
                        if (!((RolapCubeLevel)lowMember
                            .getLevel()).isUnique())
                        {
                            lowMember = lowMember.getParentMember();
                            if (lowMember.isAll()) {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                } finally {
                    evaluator.restore(savepoint);
                }
            }
        }
    }

    /**Generates rolap star key based on the fact
     * using fact alias and SQl filter data
     * if this one is present in the fact
     * @param fact the fact based on which is generated the rolap star key
     * @return the rolap star key
     */
    public static List<String> makeRolapStarKey(
        final org.eclipse.daanse.rolap.mapping.model.RelationalQuery fact)
    {
      List<String> rlStarKey = new ArrayList<>();
      org.eclipse.daanse.rolap.mapping.model.TableQuery table = null;
      rlStarKey.add(getAlias(fact));
      if (fact instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery t) {
        table = t;
      }
      // Add SQL filter to the key
      if (!Util.isNull(table) && table != null && !Util.isNull(table.getSqlWhereExpression())
          && !Util.isBlank(table.getSqlWhereExpression().getSql()))
      {
        for (String dialect : table.getSqlWhereExpression().getDialects()) {
            rlStarKey.add(dialect);
        }
        rlStarKey.add(table.getSqlWhereExpression().getSql());
      }
      return Collections.unmodifiableList(rlStarKey);
    }



    /**
     * Determines whether the GROUP BY clause is required, based on the
     * schema definitions of the hierarchy and level properties.
     *
     * The GROUP BY clause may only be eliminated if the level identified by
     * the uniqueKeyLevelName exists, the query is at a depth to include it, and all properties in the included levels are
     * functionally dependent on their level values.
     *
     * @param hierarchy  Hierarchy of the cube
     * @param levels     Levels in this hierarchy
     * @param levelDepth Level depth at which the query is occuring
     * @return whether the GROUP BY is needed
     */
    public static boolean isGroupByNeeded(
            RolapHierarchy hierarchy,
            List<RolapLevel> levels,
            int levelDepth ) {
        // Figure out if we need to generate GROUP BY at all.  It may only be
        // eliminated if we are at a depth that includes the unique key level,
        // and all properties of included levels depend on the level value.
        boolean needsGroupBy = false;  // figure out if we need GROUP BY at all

        if ( hierarchy.getUniqueKeyLevelName() == null ) {
            needsGroupBy = true;
        } else {
            boolean foundUniqueKeyLevelName = false;
            for ( int i = 0; i <= levelDepth; i++ ) {
                RolapLevel lvl = levels.get( i );

                // can ignore the "all" level
                if ( !( lvl.isAll() ) ) {
                    if ( hierarchy.getUniqueKeyLevelName().equals(
                            lvl.getName() ) ) {
                        foundUniqueKeyLevelName = true;
                    }
                    for ( RolapProperty p : lvl.getProperties() ) {
                        if ( !p.dependsOnLevelValue() ) {
                            needsGroupBy = true;
                            // GROUP BY is required, so break out of
                            // properties loop
                            break;
                        }
                    }
                    if ( needsGroupBy ) {
                        // GROUP BY is required, so break out of levels loop
                        break;
                    }
                }
            }
            if ( !foundUniqueKeyLevelName ) {
                // if we're not deep enough to be unique,
                // then the GROUP BY is required
                needsGroupBy = true;
            }
        }

        return needsGroupBy;
    }

}
