/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.olap.function.def.member.defaultmember;


import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.Cube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.TimeDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.HierarchyFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelDefinition;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class DefaultMemberFunDefTest {

    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    /**
     * EMF version of TestDefaultMemberModifier
     * Creates Time2 dimension with two hierarchies, where Weekly hierarchy has explicit defaultMember
     */
    public static class TestDefaultMemberModifierEmf implements CatalogMappingSupplier {

        private CatalogImpl catalog;

        public TestDefaultMemberModifierEmf(Catalog cat) {
            // Copy catalog using EcoreUtil
            EcoreUtil.Copier copier  = EmfUtil.copier((CatalogImpl) cat);
            catalog = (CatalogImpl) copier.get(cat);

            // Find Sales cube
            PhysicalCube salesCube = null;
            for (Cube cube : Packages.available(catalog, Cube.class)) {
                if ("Sales".equals(cube.getName()) && cube instanceof PhysicalCube) {
                    salesCube = (PhysicalCube) cube;
                    break;
                }
            }

            if (salesCube != null) {
                // Create first hierarchy (no hasAll) using RolapMappingFactory
                Level yearLevel1 =
                    LevelFactory.eINSTANCE.createLevel();
                yearLevel1.setName("Year");
                yearLevel1.setColumn((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY));
                yearLevel1.setColumnType(ColumnInternalDataType.NUMERIC);
                yearLevel1.setUniqueMembers(true);
                yearLevel1.setType(LevelDefinition.TIME_YEARS);

                Level quarterLevel =
                    LevelFactory.eINSTANCE.createLevel();
                quarterLevel.setName("Quarter");
                quarterLevel.setColumn((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY));
                quarterLevel.setUniqueMembers(false);
                quarterLevel.setType(LevelDefinition.TIME_QUARTERS);

                Level monthLevel =
                    LevelFactory.eINSTANCE.createLevel();
                monthLevel.setName("Month");
                monthLevel.setColumn((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY));
                monthLevel.setUniqueMembers(false);
                monthLevel.setColumnType(ColumnInternalDataType.NUMERIC);
                monthLevel.setType(LevelDefinition.TIME_MONTHS);

                TableSource tableQuery1 =
                    SourceFactory.eINSTANCE.createTableSource();
                tableQuery1.setTable((Table) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.TABLE_TIME_BY_DAY));

                ExplicitHierarchy hierarchy1 =
                    HierarchyFactory.eINSTANCE.createExplicitHierarchy();
                hierarchy1.setHasAll(false);
                hierarchy1.setPrimaryKey((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_TIME_ID_TIME_BY_DAY));
                hierarchy1.setSource(tableQuery1);
                hierarchy1.getLevels().add(yearLevel1);
                hierarchy1.getLevels().add(quarterLevel);
                hierarchy1.getLevels().add(monthLevel);

                // Create second hierarchy (Weekly with defaultMember) using RolapMappingFactory
                Level yearLevel2 =
                    LevelFactory.eINSTANCE.createLevel();
                yearLevel2.setName("Year");
                yearLevel2.setColumn((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY));
                yearLevel2.setColumnType(ColumnInternalDataType.NUMERIC);
                yearLevel2.setUniqueMembers(true);
                yearLevel2.setType(LevelDefinition.TIME_YEARS);

                Level weekLevel =
                    LevelFactory.eINSTANCE.createLevel();
                weekLevel.setName("Week");
                weekLevel.setColumn((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_WEEK_OF_YEAR_TIME_BY_DAY));
                weekLevel.setColumnType(ColumnInternalDataType.NUMERIC);
                weekLevel.setUniqueMembers(false);
                weekLevel.setType(LevelDefinition.TIME_WEEKS);

                Level dayLevel =
                    LevelFactory.eINSTANCE.createLevel();
                dayLevel.setName("Day");
                dayLevel.setColumn((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_DAY_OF_MONTH_TIME_BY_DAY));
                dayLevel.setUniqueMembers(false);
                dayLevel.setColumnType(ColumnInternalDataType.NUMERIC);
                dayLevel.setType(LevelDefinition.TIME_DAYS);

                TableSource tableQuery2 =
                    SourceFactory.eINSTANCE.createTableSource();
                tableQuery2.setTable((Table) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.TABLE_TIME_BY_DAY));

                ExplicitHierarchy hierarchy2 =
                    HierarchyFactory.eINSTANCE.createExplicitHierarchy();
                hierarchy2.setName("Weekly");
                hierarchy2.setHasAll(true);
                hierarchy2.setPrimaryKey((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_TIME_ID_TIME_BY_DAY));
                hierarchy2.setDefaultMember("[Time2].[Weekly].[1997].[23]"); // Set explicit default member
                hierarchy2.setSource(tableQuery2);
                hierarchy2.getLevels().add(yearLevel2);
                hierarchy2.getLevels().add(weekLevel);
                hierarchy2.getLevels().add(dayLevel);

                // Create Time2 dimension using RolapMappingFactory
                TimeDimension timeDimension =
                    DimensionFactory.eINSTANCE.createTimeDimension();
                timeDimension.setName("Time2");
                timeDimension.getHierarchies().add(hierarchy1);
                timeDimension.getHierarchies().add(hierarchy2);

                // Create dimension connector using RolapMappingFactory
                DimensionConnector dimConnector =
                    DimensionFactory.eINSTANCE.createDimensionConnector();
                dimConnector.setOverrideDimensionName("Time2");
                dimConnector.setForeignKey((Column) copier.get(org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier.COLUMN_TIME_ID_SALESFACT));
                dimConnector.setDimension(timeDimension);

                // Add dimension connector to Sales cube
                salesCube.getDimensionConnectors().add(dimConnector);
            }
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }

    @Test
    void testDefaultMember(Context<?> context) {
        // [Time] has no default member and no all, so the default member is
        // the first member of the first level.
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Time].[Time].DefaultMember} on columns\n"
                    + "from Sales" );
        assertEquals(
            "1997",
            result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );

        // [Time].[Weekly] has an all member and no explicit default.
        result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Time].[Weekly].DefaultMember} on columns\n"
                    + "from Sales" );
        assertEquals("All Weeklys",
            result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestDefaultMemberModifierEmf.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testExplicitDefaultMember(Context<?> context) {
        final String memberUname ="[Time2].[Weekly].[1997].[23]";
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      "  <Dimension name=\"Time2\" type=\"TimeDimension\" foreignKey=\"time_id\">\n"
        + "    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">\n"
        + "      <Table name=\"time_by_day\"/>\n"
        + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
        + "          levelType=\"TimeYears\"/>\n"
        + "      <Level name=\"Quarter\" column=\"quarter\" uniqueMembers=\"false\"\n"
        + "          levelType=\"TimeQuarters\"/>\n"
        + "      <Level name=\"Month\" column=\"month_of_year\" uniqueMembers=\"false\" type=\"Numeric\"\n"
        + "          levelType=\"TimeMonths\"/>\n"
        + "    </Hierarchy>\n"
        + "    <Hierarchy hasAll=\"true\" name=\"Weekly\" primaryKey=\"time_id\"\n"
        + "          defaultMember=\""
        + memberUname
        + "\">\n"
        + "      <Table name=\"time_by_day\"/>\n"
        + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
        + "          levelType=\"TimeYears\"/>\n"
        + "      <Level name=\"Week\" column=\"week_of_year\" type=\"Numeric\" uniqueMembers=\"false\"\n"
        + "          levelType=\"TimeWeeks\"/>\n"
        + "      <Level name=\"Day\" column=\"day_of_month\" uniqueMembers=\"false\" type=\"Numeric\"\n"
        + "          levelType=\"TimeDays\"/>\n"
        + "    </Hierarchy>\n"
        + "  </Dimension>" ));
      */
        /*
        class TestDefaultMemberModifier extends PojoMappingModifier {

            public TestDefaultMemberModifier(CatalogMapping catalogMapping) {
                super(catalogMapping);
            }

            protected List<? extends DimensionConnectorMapping> cubeDimensionConnectors(CubeMapping cube) {
                List<DimensionConnectorMapping> result = new ArrayList<>();
                result.addAll(super.cubeDimensionConnectors(cube));
                if ("Sales".equals(cube.getName())) {
                    TimeDimensionMappingImpl dimension = TimeDimensionMappingImpl
                        .builder()
                        .withName("Time2")
                        //.withForeignKey("time_id")
                        .withHierarchies(List.of(
                            ExplicitHierarchyMappingImpl.builder()
                                .withHasAll(false)
                                .withPrimaryKey(FoodmartMappingSupplier.TIME_ID_COLUMN_IN_TIME_BY_DAY)
                                .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.TIME_BY_DAY_TABLE).build())
                                .withLevels(List.of(
                                    LevelMappingImpl.builder()
                                        .withName("Year")
                                        .withColumn(FoodmartMappingSupplier.THE_YEAR_COLUMN_IN_TIME_BY_DAY)
                                        .withType(InternalDataType.NUMERIC)
                                        .withUniqueMembers(true)
                                        .withLevelType(LevelType.TIME_YEARS)
                                        .build(),
                                    LevelMappingImpl.builder()
                                        .withName("Quarter")
                                        .withColumn(FoodmartMappingSupplier.QUARTER_COLUMN_IN_TIME_BY_DAY)
                                        .withUniqueMembers(false)
                                        .withLevelType(LevelType.TIME_QUARTERS)
                                        .build(),
                                    LevelMappingImpl.builder()
                                        .withName("Month")
                                        .withColumn(FoodmartMappingSupplier.MONTH_OF_YEAR_COLUMN_IN_TIME_BY_DAY)
                                        .withUniqueMembers(false)
                                        .withType(InternalDataType.NUMERIC)
                                        .withLevelType(LevelType.TIME_MONTHS)
                                        .build()
                                ))
                                .build(),
                            ExplicitHierarchyMappingImpl.builder()
                                .withHasAll(true)
                                .withName("Weekly")
                                .withPrimaryKey(FoodmartMappingSupplier.TIME_ID_COLUMN_IN_TIME_BY_DAY)
                                .withDefaultMember(memberUname)
                                .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.TIME_BY_DAY_TABLE).build())
                                .withLevels(List.of(
                                    LevelMappingImpl.builder()
                                        .withName("Year")
                                        .withColumn(FoodmartMappingSupplier.THE_YEAR_COLUMN_IN_TIME_BY_DAY)
                                        .withType(InternalDataType.NUMERIC)
                                        .withUniqueMembers(true)
                                        .withLevelType(LevelType.TIME_YEARS)
                                        .build(),
                                    LevelMappingImpl.builder()
                                        .withName("Week")
                                        .withColumn(FoodmartMappingSupplier.WEEK_OF_YEAR_COLUMN_IN_TIME_BY_DAY)
                                        .withType(InternalDataType.NUMERIC)
                                        .withUniqueMembers(false)
                                        .withLevelType(LevelType.TIME_WEEKS)
                                        .build(),
                                    LevelMappingImpl.builder()
                                        .withName("Day")
                                        .withColumn(FoodmartMappingSupplier.DAY_OF_MONTH_COLUMN_TIME_BY_DAY)
                                        .withUniqueMembers(false)
                                        .withType(InternalDataType.NUMERIC)
                                        .withLevelType(LevelType.TIME_DAYS)
                                        .build()
                                ))
                                .build()
                        ))
                        .build();
                    result.add(DimensionConnectorMappingImpl.builder()
                        .withOverrideDimensionName("Time2")
                        .withForeignKey(FoodmartMappingSupplier.TIME_ID_COLUMN_IN_SALES_FACT_1997)
                        .withDimension(dimension)
                        .build());
                }
                return result;
            }
        }
        */

        // In this variant of the schema, Time2.Weekly has an explicit default
        // member.
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Time2].[Weekly].DefaultMember} on columns\n"
                    + "from Sales" );
        assertEquals(
            "23",
            result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );
    }

    @Test
    void testDimensionDefaultMember(Context<?> context) {
      assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Measures].DefaultMember").returns("[Measures].[Unit Sales]");
    }

}
