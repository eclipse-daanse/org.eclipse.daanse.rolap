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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the SQL {@link TupleSqlMapper#tupleLevelMembersSql} (and its same-table, comma-product,
 * recorder-join-order and parent-child variants) produces for multi-target tuple reads and
 * virtual-cube union arms — join order, FROM rooting, WHERE/HAVING placement, GROUP BY and ORDER BY
 * (suppressed on a union arm) — plus the routing gates ({@code supportsTupleRead} and friends) that
 * decide which shapes the builder serves.
 * <p>
 * Needs the Mockito inline mock maker ({@code src/test/resources/mockito-extensions}):
 * {@link RolapCubeLevel} finalizes {@code getHierarchy()}.
 */
class TupleSqlMapperTupleReadTest {

    // ---- star fixture (FoodMart sales star) -------------------------------------------------

    private final RolapStar star = mock(RolapStar.class);
    private final RolapStar.Table fact = table("sales_fact_1997", "sales_fact_1997", null, null);
    private final RolapStar.Table customer = table("customer", "customer", fact,
            join("sales_fact_1997", "customer_id", "customer", "customer_id"));
    private final RolapStar.Table store = table("store", "store", fact,
            join("sales_fact_1997", "store_id", "store", "store_id"));
    private final RolapStar.Table product = table("product", "product", fact,
            join("sales_fact_1997", "product_id", "product", "product_id"));
    private final RolapStar.Table productClass = table("product_class", "product_class", product,
            join("product", "product_class_id", "product_class", "product_class_id"));

    private final Dialect ansi = new AnsiDialect();

    TupleSqlMapperTupleReadTest() {
        when(star.getFactTable()).thenReturn(fact);
    }

    private RolapStar.Table table(String name, String alias, RolapStar.Table parent,
            RolapStar.Condition joinCondition) {
        return table(star, name, alias, parent, joinCondition);
    }

    private static RolapStar.Table table(RolapStar owningStar, String name, String alias,
            RolapStar.Table parent, RolapStar.Condition joinCondition) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(alias);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(joinCondition);
        when(t.getStar()).thenReturn(owningStar);
        return t;
    }

    private static RolapStar.Condition join(String leftTable, String leftCol, String rightTable,
            String rightCol) {
        RolapStar.Condition c = mock(RolapStar.Condition.class);
        when(c.leftColumn()).thenReturn(Optional.of(new JoinColumn(leftTable, leftCol)));
        when(c.rightColumn()).thenReturn(Optional.of(new JoinColumn(rightTable, rightCol)));
        return c;
    }

    private static TableSource tableSource(String name) {
        TableSource source = mock(TableSource.class);
        when(source.getAlias()).thenReturn(name);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet.class);
        when(ncs.getName()).thenReturn(name);
        when(source.getTable()).thenReturn(ncs);
        return source;
    }

    /** A two-table snowflake relation: {@code left join right on left.leftKey = right.rightKey}. */
    private static JoinSource joinSource(TableSource leftSource, String leftKeyName,
            TableSource rightSource, String rightKeyName) {
        JoinSource joinSource = mock(JoinSource.class);
        JoinedQueryElement left = mock(JoinedQueryElement.class);
        JoinedQueryElement right = mock(JoinedQueryElement.class);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column leftKey =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class);
        when(leftKey.getName()).thenReturn(leftKeyName);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column rightKey =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class);
        when(rightKey.getName()).thenReturn(rightKeyName);
        when(left.getSource()).thenReturn(leftSource);
        when(left.getKey()).thenReturn(leftKey);
        when(right.getSource()).thenReturn(rightSource);
        when(right.getKey()).thenReturn(rightKey);
        when(joinSource.getLeft()).thenReturn(left);
        when(joinSource.getRight()).thenReturn(right);
        return joinSource;
    }

    /** The FoodMart product relation: {@code product join product_class on product_class_id}. */
    private static JoinSource productJoinSource() {
        return joinSource(tableSource("product"), "product_class_id",
                tableSource("product_class"), "product_class_id");
    }

    private static RolapCubeHierarchy hierarchy(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            RolapCubeLevel... levels) {
        RolapCubeHierarchy h = mock(RolapCubeHierarchy.class);
        when(h.getRelation()).thenReturn(relation);
        // doReturn: getLevels()'s wildcard return defeats when().thenReturn() type inference.
        org.mockito.Mockito.doReturn(List.of(levels)).when(h).getLevels();
        for (RolapCubeLevel level : levels) {
            when(level.getHierarchy()).thenReturn(h);
        }
        return h;
    }

    /** A plain-column cube level whose star key lives on {@code starTable}. */
    private RolapCubeLevel level(int depth, String table, String column, RolapStar.Table starTable) {
        RolapCubeLevel l = mock(RolapCubeLevel.class);
        when(l.getDepth()).thenReturn(depth);
        when(l.getKeyExp()).thenReturn(new RolapColumn(table, column));
        when(l.getInternalType()).thenReturn(BestFitColumnType.STRING);
        when(l.getOrdinalExps()).thenReturn(List.of());
        when(l.getProperties()).thenReturn(new org.eclipse.daanse.rolap.element.RolapProperty[0]);
        when(l.getUniqueName()).thenReturn("[" + table + "].[" + column + "]");
        RolapStar.Column starColumn = mock(RolapStar.Column.class);
        when(starColumn.getTable()).thenReturn(starTable);
        when(l.getBaseStarKeyColumn(null)).thenReturn(starColumn);
        return l;
    }

    private String render(org.eclipse.daanse.sql.statement.api.model.SelectStatement statement) {
        return new DialectSqlRenderer(ansi).render(statement).sql();
    }

    // ---- pins --------------------------------------------------------------------------------

    /**
     * Multi-target tuple read over two single-table dimensions ({@code [Customers].[City]} x
     * {@code [Store Size in SQFT].[Store Sqft]}) with a bare non-empty fact join: both targets'
     * levels projected in target order, ONE fact join through the first target's chain, the second
     * target joined via its own star chain, cross-target GROUP BY and ORDER BY.
     */
    @Test
    void multiTargetTupleReadJoinsSecondTargetThroughTheFact() {
        RolapCubeLevel country = level(0, "customer", "country", customer);
        RolapCubeLevel state = level(1, "customer", "state_province", customer);
        RolapCubeLevel city = level(2, "customer", "city", customer);
        hierarchy(tableSource("customer"), country, state, city);
        RolapCubeLevel sqft = level(0, "store", "store_sqft", store);
        hierarchy(tableSource("store"), sqft);

        List<RolapLevel> targets = List.of(city, sqft);
        assertThat(TupleSqlMapper.supportsTupleRead(targets, null)).isTrue();

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                true, null, true));

        assertThat(sql).isEqualTo(
                "select \"customer\".\"country\" as \"c0\","
                + " \"customer\".\"state_province\" as \"c1\","
                + " \"customer\".\"city\" as \"c2\","
                + " \"store\".\"store_sqft\" as \"c3\""
                + " from \"customer\" as \"customer\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\""
                + " join \"store\" as \"store\""
                + " on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\""
                + " group by \"customer\".\"country\", \"customer\".\"state_province\","
                + " \"customer\".\"city\", \"store\".\"store_sqft\""
                + " order by CASE WHEN \"customer\".\"country\" IS NULL THEN 1 ELSE 0 END,"
                + " \"customer\".\"country\" ASC,"
                + " CASE WHEN \"customer\".\"state_province\" IS NULL THEN 1 ELSE 0 END,"
                + " \"customer\".\"state_province\" ASC,"
                + " CASE WHEN \"customer\".\"city\" IS NULL THEN 1 ELSE 0 END,"
                + " \"customer\".\"city\" ASC,"
                + " CASE WHEN \"store\".\"store_sqft\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_sqft\" ASC");
    }

    /**
     * Multi-target read whose SECOND target is a snowflake dimension ({@code [Store2].[Store State]}
     * x {@code [Product].[Product Family]}), with a member-set restriction on the first target: the
     * second target's chain folds fact-adjacent-first ({@code join product … join product_class}),
     * and the member-set conjunct on the first target's own table contributes WHERE only (no
     * duplicate join).
     */
    @Test
    void multiTargetSnowflakeSecondTargetFoldsFactAdjacentFirst() {
        RolapCubeLevel storeCountry = level(0, "store", "store_country", store);
        RolapCubeLevel storeState = level(1, "store", "store_state", store);
        hierarchy(tableSource("store"), storeCountry, storeState);
        RolapCubeLevel family = level(0, "product_class", "product_family", productClass);
        hierarchy(tableSource("product"), family);

        List<RolapLevel> targets = List.of(storeState, family);
        assertThat(TupleSqlMapper.supportsTupleRead(targets, null)).isTrue();

        // The member-set arg's contribution: one grouped conjunct on the store table (a
        // single-element AND renders parenthesised).
        ConstraintContribution.ColumnPredicate memberSet = new ConstraintContribution.ColumnPredicate(
                store, Predicates.and(List.of(
                        Predicates.raw("\"store\".\"store_state\" in ('BC', 'CA', 'OR')"))));

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(store), List.of(memberSet), Optional.empty(),
                Optional.empty(), true, null, true));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_country\" as \"c0\","
                + " \"store\".\"store_state\" as \"c1\","
                + " \"product_class\".\"product_family\" as \"c2\""
                + " from \"store\" as \"store\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\""
                + " join \"product\" as \"product\""
                + " on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\""
                + " join \"product_class\" as \"product_class\""
                + " on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\""
                + " where (\"store\".\"store_state\" in ('BC', 'CA', 'OR'))"
                + " group by \"store\".\"store_country\", \"store\".\"store_state\","
                + " \"product_class\".\"product_family\""
                + " order by CASE WHEN \"store\".\"store_country\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_country\" ASC,"
                + " CASE WHEN \"store\".\"store_state\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_state\" ASC,"
                + " CASE WHEN \"product_class\".\"product_family\" IS NULL THEN 1 ELSE 0 END,"
                + " \"product_class\".\"product_family\" ASC");
    }

    /**
     * A virtual-cube union arm ({@code whichSelect=NOT_LAST}) for {@code members
     * [Product].[Product Department]}: the same level-members SELECT with the ORDER BY suppressed
     * (it runs inside a {@code union … order by 1, 2} wrapper). The snowflake FROM root stays
     * intact, the per-cube fact joins INTO it, GROUP BY is emitted, NO ORDER BY.
     */
    @Test
    void unionArmSuppressesOrderByAndKeepsGroupBy() {
        RolapCubeLevel family = level(0, "product_class", "product_family", productClass);
        RolapCubeLevel department = level(1, "product_class", "product_department", productClass);
        hierarchy(productJoinSource(), family, department);

        List<RolapLevel> targets = List.of(department);
        assertThat(TupleSqlMapper.supportsTupleRead(targets, null)).isTrue();

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                true, null, false));

        assertThat(sql).isEqualTo(
                "select \"product_class\".\"product_family\" as \"c0\","
                + " \"product_class\".\"product_department\" as \"c1\""
                + " from \"product\" as \"product\""
                + " join \"product_class\" as \"product_class\""
                + " on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\""
                + " group by \"product_class\".\"product_family\","
                + " \"product_class\".\"product_department\"");
    }

    /**
     * Two targets whose star chains SHARE an intermediate table that is also part of the FROM-root
     * relation — HR's {@code [Store].[Store Country] x [Pay Type].[Pay Type]}: store→employee→salary
     * and position→employee→salary over an {@code employee ⋈ store} root. employee is joined
     * exactly ONCE (it anchors both the fact join and the position join) and position attaches
     * directly to it; joining {@code employee} twice under the same alias would make H2 throw
     * {@code Ambiguous column name}.
     */
    @Test
    void multiTargetSharedIntermediateTableIsJoinedOnce() {
        RolapStar hr = mock(RolapStar.class);
        RolapStar.Table salary = table(hr, "salary", "salary", null, null);
        when(hr.getFactTable()).thenReturn(salary);
        RolapStar.Table employee = table(hr, "employee", "employee", salary,
                join("salary", "employee_id", "employee", "employee_id"));
        RolapStar.Table hrStore = table(hr, "store", "store", employee,
                join("employee", "store_id", "store", "store_id"));
        RolapStar.Table position = table(hr, "position", "position", employee,
                join("employee", "position_id", "position", "position_id"));

        // The HR Store dimension's relation: employee ⋈ store — the shared chain table employee
        // is already inside the FROM root.
        RolapCubeLevel storeCountry = level(0, "store", "store_country", hrStore);
        hierarchy(joinSource(tableSource("employee"), "store_id", tableSource("store"), "store_id"),
                storeCountry);
        RolapCubeLevel payType = level(0, "position", "pay_type", position);
        hierarchy(tableSource("position"), payType);

        List<RolapLevel> targets = List.of(storeCountry, payType);
        assertThat(TupleSqlMapper.supportsTupleRead(targets, null)).isTrue();

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                true, null, true));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_country\" as \"c0\","
                + " \"position\".\"pay_type\" as \"c1\""
                + " from \"employee\" as \"employee\""
                + " join \"store\" as \"store\""
                + " on \"employee\".\"store_id\" = \"store\".\"store_id\""
                + " join \"salary\" as \"salary\""
                + " on \"salary\".\"employee_id\" = \"employee\".\"employee_id\""
                + " join \"position\" as \"position\""
                + " on \"employee\".\"position_id\" = \"position\".\"position_id\""
                + " group by \"store\".\"store_country\", \"position\".\"pay_type\""
                + " order by CASE WHEN \"store\".\"store_country\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_country\" ASC,"
                + " CASE WHEN \"position\".\"pay_type\" IS NULL THEN 1 ELSE 0 END,"
                + " \"position\".\"pay_type\" ASC");
    }

    // ---- recorder join order (computed-tuple route) ----------------

    /** First target on customer, second target the Product snowflake (product ⋈ product_class),
     *  a slicer conjunct on the store table. */
    private List<RolapLevel> customerByProductNameTargets() {
        RolapCubeLevel country = level(0, "customer", "country", customer);
        hierarchy(tableSource("customer"), country);
        RolapCubeLevel family = level(0, "product_class", "product_family", productClass);
        RolapCubeLevel productName = level(1, "product", "product_name", product);
        hierarchy(productJoinSource(), family, productName);
        return List.of(country, productName);
    }

    private ConstraintContribution.ColumnPredicate storeSlicer() {
        return new ConstraintContribution.ColumnPredicate(store,
                Predicates.and(List.of(Predicates.raw("\"store\".\"store_name\" = 'Store 14'"))));
    }

    private static final String CUSTOMER_X_PRODUCT_HEAD =
            "select \"customer\".\"country\" as \"c0\","
            + " \"product_class\".\"product_family\" as \"c1\","
            + " \"product\".\"product_name\" as \"c2\""
            + " from \"customer\" as \"customer\""
            + " join \"sales_fact_1997\" as \"sales_fact_1997\""
            + " on \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"";

    private static final String JOIN_PRODUCT =
            " join \"product\" as \"product\""
            + " on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"";

    private static final String JOIN_PRODUCT_CLASS =
            " join \"product_class\" as \"product_class\""
            + " on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"";

    private static final String JOIN_STORE =
            " join \"store\" as \"store\""
            + " on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\"";

    private static final String CUSTOMER_X_PRODUCT_TAIL =
            " where (\"store\".\"store_name\" = 'Store 14')"
            + " group by \"customer\".\"country\", \"product_class\".\"product_family\","
            + " \"product\".\"product_name\""
            + " order by CASE WHEN \"customer\".\"country\" IS NULL THEN 1 ELSE 0 END,"
            + " \"customer\".\"country\" ASC,"
            + " CASE WHEN \"product_class\".\"product_family\" IS NULL THEN 1 ELSE 0 END,"
            + " \"product_class\".\"product_family\" ASC,"
            + " CASE WHEN \"product\".\"product_name\" IS NULL THEN 1 ELSE 0 END,"
            + " \"product\".\"product_name\" ASC";

    /**
     * {@link TupleSqlMapper#tupleLevelMembersSqlRecorderJoinOrder}, used for the computed-expression
     * tuple route: the second target's snowflake chain is emitted CONTIGUOUSLY, fact-adjacent-first
     * ({@code join product} immediately followed by {@code join product_class}), BEFORE the
     * later-registered context table ({@code store}). Everything outside the join sequence is
     * identical to the default path.
     */
    @Test
    void recorderJoinOrderKeepsSnowflakeChainContiguous() {
        List<RolapLevel> targets = customerByProductNameTargets();
        assertThat(TupleSqlMapper.supportsTupleRead(targets, null)).isTrue();

        String sql = render(TupleSqlMapper.tupleLevelMembersSqlRecorderJoinOrder(targets, true,
                Optional.empty(), List.of(store), List.of(storeSlicer()), Optional.empty(),
                Optional.empty(), true, null, true));

        assertThat(sql).isEqualTo(CUSTOMER_X_PRODUCT_HEAD
                + JOIN_PRODUCT + JOIN_PRODUCT_CLASS + JOIN_STORE
                + CUSTOMER_X_PRODUCT_TAIL);
    }

    /**
     * On a shape where the breadth-first fold and the chain-contiguous variant agree (the
     * {@code [Store2].[Store State] x [Product].[Product Family]} tuple read of
     * {@link #multiTargetSnowflakeSecondTargetFoldsFactAdjacentFirst}), both
     * {@link TupleSqlMapper#tupleLevelMembersSql} and
     * {@link TupleSqlMapper#tupleLevelMembersSqlRecorderJoinOrder} emit identical SQL. The two
     * variants differ only on the displaced-snowflake shape pinned by the two tests above.
     */
    @Test
    void setFamilyFlipIsByteNeutralOnAPreviouslyMatchingShape() {
        RolapCubeLevel storeCountry = level(0, "store", "store_country", store);
        RolapCubeLevel storeState = level(1, "store", "store_state", store);
        hierarchy(tableSource("store"), storeCountry, storeState);
        RolapCubeLevel family = level(0, "product_class", "product_family", productClass);
        hierarchy(tableSource("product"), family);
        List<RolapLevel> targets = List.of(storeState, family);
        ConstraintContribution.ColumnPredicate memberSet = new ConstraintContribution.ColumnPredicate(
                store, Predicates.and(List.of(
                        Predicates.raw("\"store\".\"store_state\" in ('BC', 'CA', 'OR')"))));

        String fold = render(TupleSqlMapper.tupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(store), List.of(memberSet), Optional.empty(),
                Optional.empty(), true, null, true));
        String chainContiguous = render(TupleSqlMapper.tupleLevelMembersSqlRecorderJoinOrder(
                targets, true, Optional.empty(), List.of(store), List.of(memberSet),
                Optional.empty(), Optional.empty(), true, null, true));

        assertThat(chainContiguous).isEqualTo(fold);
    }

    /**
     * The standard {@link TupleSqlMapper#tupleLevelMembersSql} keeps the breadth-first fold order —
     * {@code product_class} lands AFTER the later-registered fact-adjacent {@code store} — for the
     * exact same inputs as the recorder-order test above.
     */
    @Test
    void defaultJoinOrderStaysBreadthFirstFold() {
        List<RolapLevel> targets = customerByProductNameTargets();

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(store), List.of(storeSlicer()), Optional.empty(),
                Optional.empty(), true, null, true));

        assertThat(sql).isEqualTo(CUSTOMER_X_PRODUCT_HEAD
                + JOIN_PRODUCT + JOIN_STORE + JOIN_PRODUCT_CLASS
                + CUSTOMER_X_PRODUCT_TAIL);
    }

    // ---- same-table (no-fact-adjacent) route ----------------------

    /** A star whose fact table IS the store table (FoodMart's Store cube): every dimension is
     *  degenerate on it, so no target has a fact-adjacent table. */
    private RolapStar.Table sameTableStoreFact() {
        RolapStar storeStar = mock(RolapStar.class);
        RolapStar.Table storeFact = table(storeStar, "store", "store", null, null);
        when(storeStar.getFactTable()).thenReturn(storeFact);
        return storeFact;
    }

    /**
     * Same-table multi-target build, keys-only shape ({@code [Store Type].[Store Type]} x
     * {@code [Store].[Store State]}): ONE single-table FROM (no fact join), both targets
     * projected/grouped/ordered in target order. {@code supportsTupleRead} declines this shape; the
     * dedicated {@code supportsSameTableTupleRead} gate routes it.
     */
    @Test
    void sameTableTupleReadBuildsSingleTableSelect() {
        RolapStar.Table storeFact = sameTableStoreFact();
        RolapCubeLevel storeType = level(0, "store", "store_type", storeFact);
        hierarchy(tableSource("store"), storeType);
        RolapCubeLevel storeCountry = level(0, "store", "store_country", storeFact);
        RolapCubeLevel storeState = level(1, "store", "store_state", storeFact);
        hierarchy(tableSource("store"), storeCountry, storeState);

        List<RolapLevel> targets = List.of(storeType, storeState);
        assertThat(TupleSqlMapper.supportsTupleRead(targets, null)).isFalse();
        assertThat(TupleSqlMapper.supportsSameTableTupleRead(targets, null, List.of(), List.of()))
                .isTrue();

        String sql = render(TupleSqlMapper.sameTableTupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                null, true));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_type\" as \"c0\","
                + " \"store\".\"store_country\" as \"c1\","
                + " \"store\".\"store_state\" as \"c2\""
                + " from \"store\" as \"store\""
                + " group by \"store\".\"store_type\", \"store\".\"store_country\","
                + " \"store\".\"store_state\""
                + " order by CASE WHEN \"store\".\"store_type\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_type\" ASC,"
                + " CASE WHEN \"store\".\"store_country\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_country\" ASC,"
                + " CASE WHEN \"store\".\"store_state\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_state\" ASC");
    }

    /**
     * Same-table build, duplicate-column + native-HAVING shape ({@code [Store].[Store Name]} x
     * {@code [Store Type].[Store Type]}): the same column projected as a FIRST-target property AND
     * the SECOND target's key keeps BOTH select items under distinct aliases; GROUP BY carries the
     * underlying column ONCE (renderer dedup, first-occurrence order); the ORDER BY covers the KEY
     * columns of both targets (a property is never ordered, so the duplicate key IS); the native
     * HAVING is carried through.
     */
    @Test
    void sameTableDuplicateColumnKeepsBothAliasesGroupsOnceAndCarriesHaving() {
        RolapStar.Table storeFact = sameTableStoreFact();
        RolapCubeLevel storeName = level(0, "store", "store_name", storeFact);
        org.eclipse.daanse.rolap.element.RolapProperty typeProperty =
                mock(org.eclipse.daanse.rolap.element.RolapProperty.class);
        when(typeProperty.getExp()).thenReturn(new RolapColumn("store", "store_type"));
        when(typeProperty.getName()).thenReturn("Store Type");
        when(typeProperty.dependsOnLevelValue()).thenReturn(false);
        when(storeName.getProperties()).thenReturn(
                new org.eclipse.daanse.rolap.element.RolapProperty[] { typeProperty });
        hierarchy(tableSource("store"), storeName);
        RolapCubeLevel storeType = level(0, "store", "store_type", storeFact);
        hierarchy(tableSource("store"), storeType);

        List<RolapLevel> targets = List.of(storeName, storeType);
        assertThat(TupleSqlMapper.supportsTupleRead(targets, null)).isFalse();
        assertThat(TupleSqlMapper.supportsSameTableTupleRead(targets, null, List.of(), List.of()))
                .isTrue();

        String sql = render(TupleSqlMapper.sameTableTupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(), List.of(), Optional.empty(),
                Optional.of(Predicates.raw("not ((sum(\"store\".\"store_sqft\") is null))")),
                null, true));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_name\" as \"c0\","
                + " \"store\".\"store_type\" as \"c1\","
                + " \"store\".\"store_type\" as \"c2\""
                + " from \"store\" as \"store\""
                + " group by \"store\".\"store_name\", \"store\".\"store_type\""
                + " having not ((sum(\"store\".\"store_sqft\") is null))"
                + " order by CASE WHEN \"store\".\"store_name\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_name\" ASC,"
                + " CASE WHEN \"store\".\"store_type\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_type\" ASC");
    }

    /**
     * A further target on ANOTHER table is outside the same-table shape: the routing gate
     * {@code supportsSameTableTupleRead} declines it, and the build method treats being called
     * anyway as a caller bug and throws.
     */
    @Test
    void sameTableDeclinesTargetOutsideSharedTable() {
        RolapStar.Table storeFact = sameTableStoreFact();
        RolapCubeLevel storeType = level(0, "store", "store_type", storeFact);
        hierarchy(tableSource("store"), storeType);
        RolapCubeLevel country = level(0, "customer", "country", customer);
        hierarchy(tableSource("customer"), country);

        List<RolapLevel> targets = List.of(storeType, country);
        assertThat(TupleSqlMapper.supportsSameTableTupleRead(targets, null, List.of(), List.of()))
                .isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> TupleSqlMapper.sameTableTupleLevelMembersSql(targets, true,
                        Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                        null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projection-outside-shared-table");
    }

    // ---- native-order union arm --------

    /**
     * A virtual-cube union arm ({@code emitOrderBy=false}) whose contribution carries a native
     * TopCount measure order: the measure is projected with the trailing alias and the arm ORDERs
     * BY the measure ONLY, while the level ordering stays suppressed (the ordering runs inside the
     * union input, where the renderer emits it verbatim).
     */
    @Test
    void unionArmCarriesNativeOrderMeasureAndDropsLevelOrders() {
        RolapCubeLevel family = level(0, "product_class", "product_family", productClass);
        RolapCubeLevel department = level(1, "product_class", "product_department", productClass);
        hierarchy(productJoinSource(), family, department);

        ConstraintContribution.NativeOrder topCount = new ConstraintContribution.NativeOrder(
                org.eclipse.daanse.sql.statement.api.Expressions.raw(
                        "sum(\"sales_fact_1997\".\"unit_sales\")"),
                org.eclipse.daanse.olap.api.sql.SortingDirection.DESC, true);

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(List.of(department), true,
                Optional.empty(), List.of(), List.of(), Optional.of(topCount), Optional.empty(),
                true, null, false));

        assertThat(sql).isEqualTo(
                "select \"product_class\".\"product_family\" as \"c0\","
                + " \"product_class\".\"product_department\" as \"c1\","
                + " sum(\"sales_fact_1997\".\"unit_sales\") as \"c2\""
                + " from \"product\" as \"product\""
                + " join \"product_class\" as \"product_class\""
                + " on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\""
                + " group by \"product_class\".\"product_family\","
                + " \"product_class\".\"product_department\""
                + " order by CASE WHEN sum(\"sales_fact_1997\".\"unit_sales\") IS NULL"
                + " THEN 1 ELSE 0 END, sum(\"sales_fact_1997\".\"unit_sales\") DESC");
    }

    // ---- comma-product tuple read (no fact join) -------------------------------------------

    /** A store target and the Product snowflake target with NO fact join between them. */
    private List<RolapLevel> storeByProductFamilyTargets() {
        RolapCubeLevel storeCountry = level(0, "store", "store_country", store);
        RolapCubeLevel storeState = level(1, "store", "store_state", store);
        hierarchy(tableSource("store"), storeCountry, storeState);
        RolapCubeLevel family = level(0, "product_class", "product_family", productClass);
        hierarchy(productJoinSource(), family);
        return List.of(storeState, family);
    }

    /**
     * The comma-product tuple read (contribution carries no fact join): FROM = the first target's
     * table, then the further target's tables comma-appended in registration order; the further
     * target's INTERNAL join equality is a WHERE conjunct (the assembler pushes
     * disconnected-component edges into WHERE); the native Filter HAVING is carried; ORDER BY per
     * {@code whichSelect=ONLY}.
     */
    @Test
    void productTupleReadCommaJoinsDisconnectedTargets() {
        List<RolapLevel> targets = storeByProductFamilyTargets();
        assertThat(TupleSqlMapper.supportsProductTupleRead(targets, List.of(), List.of())).isTrue();

        String sql = render(TupleSqlMapper.productTupleLevelMembersSql(targets, true,
                Optional.empty(), List.of(), Optional.empty(),
                Optional.of(Predicates.raw(
                        "\"store\".\"store_state\" is not null and \"store\".\"store_state\" like '%A%'")),
                null, true));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_country\" as \"c0\","
                + " \"store\".\"store_state\" as \"c1\","
                + " \"product_class\".\"product_family\" as \"c2\""
                + " from \"store\" as \"store\", \"product\" as \"product\","
                + " \"product_class\" as \"product_class\""
                + " where \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\""
                + " group by \"store\".\"store_country\", \"store\".\"store_state\","
                + " \"product_class\".\"product_family\""
                + " having \"store\".\"store_state\" is not null and \"store\".\"store_state\" like '%A%'"
                + " order by CASE WHEN \"store\".\"store_country\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_country\" ASC,"
                + " CASE WHEN \"store\".\"store_state\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_state\" ASC,"
                + " CASE WHEN \"product_class\".\"product_family\" IS NULL THEN 1 ELSE 0 END,"
                + " \"product_class\".\"product_family\" ASC");
    }

    /**
     * A context predicate on a table OUTSIDE the targets' relations (here the customer table)
     * cannot reach a comma-product FROM (no fact join to carry it): the gate declines and the build
     * method treats being called anyway as a caller bug.
     */
    @Test
    void productTupleReadDeclinesPredicateOutsideTargets() {
        List<RolapLevel> targets = storeByProductFamilyTargets();
        ConstraintContribution.ColumnPredicate foreign = new ConstraintContribution.ColumnPredicate(
                customer, Predicates.and(List.of(
                        Predicates.raw("\"customer\".\"country\" = 'USA'"))));

        assertThat(TupleSqlMapper.supportsProductTupleRead(targets, List.of(), List.of(foreign)))
                .isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> TupleSqlMapper.productTupleLevelMembersSql(targets, true,
                        Optional.empty(), List.of(foreign), Optional.empty(), Optional.empty(),
                        null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("predicate-outside-targets");
    }

    // ---- schema SQL table filter (FromTable.filter slot) ----------------------------------

    /** A table source declared with a schema {@code <SQL>} WHERE filter. */
    private static TableSource filteredTableSource(String name, String filterSql) {
        TableSource source = tableSource(name);
        org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement filter =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement.class);
        when(filter.getSql()).thenReturn(filterSql);
        when(source.getSqlWhereExpression()).thenReturn(filter);
        return source;
    }

    /**
     * A relation table declared with a schema {@code sqlWhereExpression} carries it into WHERE as
     * its own parenthesised conjunct. The filter is lifted out of the {@code FromTable.filter} slot
     * into a leading explicit conjunct ({@code RelationFromMapper.liftTableFilters}); with no other
     * conjunct the result matches the slot rendering.
     */
    @Test
    void levelMembersCarriesSchemaSqlTableFilterInWhere() {
        RolapCubeLevel storeState = level(0, "store", "store_state", store);
        hierarchy(filteredTableSource("store",
                "\"store\".\"store_id\" in (select distinct \"store_id\" from \"tinysales\")"),
                storeState);

        String sql = render(TupleSqlMapper.levelMembersSql(storeState, true));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_state\" as \"c0\""
                + " from \"store\" as \"store\""
                + " where (\"store\".\"store_id\" in (select distinct \"store_id\" from \"tinysales\"))"
                + " group by \"store\".\"store_state\""
                + " order by CASE WHEN \"store\".\"store_state\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_state\" ASC");
    }

    /**
     * A schema {@code <SQL>} filter on a JOINED table (the fact, entering via the fact-join step)
     * LEADS the WHERE — before the explicit constraint conjuncts — matching the FROM-entry conjunct
     * order ({@code [Product].[(All)] x [Store State]} over a fact declared with {@code store_id in
     * (select distinct …)}). {@code RelationFromMapper.liftTableFilters} places the filter first.
     */
    @Test
    void levelMembersEmitsJoinedTableFilterBeforeExplicitConjuncts() {
        RolapCubeLevel storeState = level(0, "store", "store_state", store);
        hierarchy(tableSource("store"), storeState);
        // Built OUTSIDE the when() call: nesting mock creation inside thenReturn leaves the
        // stubbing unfinished (Mockito).
        TableSource filteredFact = filteredTableSource("sales_fact_1997",
                "\"sales_fact_1997\".\"store_id\" in (select distinct \"store_id\" from \"store\""
                + " where \"store\".\"store_state\" = 'CA')");
        when(fact.getRelation()).thenReturn(filteredFact);
        // A single-element AND renders parenthesised.
        ConstraintContribution.ColumnPredicate country = new ConstraintContribution.ColumnPredicate(
                store, Predicates.and(List.of(
                        Predicates.raw("\"store\".\"store_country\" = 'USA'"))));

        String sql = render(TupleSqlMapper.levelMembersSql(storeState, true,
                Optional.of(Predicates.and(List.of(country.predicate()))), List.of(store),
                List.of(country), Optional.empty(), Optional.empty(), true, null));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_state\" as \"c0\""
                + " from \"store\" as \"store\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\""
                + " where (\"sales_fact_1997\".\"store_id\" in (select distinct \"store_id\""
                + " from \"store\" where \"store\".\"store_state\" = 'CA'))"
                + " and (\"store\".\"store_country\" = 'USA')"
                + " group by \"store\".\"store_state\""
                + " order by CASE WHEN \"store\".\"store_state\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_state\" ASC");
    }

    // ---- parent-child tuple candidate -----------------------------

    /** A parent-child level on the employee table ({@code supervisor_id} parent key). */
    private RolapCubeLevel parentChildLevel() {
        RolapStar hr = mock(RolapStar.class);
        RolapStar.Table salary = table(hr, "salary", "salary", null, null);
        when(hr.getFactTable()).thenReturn(salary);
        RolapStar.Table employee = table(hr, "employee", "employee", salary,
                join("salary", "employee_id", "employee", "employee_id"));
        RolapCubeLevel pc = level(0, "employee", "employee_id", employee);
        when(pc.isParentChild()).thenReturn(true);
        when(pc.getParentExp()).thenReturn(new RolapColumn("employee", "supervisor_id"));
        when(pc.getNullParentValue()).thenReturn(null);
        hierarchy(tableSource("employee"), pc);
        return pc;
    }

    /**
     * The strict tuple gate declines a parent-child target, while the candidate gate
     * ({@code supportsTupleReadAllowingParentChild}) accepts it when the parent key is a plain
     * column.
     */
    @Test
    void parentChildGateStrictDeclinesCandidateAccepts() {
        RolapCubeLevel pc = parentChildLevel();
        assertThat(TupleSqlMapper.supportsTupleRead(List.of(pc), null)).isFalse();
        assertThat(TupleSqlMapper.supportsTupleReadAllowingParentChild(List.of(pc), null)).isTrue();
    }

    /**
     * Parent-child candidate emission, standalone ({@code whichSelect=ONLY}): the parent key is
     * projected AHEAD of the level key (SELECT + GROUP BY) and ordered nulls-FIRST, for a null
     * {@code nullParentValue}.
     */
    @Test
    void parentChildTupleCandidateProjectsParentFirstWithNullsFirstOrder() {
        RolapCubeLevel pc = parentChildLevel();

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(List.of(pc), true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                false, null, true));

        assertThat(sql).isEqualTo(
                "select \"employee\".\"supervisor_id\" as \"c0\","
                + " \"employee\".\"employee_id\" as \"c1\""
                + " from \"employee\" as \"employee\""
                + " group by \"employee\".\"supervisor_id\", \"employee\".\"employee_id\""
                + " order by CASE WHEN \"employee\".\"supervisor_id\" IS NULL THEN 0 ELSE 1 END,"
                + " \"employee\".\"supervisor_id\" ASC,"
                + " CASE WHEN \"employee\".\"employee_id\" IS NULL THEN 1 ELSE 0 END,"
                + " \"employee\".\"employee_id\" ASC");
    }

    /**
     * A NOT_LAST union arm ({@code emitOrderBy=false}) suppresses the parent PROJECTION along with
     * the level ordering (the parent key is projected only for {@code whichSelect} LAST/ONLY),
     * while the parent table still anchors the FROM.
     */
    @Test
    void parentChildUnionArmSuppressesParentProjection() {
        RolapCubeLevel pc = parentChildLevel();

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(List.of(pc), true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                false, null, false));

        assertThat(sql).isEqualTo(
                "select \"employee\".\"employee_id\" as \"c0\""
                + " from \"employee\" as \"employee\""
                + " group by \"employee\".\"employee_id\"");
    }

    // ---- arm-computed candidate -------------------------

    /**
     * A NOT_LAST union arm ({@code emitOrderBy=false}) with a computed-expression further-target
     * key ({@code [Product Family] x RTRIM(quarter)}): the chain-contiguous variant projects/groups
     * the computed key through the RawVariant channel, keeps the first target's snowflake contiguous
     * ({@code product join product_class}) before the fact join, and emits NO ORDER BY (the union
     * wrapper orders by ordinals).
     */
    @Test
    void computedArmSuppressesOrderByAndCarriesComputedKey() {
        RolapStar.Table timeByDay = table("time_by_day", "time_by_day", fact,
                join("sales_fact_1997", "time_id", "time_by_day", "time_id"));
        RolapCubeLevel family = level(0, "product_class", "product_family", productClass);
        hierarchy(productJoinSource(), family);

        RolapCubeLevel quarter = level(0, "time_by_day", "the_year", timeByDay);
        SqlExpression rtrim = computed("RTRIM(quarter)");
        when(quarter.getKeyExp()).thenReturn(rtrim);
        hierarchy(tableSource("time_by_day"), quarter);

        List<RolapLevel> targets = List.of(family, quarter);
        // Strict gate declines (level-unsupported: computed key).
        assertThat(TupleSqlMapper.supportsTupleRead(targets, null)).isFalse();

        String sql = render(TupleSqlMapper.tupleLevelMembersSqlRecorderJoinOrder(targets, true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                true, null, false));

        assertThat(sql).isEqualTo(
                "select \"product_class\".\"product_family\" as \"c0\","
                + " RTRIM(quarter) as \"c1\""
                + " from \"product\" as \"product\""
                + " join \"product_class\" as \"product_class\""
                + " on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\""
                + " join \"time_by_day\" as \"time_by_day\""
                + " on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\""
                + " group by \"product_class\".\"product_family\", RTRIM(quarter)");
    }

    // ---- quiet tuple-gate twin -----------------------------

    /**
     * {@link TupleSqlMapper#supportsTupleReadQuiet} decides the same as the loud gate — accepted
     * shape and {@code projection-outside-scope} decline — but without emitting log lines.
     */
    @Test
    void quietTupleGateTwinMatchesLoudGate() {
        RolapCubeLevel country = level(0, "customer", "country", customer);
        hierarchy(tableSource("customer"), country);
        RolapCubeLevel type = level(0, "store", "store_type", store);
        hierarchy(tableSource("store"), type);

        // Accepted shape: both gates true.
        assertThat(TupleSqlMapper.supportsTupleReadQuiet(List.of(country, type), null)).isTrue();
        assertThat(TupleSqlMapper.supportsTupleRead(List.of(country, type), null)).isTrue();

        // projection-outside-scope decline: a further-target property on a foreign table.
        org.eclipse.daanse.rolap.element.RolapProperty outside =
                mock(org.eclipse.daanse.rolap.element.RolapProperty.class);
        when(outside.getName()).thenReturn("p");
        when(outside.getExp()).thenReturn(new RolapColumn("warehouse", "warehouse_name"));
        when(type.getProperties()).thenReturn(
                new org.eclipse.daanse.rolap.element.RolapProperty[] { outside });

        assertThat(TupleSqlMapper.supportsTupleReadQuiet(List.of(country, type), null)).isFalse();
        assertThat(TupleSqlMapper.supportsTupleRead(List.of(country, type), null)).isFalse();
    }

    /** A computed ({@code <SQL>}) expression carrying ONE generic variant. */
    private static SqlExpression computed(String genericSql) {
        SqlExpression e = mock(SqlExpression.class);
        org.eclipse.daanse.olap.api.SqlStatement stmt =
                mock(org.eclipse.daanse.olap.api.SqlStatement.class);
        when(stmt.getDialects()).thenReturn(List.of("generic"));
        when(stmt.getSql()).thenReturn(genericSql);
        org.mockito.Mockito.doReturn(List.of(stmt)).when(e).getSqls();
        return e;
    }

    // ---- computed-parent parent-child candidate -----------------------

    /**
     * {@link TupleSqlMapper#supportsParentChildComputedParent} accepts EXACTLY the computed-parent
     * parent-child shape ({@code RTRIM(supervisor_id)} parent key), which the strict
     * {@code supportsParentChild} declines; a PLAIN-parent parent-child level is the reverse.
     */
    @Test
    void computedParentGateAcceptsExactlyTheComputedParentShape() {
        RolapCubeLevel pc = parentChildLevel();
        // Plain parent: strict gate true, computed-parent gate false.
        assertThat(TupleSqlMapper.supportsParentChild(pc)).isTrue();
        assertThat(TupleSqlMapper.supportsParentChildComputedParent(pc)).isFalse();

        SqlExpression computedParent = computed("RTRIM(supervisor_id)");
        when(pc.getParentExp()).thenReturn(computedParent);
        assertThat(TupleSqlMapper.supportsParentChild(pc)).isFalse();
        assertThat(TupleSqlMapper.supportsParentChildComputedParent(pc)).isTrue();

        // A computed KEY keeps the level outside (only the parent key is relaxed).
        SqlExpression computedKey = computed("RTRIM(employee_id)");
        when(pc.getKeyExp()).thenReturn(computedKey);
        assertThat(TupleSqlMapper.supportsParentChildComputedParent(pc)).isFalse();
    }

    /**
     * The standalone {@code levelMembersSql(level, dialect)} projects the computed parent FIRST
     * through the RawVariant channel (SELECT + GROUP BY) and collates the non-null
     * {@code nullParentValue} via the null-sort-value ORDER form.
     */
    @Test
    void computedParentCandidateProjectsRawParentWithNullValueCollation() {
        RolapCubeLevel pc = parentChildLevel();
        SqlExpression computedParent = computed("RTRIM(supervisor_id)");
        when(pc.getParentExp()).thenReturn(computedParent);
        when(pc.getNullParentValue()).thenReturn("0");
        when(pc.getDatatype()).thenReturn(org.eclipse.daanse.sql.model.type.Datatype.NUMERIC);

        String sql = render(TupleSqlMapper.levelMembersSql(pc, true));

        assertThat(sql).isEqualTo(
                "select RTRIM(supervisor_id) as \"c0\","
                + " \"employee\".\"employee_id\" as \"c1\""
                + " from \"employee\" as \"employee\""
                + " group by RTRIM(supervisor_id), \"employee\".\"employee_id\""
                + " order by CASE WHEN RTRIM(supervisor_id) = 0 THEN 0 ELSE 1 END,"
                + " RTRIM(supervisor_id) ASC,"
                + " CASE WHEN \"employee\".\"employee_id\" IS NULL THEN 1 ELSE 0 END,"
                + " \"employee\".\"employee_id\" ASC");
    }

    // ---- parent-child count --------------------------------------------

    /**
     * The cardinality gate ({@code countSupports}) accepts a parent-child level, and
     * {@link TupleSqlMapper#levelMemberCountSql} renders the {@code select count(*) from (select
     * distinct <keys>) init} form for it (the count ignores the parent column).
     */
    @Test
    void pcCountSupportedAndBuildsDistinctKeyCount() {
        RolapCubeLevel pc = parentChildLevel();
        when(pc.isUnique()).thenReturn(true);

        assertThat(CountQueries.countSupports(pc)).isTrue();

        String sql = render(CountQueries.levelMemberCountSql(pc));

        assertThat(sql).isEqualTo(
                "select count(*) as \"c0\""
                + " from (select distinct \"employee\".\"employee_id\" as \"c0\""
                + " from \"employee\" as \"employee\") as \"init\"");
    }

    // ---- no-star-key first target anchors FROM at the hierarchy relation -----------------

    /**
     * A single-target read whose level resolves NO star key on the base cube and whose contribution
     * requires no fact join anchors the FROM at the target's hierarchy relation (the mapper's
     * fact==null branch); as a union arm the ORDER BY is suppressed.
     */
    @Test
    void noStarKeySingleTargetAnchorsAtHierarchyRelation() {
        RolapCubeLevel mediaType = level(0, "promotion", "media_type", null);
        when(mediaType.getBaseStarKeyColumn(null)).thenReturn(null);
        hierarchy(tableSource("promotion"), mediaType);

        String sql = render(TupleSqlMapper.tupleLevelMembersSql(List.of(mediaType), true,
                // A grouped member-set conjunct (a nested one-element AND renders parenthesised).
                Optional.of(Predicates.and(List.of(Predicates.and(List.of(
                        Predicates.raw("\"promotion\".\"media_type\" = 'Radio'")))))),
                List.of(), List.of(), Optional.empty(), Optional.empty(), false, null, false));

        assertThat(sql).isEqualTo(
                "select \"promotion\".\"media_type\" as \"c0\""
                + " from \"promotion\" as \"promotion\""
                + " where (\"promotion\".\"media_type\" = 'Radio')"
                + " group by \"promotion\".\"media_type\"");
    }

    /**
     * A tuple read whose further target is not a cube level (no star key to chain through) is
     * rejected by {@link TupleSqlMapper#supportsTupleRead}, so the builder does not produce a FROM
     * that misses the target's table.
     */
    @Test
    void supportsTupleReadRejectsNonCubeSecondTarget() {
        RolapCubeLevel country = level(0, "customer", "country", customer);
        hierarchy(tableSource("customer"), country);

        RolapLevel shared = mock(RolapLevel.class);
        org.eclipse.daanse.rolap.element.RolapHierarchy sharedHierarchy =
                mock(org.eclipse.daanse.rolap.element.RolapHierarchy.class);
        TableSource storeSource = tableSource("store");
        when(sharedHierarchy.getRelation()).thenReturn(storeSource);
        org.mockito.Mockito.doReturn(List.of(shared)).when(sharedHierarchy).getLevels();
        when(shared.getHierarchy()).thenReturn(sharedHierarchy);
        when(shared.getDepth()).thenReturn(0);
        when(shared.getKeyExp()).thenReturn(new RolapColumn("store", "store_sqft"));
        when(shared.getOrdinalExps()).thenReturn(List.of());
        when(shared.getProperties()).thenReturn(new org.eclipse.daanse.rolap.element.RolapProperty[0]);

        assertThat(TupleSqlMapper.supportsTupleRead(List.of(country, shared), null)).isFalse();
    }
}
