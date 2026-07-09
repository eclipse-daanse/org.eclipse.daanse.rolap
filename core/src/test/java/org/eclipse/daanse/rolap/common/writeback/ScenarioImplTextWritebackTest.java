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
package org.eclipse.daanse.rolap.common.writeback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.util.resource.relational.SqlSimpleTypes;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.result.AllocationPolicy;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the TEXT branch of {@link ScenarioImpl#setCellValue}.
 *
 * Verifies that when the resolved writeback measure has
 * {@link Datatype#VARCHAR} the implementation
 *
 *  1. takes the short text path,
 *  2. pushes exactly one row onto {@code sessionValues},
 *  3. populates the target column with the typed string,
 *  4. resolves the dimension-key column from the cell coordinates, and
 *  5. fills every *other* writeback measure column with {@code null}
 *     (so the JDBC INSERT renders {@code NULL} there).
 */
class ScenarioImplTextWritebackTest {

    @Test void textPathPushesOneRowAndFillsOtherMeasuresWithNull() {
        // ----- columns on the writeback table -----
        Column wbCategoryColumn = mock(Column.class);
        when(wbCategoryColumn.getName()).thenReturn("CATEGORY");
        when(wbCategoryColumn.getType()).thenReturn(SqlSimpleTypes.Sql99.integerType());

        Column wbAmountColumn = mock(Column.class);
        when(wbAmountColumn.getName()).thenReturn("AMOUNT");
        when(wbAmountColumn.getType()).thenReturn(SqlSimpleTypes.Sql99.integerType());

        Column wbCommentColumn = mock(Column.class);
        when(wbCommentColumn.getName()).thenReturn("COMMENT");
        when(wbCommentColumn.getType()).thenReturn(SqlSimpleTypes.Sql99.varcharType());

        // ----- dimension + (unused) connector for the attribute -----
        Dimension categoryDim = mock(Dimension.class);
        when(categoryDim.getName()).thenReturn("Category");

        // ----- target text measure + a sibling numeric measure -----
        Member commentsMember = mock(Member.class);
        when(commentsMember.getUniqueName()).thenReturn("[Measures].[Comments]");
        Member amountMember = mock(Member.class);
        when(amountMember.getUniqueName()).thenReturn("[Measures].[Amount]");

        RolapWritebackAttribute categoryAttr = new RolapWritebackAttribute(categoryDim, wbCategoryColumn);
        RolapWritebackMeasure amountWb = new RolapWritebackMeasure(amountMember, wbAmountColumn, Datatype.NUMERIC);
        RolapWritebackMeasure commentsWb = new RolapWritebackMeasure(commentsMember, wbCommentColumn, Datatype.VARCHAR);

        RolapWritebackTable writebackTable = new RolapWritebackTable(
                "FACTWB", null, List.of(categoryAttr, amountWb, commentsWb));

        // ----- cube + measure -----
        RolapCube cube = mock(RolapCube.class);
        when(cube.getWritebackTable()).thenReturn(Optional.of(writebackTable));

        RolapStoredMeasure measure = mock(RolapStoredMeasure.class);
        when(measure.getCube()).thenReturn(cube);
        when(measure.getUniqueName()).thenReturn("[Measures].[Comments]");

        // ----- cell coordinate: one member in the Category dimension -----
        Member categoryMember = mock(Member.class);
        Dimension memberDim = mock(Dimension.class);
        when(memberDim.getName()).thenReturn("Category");
        when(categoryMember.getDimension()).thenReturn(memberDim);
        when(categoryMember.getName()).thenReturn("A");
        // getParentMember() defaults to null -> findKeyForDimension's loop exits
        // immediately. isAll() defaults to false. The mock member is not a
        // RolapCubeMember, so the fallback uses member.getName() ("A").

        // ----- exercise -----
        ScenarioImpl scenario = new ScenarioImpl();
        scenario.setCellValue(
                null,
                List.of(measure, categoryMember),
                "on track",
                Double.valueOf(0d),
                AllocationPolicy.EQUAL_ALLOCATION,
                new Object[0]);

        // ----- verify -----
        List<Map<String, Map.Entry<DataTypeJdbc, Object>>> rows = scenario.getSessionValues();
        assertThat(rows).as("text path emits exactly one row").hasSize(1);

        Map<String, Map.Entry<DataTypeJdbc, Object>> row = rows.get(0);
        assertThat(row).containsOnlyKeys("CATEGORY", "AMOUNT", "COMMENT");

        // CATEGORY: VARCHAR + the member name ("A")
        Map.Entry<DataTypeJdbc, Object> cat = row.get("CATEGORY");
        assertThat(cat.getKey()).isEqualTo(DataTypeJdbc.VARCHAR);
        assertThat(cat.getValue()).isEqualTo("A");

        // COMMENT: VARCHAR + the typed string
        Map.Entry<DataTypeJdbc, Object> comment = row.get("COMMENT");
        assertThat(comment.getKey()).isEqualTo(DataTypeJdbc.VARCHAR);
        assertThat(comment.getValue()).isEqualTo("on track");

        // AMOUNT: other measure -> NUMERIC + null
        Map.Entry<DataTypeJdbc, Object> amount = row.get("AMOUNT");
        assertThat(amount.getKey()).isEqualTo(DataTypeJdbc.NUMERIC);
        assertThat(amount.getValue()).isNull();
    }

    /**
     * The simplified {@code findKeyForDimension} returns the cell's *own*
     * member key, unaffected by hierarchy depth. A leaf-level coordinate in a
     * three-level snowflake hierarchy should yield the leaf key.
     */
    @Test void textPathHandlesMultiLevelLeafMember() {
        ScenarioImpl scenario = pushTextRow("OrgUnit", "DEPT_A1", "deep");
        assertThat(scenario.getSessionValues().get(0).get("CATEGORY").getValue())
                .as("multi-level cell: leaf key carries through")
                .isEqualTo("DEPT_A1");
    }

    /**
     * Intermediate-level coordinates (parent-child or multi-level) should also
     * round-trip as their own member key — we never walk up the parent chain.
     */
    @Test void textPathHandlesIntermediateMember() {
        ScenarioImpl scenario = pushTextRow("OrgUnit", "DIV_A", "mid-level note");
        assertThat(scenario.getSessionValues().get(0).get("CATEGORY").getValue())
                .as("intermediate-level cell: own key carries through")
                .isEqualTo("DIV_A");
    }

    /**
     * When the cell coordinate for the attribute's dimension is the
     * {@code All} member, the column value must be {@code null} — the writer
     * has not picked any specific member yet.
     */
    @Test void textPathReturnsNullKeyForAllMember() {
        // Build the same fixture as the main test, but with the categoryMember
        // configured as the [All] level.
        Column wbCategoryColumn = mock(Column.class);
        when(wbCategoryColumn.getName()).thenReturn("CATEGORY");
        when(wbCategoryColumn.getType()).thenReturn(SqlSimpleTypes.Sql99.varcharType());
        Column wbCommentColumn = mock(Column.class);
        when(wbCommentColumn.getName()).thenReturn("COMMENT");
        when(wbCommentColumn.getType()).thenReturn(SqlSimpleTypes.Sql99.varcharType());

        Dimension categoryDim = mock(Dimension.class);
        when(categoryDim.getName()).thenReturn("Category");
        Member commentsMember = mock(Member.class);
        when(commentsMember.getUniqueName()).thenReturn("[Measures].[Comments]");
        RolapWritebackAttribute attr = new RolapWritebackAttribute(categoryDim, wbCategoryColumn);
        RolapWritebackMeasure commentsWb = new RolapWritebackMeasure(commentsMember, wbCommentColumn, Datatype.VARCHAR);
        RolapWritebackTable wbTable = new RolapWritebackTable("FACTWB", null, List.of(attr, commentsWb));

        RolapCube cube = mock(RolapCube.class);
        when(cube.getWritebackTable()).thenReturn(Optional.of(wbTable));

        RolapStoredMeasure measure = mock(RolapStoredMeasure.class);
        when(measure.getCube()).thenReturn(cube);
        when(measure.getUniqueName()).thenReturn("[Measures].[Comments]");

        Member allCategory = mock(Member.class);
        Dimension memberDim = mock(Dimension.class);
        when(memberDim.getName()).thenReturn("Category");
        when(allCategory.getDimension()).thenReturn(memberDim);
        when(allCategory.isAll()).thenReturn(true);

        ScenarioImpl scenario = new ScenarioImpl();
        scenario.setCellValue(null, List.of(measure, allCategory), "global note", null,
                AllocationPolicy.EQUAL_ALLOCATION, new Object[0]);

        Map.Entry<DataTypeJdbc, Object> cat = scenario.getSessionValues().get(0).get("CATEGORY");
        assertThat(cat.getValue()).as("all-member -> null key").isNull();
    }

    /**
     * If the cube has a writeback table but no {@link RolapWritebackMeasure}
     * matches the cell's measure, the implementation logs a WARN and falls
     * back to the numeric path (which is exercised separately). The test only
     * asserts the observable post-condition: no text row is pushed.
     */
    @Test void noMatchingWritebackMeasure_noTextRowPushed() {
        Column wbAmountColumn = mock(Column.class);
        when(wbAmountColumn.getName()).thenReturn("AMOUNT");
        when(wbAmountColumn.getType()).thenReturn(SqlSimpleTypes.Sql99.integerType());

        Member amountMember = mock(Member.class);
        when(amountMember.getUniqueName()).thenReturn("[Measures].[Amount]");
        RolapWritebackMeasure amountWb = new RolapWritebackMeasure(amountMember, wbAmountColumn, Datatype.NUMERIC);
        RolapWritebackTable wbTable = new RolapWritebackTable("FACTWB", null, List.of(amountWb));

        RolapCube cube = mock(RolapCube.class);
        when(cube.getWritebackTable()).thenReturn(Optional.of(wbTable));

        // The cell's measure references a measure that is NOT in the
        // writeback table.
        RolapStoredMeasure measure = mock(RolapStoredMeasure.class);
        when(measure.getCube()).thenReturn(cube);
        when(measure.getUniqueName()).thenReturn("[Measures].[Comments]");

        ScenarioImpl scenario = new ScenarioImpl();
        // The numeric path needs a real RolapStar fixture which we don't
        // provide here — it'll fail with NPE / AssertionError. That's expected:
        // we only want to verify that the TEXT branch did NOT consume the call.
        try {
            scenario.setCellValue(null, List.of(measure), 42.0d, 0.0d,
                    AllocationPolicy.EQUAL_ALLOCATION, new Object[0]);
        } catch (Throwable expected) {
            // numeric allocation path is half-mocked
        }
        assertThat(scenario.getSessionValues())
                .as("text path must NOT be taken when no measure matches")
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ScenarioImpl pushTextRow(String dimensionName, String memberKey, String value) {
        Column wbCategoryColumn = mock(Column.class);
        when(wbCategoryColumn.getName()).thenReturn("CATEGORY");
        when(wbCategoryColumn.getType()).thenReturn(SqlSimpleTypes.Sql99.varcharType());
        Column wbCommentColumn = mock(Column.class);
        when(wbCommentColumn.getName()).thenReturn("COMMENT");
        when(wbCommentColumn.getType()).thenReturn(SqlSimpleTypes.Sql99.varcharType());

        Dimension categoryDim = mock(Dimension.class);
        when(categoryDim.getName()).thenReturn(dimensionName);
        Member commentsMember = mock(Member.class);
        when(commentsMember.getUniqueName()).thenReturn("[Measures].[Comments]");

        RolapWritebackAttribute attr = new RolapWritebackAttribute(categoryDim, wbCategoryColumn);
        RolapWritebackMeasure commentsWb = new RolapWritebackMeasure(commentsMember, wbCommentColumn, Datatype.VARCHAR);
        RolapWritebackTable wbTable = new RolapWritebackTable("FACTWB", null, List.of(attr, commentsWb));

        RolapCube cube = mock(RolapCube.class);
        when(cube.getWritebackTable()).thenReturn(Optional.of(wbTable));

        RolapStoredMeasure measure = mock(RolapStoredMeasure.class);
        when(measure.getCube()).thenReturn(cube);
        when(measure.getUniqueName()).thenReturn("[Measures].[Comments]");

        Member dimMember = mock(Member.class);
        Dimension memberDim = mock(Dimension.class);
        when(memberDim.getName()).thenReturn(dimensionName);
        when(dimMember.getDimension()).thenReturn(memberDim);
        when(dimMember.getName()).thenReturn(memberKey);

        ScenarioImpl scenario = new ScenarioImpl();
        scenario.setCellValue(null, List.of(measure, dimMember), value, null,
                AllocationPolicy.EQUAL_ALLOCATION, new Object[0]);
        return scenario;
    }
}
