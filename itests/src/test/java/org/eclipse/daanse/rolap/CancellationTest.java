/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/
package org.eclipse.daanse.rolap;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.function.def.crossjoin.CrossJoinFunDef;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.rolap.common.result.RolapResult;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.olap.fun.CrossJoinTest;

@RolapContextTest(FoodmartTestInstance.class)
class CancellationTest {

    @BeforeEach
    public void beforeEach() {

    }

    @AfterEach
    public void afterEach() {
    }

    private Cube cubeByName(Connection connection, String cubeName) {
        CatalogReader reader = connection.getCatalogReader().withLocus();
        List<Cube> cubes = reader.getCubes();
        Cube resultCube = null;
        for (Cube cube : cubes) {
            if (cubeName.equals(cube.getName())) {
                resultCube = cube;
                break;
            }
        }
        return resultCube;
    }

    private TupleList productMembersPotScrubbersPotsAndPans(
            CatalogReader salesCubeCatalogReader)
    {
        return new UnaryTupleList(Arrays.asList(
            salesCubeCatalogReader.getMemberByUniqueName(
                IdImpl.toList(
                    "Product", "All Products", "Non-Consumable", "Household",
                    "Kitchen Products", "Pot Scrubbers", "Cormorant"),
                true),
            salesCubeCatalogReader.getMemberByUniqueName(
                IdImpl.toList(
                    "Product", "All Products", "Non-Consumable", "Household",
                    "Kitchen Products", "Pot Scrubbers", "Denny"),
                true),
            salesCubeCatalogReader.getMemberByUniqueName(
                IdImpl.toList(
                    "Product", "All Products", "Non-Consumable", "Household",
                    "Kitchen Products", "Pot Scrubbers", "Red Wing"),
                true),
            salesCubeCatalogReader.getMemberByUniqueName(
                IdImpl.toList(
                    "Product", "All Products", "Non-Consumable", "Household",
                    "Kitchen Products", "Pots and Pans", "Cormorant"),
                true),
            salesCubeCatalogReader.getMemberByUniqueName(
                IdImpl.toList(
                    "Product", "All Products", "Non-Consumable", "Household",
                    "Kitchen Products", "Pots and Pans", "Denny"),
                true),
            salesCubeCatalogReader.getMemberByUniqueName(
                IdImpl.toList(
                    "Product", "All Products", "Non-Consumable", "Household",
                    "Kitchen Products", "Pots and Pans", "High Quality"),
                true),
            salesCubeCatalogReader.getMemberByUniqueName(
                IdImpl.toList(
                    "Product", "All Products", "Non-Consumable", "Household",
                    "Kitchen Products", "Pots and Pans", "Red Wing"),
                true),
            salesCubeCatalogReader.getMemberByUniqueName(
                IdImpl.toList(
                    "Product", "All Products", "Non-Consumable", "Household",
                    "Kitchen Products", "Pots and Pans", "Sunset"),
                true)));
    }

    /**
     * Creates a cell region, runs a query, then flushes the cache.
     */
    @Test
    @RolapConfig(key = ConfigConstants.CHECK_CANCEL_OR_TIMEOUT_INTERVAL, value = "1", type = Integer.class)
    void testNonEmptyListCancellation(Context<?> context) throws OlapRuntimeException {
        // tests that cancellation/timeout is checked in
        // CrossJoinFunDef.nonEmptyList
        CrossJoinFunDefTester crossJoinFunDef =
                new CrossJoinFunDefTester(new CrossJoinTest.NullFunDef().getFunctionMetaData());
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(), "select store.[store name].members on 0 from sales");
        Evaluator eval = ((RolapResult) result).getEvaluator(new int[]{0});
        TupleList list = new UnaryTupleList();
        for (Position pos : result.getAxes()[0].getPositions()) {
            list.add(pos);
        }
        ExecutionImpl exec = spy(new ExecutionImpl(eval.getQuery().getStatement(), Optional.empty()));
        eval.getQuery().getStatement().start(exec);
        CrossJoinFunDef.nonEmptyList(eval, list, null, crossJoinFunDef.getCtag());
        // checkCancelOrTimeout should be called once
        // for each tuple since phase interval is 1
        verify(exec, times(list.size())).checkCancelOrTimeout();
    }

    @Test
    @RolapConfig(key = ConfigConstants.CHECK_CANCEL_OR_TIMEOUT_INTERVAL, value = "1", type = Integer.class)
    void testMutableCrossJoinCancellation(Context<?> context) throws OlapRuntimeException {
        // tests that cancellation/timeout is checked in
        // CrossJoinFunDef.mutableCrossJoin
        Connection connection = context.getConnectionWithDefaultRole();
        RolapCube salesCube = (RolapCube) cubeByName(
             connection,
            "Sales");
        CatalogReader salesCubeCatalogReader =
            salesCube.getCatalogReader(
                    connection.getRole()).withLocus();

        TupleList productMembers =
            productMembersPotScrubbersPotsAndPans(salesCubeCatalogReader);

        String selectGenders = "select Gender.members on 0 from sales";
        Result genders = executeQuery(connection, selectGenders);

        Evaluator gendersEval =
            ((RolapResult) genders).getEvaluator(new int[]{0});
        TupleList genderMembers = new UnaryTupleList();
        for (Position pos : genders.getAxes()[0].getPositions()) {
            genderMembers.add(pos);
        }

        ExecutionImpl execution =
            spy(new ExecutionImpl(genders.getQuery().getStatement(), Optional.empty()));
        execution.asContext().setExecution(execution);
        TupleList mutableCrossJoinResult =
            mutableCrossJoin(productMembers, genderMembers, execution);

        gendersEval.getQuery().getStatement().start(execution);

        // checkCancelOrTimeout should be called once
        // for each tuple from mutableCrossJoin since phase interval is 1
        // plus once for each productMembers item
        // since it gets through SqlStatement.execute
        int expectedCallsQuantity =
            mutableCrossJoinResult.size() + productMembers.size();
        verify(execution, times(expectedCallsQuantity)).checkCancelOrTimeout();
    }

    private TupleList mutableCrossJoin(
        final TupleList list1, final TupleList list2, final ExecutionImpl execution)
        {
            ExecutionMetadata metadata = ExecutionMetadata.of("CancellationTest", "CancellationTest", null, 0);
            return ExecutionContext.where(
                execution.asContext().createChild(metadata, Optional.empty()),
                () -> {
                    return CrossJoinFunDef.mutableCrossJoin(list1, list2);
                });
        }

    class CrossJoinFunDefTester extends CrossJoinFunDef {
        public CrossJoinFunDefTester(FunctionMetaData functionMetaData) {
            super(functionMetaData);
        }

        //@Override
		//public TupleList nonEmptyList(
        //    Evaluator evaluator,
        //    TupleList list,
        //    ResolvedFunCall call)
        //{
        //    return super.nonEmptyList(evaluator, list, call);
        //}
    }
}
