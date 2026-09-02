/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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


package org.eclipse.daanse.olap.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.agg.Segment;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.function.FunctionResolver;
import org.eclipse.daanse.olap.api.query.Quoting;
import org.eclipse.daanse.olap.api.type.BooleanType;
import org.eclipse.daanse.olap.api.type.DateTimeType;
import org.eclipse.daanse.olap.api.type.DecimalType;
import org.eclipse.daanse.olap.api.type.DimensionType;
import org.eclipse.daanse.olap.api.type.HierarchyType;
import org.eclipse.daanse.olap.api.type.LevelType;
import org.eclipse.daanse.olap.api.type.MemberType;
import org.eclipse.daanse.olap.api.type.NullType;
import org.eclipse.daanse.olap.api.type.NumericType;
import org.eclipse.daanse.olap.api.type.ScalarType;
import org.eclipse.daanse.olap.api.type.SetType;
import org.eclipse.daanse.olap.api.type.StringType;
import org.eclipse.daanse.olap.api.type.TupleType;
import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.olap.util.type.TypeUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit test for mondrian type facility.
 *
 * @author jhyde
 * @since Jan 17, 2008
 */
@RolapContextTest(FoodmartTestInstance.class)
class TypeTest {

    @Test
    void testConversions(Context<?> foodMartContext) {
        final Connection connection = foodMartContext.getConnectionWithDefaultRole();
        Cube salesCube =
            getCubeWithName("Sales", connection.getCatalog().getCubes());
        assertTrue(salesCube != null);
        Dimension customersDimension = null;
        for (Dimension dimension : salesCube.getDimensions()) {
            if (dimension.getName().equals("Customers")) {
                customersDimension = dimension;
            }
        }

        assertTrue(customersDimension != null);
        Hierarchy hierarchy = customersDimension.getHierarchy();
        Member member = hierarchy.getDefaultMember();
        Level level = member.getLevel();
        Type memberType = new MemberType(
            customersDimension, hierarchy, level, member);
        final LevelType levelType =
            new LevelType(customersDimension, hierarchy, level);
        final HierarchyType hierarchyType =
            new HierarchyType(customersDimension, hierarchy);
        final DimensionType dimensionType =
            new DimensionType(customersDimension);
        final StringType stringType = StringType.INSTANCE;
        final ScalarType scalarType = ScalarType.INSTANCE;
        final NumericType numericType = NumericType.INSTANCE;
        final DateTimeType dateTimeType = DateTimeType.INSTANCE;
        final DecimalType decimalType = new DecimalType(10, 2);
        final DecimalType integerType = new DecimalType(7, 0);
        final NullType nullType = NullType.INSTANCE;
        final MemberType unknownMemberType = MemberType.Unknown;
        final TupleType tupleType =
            new TupleType(
                new Type[] {memberType,  unknownMemberType});
        final SetType tupleSetType = new SetType(tupleType);
        final SetType setType = new SetType(memberType);
        final LevelType unknownLevelType = LevelType.Unknown;
        final HierarchyType unknownHierarchyType = HierarchyType.Unknown;
        final DimensionType unknownDimensionType = DimensionType.Unknown;
        final BooleanType booleanType = BooleanType.INSTANCE;
        Type[] types = {
            memberType,
            levelType,
            hierarchyType,
            dimensionType,
            numericType,
            dateTimeType,
            decimalType,
            integerType,
            scalarType,
            nullType,
            stringType,
            booleanType,
            tupleType,
            tupleSetType,
            setType,
            unknownDimensionType,
            unknownHierarchyType,
            unknownLevelType,
            unknownMemberType
        };

        for (Type type : types) {
            // Check that each type is assignable to itself.
            final String desc = type.toString() + ":" + type.getClass();
            assertEquals(type, type.computeCommonType(type, null),desc);

            int[] conversionCount = {0};
            assertEquals(
                type, type.computeCommonType(type, conversionCount),desc);
            assertEquals(0, conversionCount[0]);

            // Check that each scalar type is assignable to nullable with zero
            // conversions.
            if (type instanceof ScalarType) {
                assertEquals(type, type.computeCommonType(nullType, null));
                assertEquals(
                    type, type.computeCommonType(nullType, conversionCount));
                assertEquals(0, conversionCount[0]);
            }
        }

        for (Type fromType : types) {
            for (Type toType : types) {
                Type type = fromType.computeCommonType(toType, null);
                Type type2 = toType.computeCommonType(fromType, null);
                final String desc =
                    "symmetric, from " + fromType + ", to " + toType;
                assertEquals(type, type2, desc);

                int[] conversionCount = {0};
                int[] conversionCount2 = {0};
                type = fromType.computeCommonType(toType, conversionCount);
                type2 = toType.computeCommonType(fromType, conversionCount2);
                if (conversionCount[0] == 0
                    && conversionCount2[0] == 0)
                {
                    assertEquals(type, type2,desc);
                }

                final DataType toCategory = TypeUtil.typeToCategory(toType);
                final List<FunctionResolver.Conversion> conversions =
                    new ArrayList<>();
                final boolean canConvert =
                    TypeUtil.canConvert(
                        0,
                        fromType,
                        toCategory,
                        conversions);
                if (canConvert && conversions.size() == 0 && type == null) {
                    if (!(fromType == memberType && toType == tupleType
                        || fromType == tupleSetType && toType == setType
                        || fromType == setType && toType == tupleSetType))
                    {
                        Assertions.fail(
                            "can convert from " + fromType + " to " + toType
                            + ", but their most general type is null");
                    }
                }
                if (!canConvert && type != null && type.equals(toType)) {
                	Assertions.fail(
                        "cannot convert from " + fromType + " to " + toType
                        + ", but they have a most general type " + type);
                }
            }
        }
    }

    @Test
    void testCommonTypeWhenSetTypeHavingMemberTypeAndTupleType(Context<?> foodMartContext) {
        Connection connection=	foodMartContext.getConnectionWithDefaultRole();
        MemberType measureMemberType =
            getMemberTypeHavingMeasureInIt(getUnitSalesMeasure(connection));

        MemberType genderMemberType =
            getMemberTypeHavingMaleChild(getMaleChild(connection));

        MemberType storeMemberType =
            getStoreMemberType(getStoreChild(connection));

        TupleType tupleType = new TupleType(
            new Type[] {storeMemberType, genderMemberType});

        SetType setTypeWithMember = new SetType(measureMemberType);
        SetType setTypeWithTuple = new SetType(tupleType);

        Type type1 =
            setTypeWithMember.computeCommonType(setTypeWithTuple, null);
        assertNotNull(type1);
        assertTrue(((SetType) type1).getElementType() instanceof TupleType);

        Type type2 =
            setTypeWithTuple.computeCommonType(setTypeWithMember, null);
        assertNotNull(type2);
        assertTrue(((SetType) type2).getElementType() instanceof TupleType);
        assertEquals(type1, type2);
    }

    @Test
    void testCommonTypeOfMemberandTupleTypeIsTupleType(Context<?> foodMartContext) {
        Connection connection=	foodMartContext.getConnectionWithDefaultRole();
        MemberType measureMemberType =
            getMemberTypeHavingMeasureInIt(getUnitSalesMeasure(connection));

        MemberType genderMemberType =
            getMemberTypeHavingMaleChild(getMaleChild(connection));

        MemberType storeMemberType =
            getStoreMemberType(getStoreChild(connection));

        TupleType tupleType = new TupleType(
            new Type[] {storeMemberType, genderMemberType});

        Type type1 = measureMemberType.computeCommonType(tupleType, null);
        assertNotNull(type1);
        assertTrue(type1 instanceof TupleType);

        Type type2 = tupleType.computeCommonType(measureMemberType, null);
        assertNotNull(type2);
        assertTrue(type2 instanceof TupleType);
        assertEquals(type1, type2);
    }

    @Test
    void testCommonTypeBetweenTuplesOfDifferentSizesIsATupleType(Context<?> foodMartContext) {
    Connection connection=	foodMartContext.getConnectionWithDefaultRole();
        MemberType measureMemberType =
            getMemberTypeHavingMeasureInIt(getUnitSalesMeasure(connection));

        MemberType genderMemberType =
            getMemberTypeHavingMaleChild(getMaleChild(connection));

        MemberType storeMemberType =
            getStoreMemberType(getStoreChild(connection));

        TupleType tupleTypeLarger = new TupleType(
            new Type[] {storeMemberType, genderMemberType, measureMemberType});

        TupleType tupleTypeSmaller = new TupleType(
            new Type[] {storeMemberType, genderMemberType});

        Type type1 = tupleTypeSmaller.computeCommonType(tupleTypeLarger, null);
        assertNotNull(type1);
        assertTrue(type1 instanceof TupleType);
        assertTrue(((TupleType) type1).elementTypes[0] instanceof MemberType);
        assertTrue(((TupleType) type1).elementTypes[1] instanceof MemberType);
        assertTrue(((TupleType) type1).elementTypes[2] instanceof ScalarType);

        Type type2 = tupleTypeLarger.computeCommonType(tupleTypeSmaller, null);
        assertNotNull(type2);
        assertTrue(type2 instanceof TupleType);
        assertTrue(((TupleType) type2).elementTypes[0] instanceof MemberType);
        assertTrue(((TupleType) type2).elementTypes[1] instanceof MemberType);
        assertTrue(((TupleType) type2).elementTypes[2] instanceof ScalarType);
        assertEquals(type1, type2);
    }

    private MemberType getStoreMemberType(Member storeChild) {
        return new MemberType(
            storeChild.getDimension(),
            storeChild.getDimension().getHierarchy(),
            storeChild.getLevel(),
            storeChild);
    }

    private Member getStoreChild(Connection connection) {
        List<Segment> storeParts = Arrays.<Segment>asList(
            new IdImpl.NameSegmentImpl("Store", Quoting.UNQUOTED),
            new IdImpl.NameSegmentImpl("All Stores", Quoting.UNQUOTED),
            new IdImpl.NameSegmentImpl("USA", Quoting.UNQUOTED),
            new IdImpl.NameSegmentImpl("CA", Quoting.UNQUOTED));
        return getSalesCubeCatalogReader(connection).getMemberByUniqueName(
            storeParts, false);
    }

    private MemberType getMemberTypeHavingMaleChild(Member maleChild) {
        return new MemberType(
            maleChild.getDimension(),
            maleChild.getDimension().getHierarchy(),
            maleChild.getLevel(),
            maleChild);
    }

    private MemberType getMemberTypeHavingMeasureInIt(Member unitSalesMeasure) {
        return new MemberType(
            unitSalesMeasure.getDimension(),
            unitSalesMeasure.getDimension().getHierarchy(),
            unitSalesMeasure.getDimension().getHierarchy().getLevels().getFirst(),
            unitSalesMeasure);
    }

    private Member getMaleChild(Connection connection) {
        List<Segment> genderParts = Arrays.<Segment>asList(
            new IdImpl.NameSegmentImpl("Gender", Quoting.UNQUOTED),
            new IdImpl.NameSegmentImpl("M", Quoting.UNQUOTED));
        return getSalesCubeCatalogReader(connection).getMemberByUniqueName(
            genderParts, false);
    }

    private static Member getUnitSalesMeasure(Connection connection) {
        List<Segment> measureParts = Arrays.<Segment>asList(
            new IdImpl.NameSegmentImpl("Measures", Quoting.UNQUOTED),
            new IdImpl.NameSegmentImpl("Unit Sales", Quoting.UNQUOTED));
        return getSalesCubeCatalogReader(connection).getMemberByUniqueName(
            measureParts, false);
    }

    private static CatalogReader getSalesCubeCatalogReader(Connection connection) {
        final Cube salesCube = getCubeWithName(
            "Sales",
            getCatalogReader(connection).getCubes());
        return salesCube.getCatalogReader(
        		connection.getRole()).withLocus();
    }

    private static CatalogReader getCatalogReader(Connection connection) {
        return connection.getCatalogReader().withLocus();
    }

    private static Cube getCubeWithName(String cubeName, List<Cube> cubes) {
        Cube resultCube = null;
        for (Cube cube : cubes) {
            if (cubeName.equals(cube.getName())) {
                resultCube = cube;
                break;
            }
        }
        return resultCube;
    }
}

// End TypeTest.java

