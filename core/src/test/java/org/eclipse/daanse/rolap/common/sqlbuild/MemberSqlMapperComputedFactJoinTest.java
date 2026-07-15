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

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the SQL for the COMPUTED-expression member-children read WITH a fact join — the
 * star-join {@link MemberSqlMapper#childMemberSql(org.eclipse.daanse.rolap.element.RolapLevel,
 * boolean, Optional, List, List, Optional)} overload, with the computed key/ordinal/caption/property
 * projections travelling through the shared {@code projectLevel} RawVariant channel. Rendered with
 * the ANSI dialect (double quotes, {@code CASE WHEN … IS NULL} null collation):
 * <ul>
 *   <li>a {@code <SQL>} expression in the key / ordinal / caption / property position, NO context
 *       columns — the dimension-rooted form ({@code promotion JOIN sales_fact_1997 ON
 *       fact.fk = dim.key}), one test per expression position;</li>
 *   <li>the Customers.Name compound slicer: computed ordinal + properties WITH context columns —
 *       the fact-rooted ordered form (FROM fact, dimension joined in, per-column WHERE conjuncts,
 *       trailing parent-key group).</li>
 * </ul>
 * Needs the Mockito inline mock maker ({@code src/test/resources/mockito-extensions}):
 * {@link RolapCubeLevel} finalizes {@code getHierarchy()}.
 */
class MemberSqlMapperComputedFactJoinTest {

    private final RolapStar star = mock(RolapStar.class);
    private final Dialect ansi = new AnsiDialect();

    private RolapStar.Table table(String name, String alias, RolapStar.Table parent,
            RolapStar.Condition joinCondition) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(alias);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(joinCondition);
        when(t.getStar()).thenReturn(star);
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

    /** A computed ({@code <SQL>}) expression carrying ONE generic variant — the RawVariant payload. */
    private static SqlExpression computed(String genericSql) {
        SqlExpression e = mock(SqlExpression.class);
        org.eclipse.daanse.olap.api.SqlStatement stmt =
                mock(org.eclipse.daanse.olap.api.SqlStatement.class);
        when(stmt.getDialects()).thenReturn(List.of("generic"));
        when(stmt.getSql()).thenReturn(genericSql);
        org.mockito.Mockito.doReturn(List.of(stmt)).when(e).getSqls();
        return e;
    }

    /** A cube level on a single-table relation whose star key lives on {@code starTable}. */
    private RolapCubeLevel level(String uniqueName, SqlExpression keyExp, RolapStar.Table starTable,
            TableSource relation) {
        RolapCubeLevel l = mock(RolapCubeLevel.class);
        // RolapCubeLevel.getHierarchy() covariantly returns RolapCubeHierarchy — mock that type.
        org.eclipse.daanse.rolap.element.RolapCubeHierarchy h =
                mock(org.eclipse.daanse.rolap.element.RolapCubeHierarchy.class);
        when(h.getRelation()).thenReturn(relation);
        org.mockito.Mockito.doReturn(List.of(l)).when(h).getLevels();
        org.mockito.Mockito.doReturn(h).when(l).getHierarchy();
        when(l.getDepth()).thenReturn(0);
        when(l.getUniqueName()).thenReturn(uniqueName);
        when(l.getKeyExp()).thenReturn(keyExp);
        when(l.getInternalType()).thenReturn(BestFitColumnType.STRING);
        when(l.getOrdinalExps()).thenReturn(List.of());
        when(l.getProperties()).thenReturn(new RolapProperty[0]);
        RolapStar.Column starColumn = mock(RolapStar.Column.class);
        when(starColumn.getTable()).thenReturn(starTable);
        when(l.getStarKeyColumn()).thenReturn(starColumn);
        return l;
    }

    private static RolapProperty property(String name, SqlExpression exp,
            boolean dependsOnLevelValue) {
        RolapProperty p = mock(RolapProperty.class);
        when(p.getName()).thenReturn(name);
        when(p.getExp()).thenReturn(exp);
        when(p.dependsOnLevelValue()).thenReturn(dependsOnLevelValue);
        return p;
    }

    /** The ordered star-join overload under test. */
    private String render(RolapCubeLevel level, Optional<Predicate> where,
            List<RolapStar.Table> joinTables,
            List<ConstraintContribution.ColumnPredicate> orderedPredicates,
            Optional<Predicate> memberKeyGroup) {
        return new DialectSqlRenderer(ansi)
                .render(MemberSqlMapper.childMemberSql(level, true, where, joinTables,
                        orderedPredicates, memberKeyGroup))
                .sql();
    }

    // ---- <SQL>-expression shapes -----------------

    /** The Promotions star leg: {@code promotion} fact-adjacent to {@code sales_fact_1997}. */
    private RolapStar.Table promotionLeg(RolapStar.Table fact) {
        return table("promotion", "promotion", fact,
                join("sales_fact_1997", "promotion_id", "promotion", "promotion_id"));
    }

    /**
     * The level KEY is the {@code <SQL>} expression — projected, grouped and ordered as the raw
     * fragment; FROM is the dimension-rooted form ({@code fact.fk = dim.key}).
     */
    @Test
    void byNameExprKeyPosition() {
        RolapStar.Table fact = table("sales_fact_1997", "sales_fact_1997", null, null);
        when(star.getFactTable()).thenReturn(fact);
        RolapCubeLevel level = level("[Promotions].[Promotion Name]",
                computed("RTRIM(\"promotion_name\")"), promotionLeg(fact), tableSource("promotion"));

        String sql = render(level, Optional.empty(), List.of(), List.of(), Optional.empty());

        assertThat(sql).isEqualTo(
                "select RTRIM(\"promotion_name\") as \"c0\""
                + " from \"promotion\" as \"promotion\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\""
                + " group by RTRIM(\"promotion_name\")"
                + " order by CASE WHEN RTRIM(\"promotion_name\") IS NULL THEN 1 ELSE 0 END,"
                + " RTRIM(\"promotion_name\") ASC");
    }

    /**
     * Plain key, {@code <SQL>} ordinal — both projected and grouped, the ORDER BY targets the
     * ORDINAL ({@code c1}).
     */
    @Test
    void byNameExprOrdinalPosition() {
        RolapStar.Table fact = table("sales_fact_1997", "sales_fact_1997", null, null);
        when(star.getFactTable()).thenReturn(fact);
        RolapCubeLevel level = level("[Promotions].[Promotion Name]",
                new RolapColumn("promotion", "promotion_name"), promotionLeg(fact),
                tableSource("promotion"));
        org.mockito.Mockito.doReturn(List.of(computed("RTRIM(\"promotion_name\")")))
                .when(level).getOrdinalExps();

        String sql = render(level, Optional.empty(), List.of(), List.of(), Optional.empty());

        assertThat(sql).isEqualTo(
                "select \"promotion\".\"promotion_name\" as \"c0\","
                + " RTRIM(\"promotion_name\") as \"c1\""
                + " from \"promotion\" as \"promotion\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\""
                + " group by \"promotion\".\"promotion_name\", RTRIM(\"promotion_name\")"
                + " order by CASE WHEN RTRIM(\"promotion_name\") IS NULL THEN 1 ELSE 0 END,"
                + " RTRIM(\"promotion_name\") ASC");
    }

    /**
     * Plain key, {@code <SQL>} caption — caption projected right after the key and grouped, ORDER
     * BY stays on the KEY.
     */
    @Test
    void byNameExprCaptionPosition() {
        RolapStar.Table fact = table("sales_fact_1997", "sales_fact_1997", null, null);
        when(star.getFactTable()).thenReturn(fact);
        RolapCubeLevel level = level("[Promotions].[Promotion Name]",
                new RolapColumn("promotion", "promotion_name"), promotionLeg(fact),
                tableSource("promotion"));
        SqlExpression caption = computed("RTRIM(\"promotion_name\")");
        when(level.hasCaptionColumn()).thenReturn(true);
        when(level.getCaptionExp()).thenReturn(caption);

        String sql = render(level, Optional.empty(), List.of(), List.of(), Optional.empty());

        assertThat(sql).isEqualTo(
                "select \"promotion\".\"promotion_name\" as \"c0\","
                + " RTRIM(\"promotion_name\") as \"c1\""
                + " from \"promotion\" as \"promotion\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\""
                + " group by \"promotion\".\"promotion_name\", RTRIM(\"promotion_name\")"
                + " order by CASE WHEN \"promotion\".\"promotion_name\" IS NULL THEN 1 ELSE 0 END,"
                + " \"promotion\".\"promotion_name\" ASC");
    }

    /**
     * Plain key, {@code <SQL>} {@code $name} property — property projected after the key and grouped
     * ({@code dependsOnLevelValue=false}), ORDER BY stays on the KEY.
     */
    @Test
    void byNameExprPropertyPosition() {
        RolapStar.Table fact = table("sales_fact_1997", "sales_fact_1997", null, null);
        when(star.getFactTable()).thenReturn(fact);
        RolapCubeLevel level = level("[Promotions].[Promotion Name]",
                new RolapColumn("promotion", "promotion_name"), promotionLeg(fact),
                tableSource("promotion"));
        RolapProperty nameProp = property("$name", computed("RTRIM(\"promotion_name\")"), false);
        when(level.getProperties()).thenReturn(new RolapProperty[] { nameProp });

        String sql = render(level, Optional.empty(), List.of(), List.of(), Optional.empty());

        assertThat(sql).isEqualTo(
                "select \"promotion\".\"promotion_name\" as \"c0\","
                + " RTRIM(\"promotion_name\") as \"c1\""
                + " from \"promotion\" as \"promotion\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\""
                + " group by \"promotion\".\"promotion_name\", RTRIM(\"promotion_name\")"
                + " order by CASE WHEN \"promotion\".\"promotion_name\" IS NULL THEN 1 ELSE 0 END,"
                + " \"promotion\".\"promotion_name\" ASC");
    }

    // ---- the Customers.Name compound-slicer shape ------------------

    /**
     * The Customers.Name context-constrained children read: plain key {@code customer_id}, COMPUTED
     * ordinal {@code CONCAT(fname, ' ', lname)} (ordered), computed {@code $name} property
     * ({@code dependsOnLevelValue=true} — projected, NOT grouped) and a plain {@code Gender}
     * property (grouped). Context columns present → the fact-rooted ordered form: FROM the fact, the
     * dimension joined in through the level's star chain, each context restriction its own WHERE
     * conjunct, the parenthesised parent-key group trailing.
     */
    @Test
    void computedFactJoinContextOrderedForm() {
        RolapStar.Table fact = table("sales_fact_1997", "sales_fact_1997", null, null);
        when(star.getFactTable()).thenReturn(fact);
        RolapStar.Table customer = table("customer", "customer", fact,
                join("sales_fact_1997", "customer_id", "customer", "customer_id"));
        RolapCubeLevel level = level("[Customers].[Name]",
                new RolapColumn("customer", "customer_id"), customer, tableSource("customer"));
        SqlExpression fullName = computed("CONCAT(\"customer\".\"fname\", ' ', \"customer\".\"lname\")");
        org.mockito.Mockito.doReturn(List.of(fullName)).when(level).getOrdinalExps();
        RolapProperty nameProp = property("$name", fullName, true);
        RolapProperty genderProp = property("Gender", new RolapColumn("customer", "gender"), false);
        when(level.getProperties()).thenReturn(new RolapProperty[] { nameProp, genderProp });

        Predicate state = Predicates.comparison(
                Expressions.column(TableAlias.of("customer"), "state_province"),
                ComparisonOperator.EQ, Expressions.literal("WA", Datatype.VARCHAR));
        Predicate city = Predicates.comparison(
                Expressions.column(TableAlias.of("customer"), "city"),
                ComparisonOperator.EQ, Expressions.literal("Yakima", Datatype.VARCHAR));
        Predicate parentKey = Predicates.and(List.of(city, state));

        String sql = render(level, Optional.empty(), List.of(customer),
                List.of(new ConstraintContribution.ColumnPredicate(customer, state),
                        new ConstraintContribution.ColumnPredicate(customer, city)),
                Optional.of(parentKey));

        assertThat(sql).isEqualTo(
                "select \"customer\".\"customer_id\" as \"c0\","
                + " CONCAT(\"customer\".\"fname\", ' ', \"customer\".\"lname\") as \"c1\","
                + " CONCAT(\"customer\".\"fname\", ' ', \"customer\".\"lname\") as \"c2\","
                + " \"customer\".\"gender\" as \"c3\""
                + " from \"sales_fact_1997\" as \"sales_fact_1997\""
                + " join \"customer\" as \"customer\""
                + " on \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\""
                + " where \"customer\".\"state_province\" = 'WA'"
                + " and \"customer\".\"city\" = 'Yakima'"
                + " and (\"customer\".\"city\" = 'Yakima' and \"customer\".\"state_province\" = 'WA')"
                + " group by \"customer\".\"customer_id\","
                + " CONCAT(\"customer\".\"fname\", ' ', \"customer\".\"lname\"),"
                + " \"customer\".\"gender\""
                + " order by CASE WHEN CONCAT(\"customer\".\"fname\", ' ', \"customer\".\"lname\")"
                + " IS NULL THEN 1 ELSE 0 END,"
                + " CONCAT(\"customer\".\"fname\", ' ', \"customer\".\"lname\") ASC");
    }
}
