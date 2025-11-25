/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2020 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.mdx.model.api.expression.operation.FunctionOperationAtom;
import org.eclipse.daanse.mdx.model.api.expression.operation.OperationAtom;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.Evaluator.SetEvaluator;
import org.eclipse.daanse.olap.api.Validator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.calc.todo.TupleIterable;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.function.FunctionParameter;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.query.component.QueryAxis;
import org.eclipse.daanse.olap.api.query.component.ResolvedFunCall;
import org.eclipse.daanse.olap.api.type.DecimalType;
import org.eclipse.daanse.olap.api.type.NullType;
import org.eclipse.daanse.olap.api.type.TupleType;
import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.AbstractTupleCursor;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList;
import org.eclipse.daanse.olap.function.def.aggregate.AggregateFunDef;
import org.eclipse.daanse.olap.function.def.parentheses.ParenthesesFunDef;
import org.eclipse.daanse.olap.query.component.MemberExpressionImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mockito;

import  org.eclipse.daanse.olap.server.ExecutionImpl;
import org.eclipse.daanse.rolap.element.CompoundSlicerRolapMember;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.common.RolapEvaluator;
import org.eclipse.daanse.rolap.common.RolapEvaluatorRoot;
import org.eclipse.daanse.rolap.common.RolapResult;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.SqlConstraintUtils;
import org.eclipse.daanse.rolap.common.TupleConstraintStruct;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

/**
 * <code>SqlConstraintUtilsTest</code> tests the functions defined in
 * {@link SqlConstraintUtils}.
 *
 */
class SqlConstraintUtilsTest {

    private void assertSameContent(
        String msg, Collection<Member> expected, Collection<Member> actual)
    {
        if (expected == null) {
            assertThat(actual).as(msg).isEqualTo(expected);
        }
        assertThat(actual.size()).as(msg + " size").isEqualTo(expected.size());
        Iterator<Member> itExpected = expected.iterator();
        Iterator<Member> itActual = actual.iterator();
        for (int i = 0; itExpected.hasNext(); i++) {
            assertThat(itExpected.next()).as(msg + " [" + i + "]").isEqualTo(itActual.next());
        }
    }

    private void assertSameContent(
        String msg, List<Member> expected, Member[] actual)
    {
      assertSameContent(msg, expected, Arrays.asList(actual));
    }

    /**
    * Used to suppress a series of asserts on
    * {@code SqlConstraintUtils.expandSupportedCalculatedMembers}
    * when they are supposed to result identically.
    * @param msg message for asserts
    * @param expectedMembersArray expected result
    * @param argMembersArray passed to the tested method
    * @param evaluator passed to the tested method
    */

    private void assertEveryExpandSupportedCalculatedMembers(
        String msg, Member[] expectedMembersArray, Member[] argMembersArray,
        Evaluator evaluator)
    {
      final List<Member> expectedMembersList =
          Collections.unmodifiableList(Arrays.asList(expectedMembersArray));
      final List<Member> argMembersList =
          Collections.unmodifiableList(Arrays.asList(argMembersArray));
      assertSameContent(
          msg + " - (list, eval)",
          expectedMembersList,
          SqlConstraintUtils.expandSupportedCalculatedMembers(
              argMembersList, evaluator).getMembers());
      assertSameContent(
          msg + " - (list, eval, false)",
          expectedMembersList,
          SqlConstraintUtils.expandSupportedCalculatedMembers(
              argMembersList, evaluator, false).getMembers());
      assertSameContent(
          msg + " - (list, eval, true)",
          expectedMembersList,
          SqlConstraintUtils.expandSupportedCalculatedMembers(
              argMembersList, evaluator, true).getMembers());
    }

    private void assertApartExpandSupportedCalculatedMembers(
        String msg,
        Member[] expectedByDefault,
        Member[] expectedOnDisjoint,
        Member[] argMembersArray,
        Evaluator evaluator)
    {
      final List<Member> expectedListOnDisjoin =
          Collections.unmodifiableList(Arrays.asList(expectedOnDisjoint));
      final List<Member> expectedListByDefault =
          Collections.unmodifiableList(Arrays.asList(expectedByDefault));
      final List<Member> argMembersList =
          Collections.unmodifiableList(Arrays.asList(argMembersArray));
      assertSameContent(
          msg + " - (list, eval)",
          expectedListByDefault,
          SqlConstraintUtils.expandSupportedCalculatedMembers(
              argMembersList, evaluator).getMembers());
      assertSameContent(
          msg + " - (list, eval, false)",
          expectedListByDefault,
          SqlConstraintUtils.expandSupportedCalculatedMembers(
              argMembersList, evaluator, false).getMembers());
      assertSameContent(
          msg + " - (list, eval, true)",
          expectedListOnDisjoin,
          SqlConstraintUtils.expandSupportedCalculatedMembers(
              argMembersList, evaluator, true).getMembers());
    }

    private Member makeNoncalculatedMember(String toString) {
        Member member = Mockito.mock(Member.class);
        assertThat(member.isCalculated()).isFalse();
        Mockito.doReturn("mock[" + toString + "]").when(member).toString();
        return member;
    }

    private Expression makeSupportedExpressionForCalculatedMember() {
        Expression memberExpr = new MemberExpressionImpl(Mockito.mock(Member.class));
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            memberExpr)).isTrue();
        return memberExpr;
    }

    private Expression makeUnsupportedExpressionForCalculatedMember() {
        Expression nullFunDefExpr = new ResolvedFunCallImpl(
            new NullFunDef(), new Expression[]{}, NullType.INSTANCE);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            nullFunDefExpr)).isFalse();
        return nullFunDefExpr;
    }

    private Member makeUnsupportedCalculatedMember(String toString) {
        Expression memberExp = makeUnsupportedExpressionForCalculatedMember();
        Member member = Mockito.mock(Member.class);
        Mockito.doReturn("mock[" + toString + "]").when(member).toString();
        Mockito.doReturn(true).when(member).isCalculated();
        Mockito.doReturn(memberExp).when(member).getExpression();

        assertThat(member.isCalculated()).isTrue();
        assertThat(SqlConstraintUtils.isSupportedCalculatedMember(member)).isFalse();

        return member;
    }

    private Member makeMemberExprMember(Member resultMember) {
        Expression memberExp = new MemberExpressionImpl(resultMember);
        Member member = Mockito.mock(Member.class);
        Mockito.doReturn(true).when(member).isCalculated();
        Mockito.doReturn(memberExp).when(member).getExpression();
        return member;
    }

    private Member makeAggregateExprMember(
        Evaluator mockEvaluator, List<Member> endMembers)
    {
        Member member = Mockito.mock(Member.class);
        Mockito.doReturn(true).when(member).isCalculated();

        Member aggregatedMember0 = Mockito.mock(Member.class);
        Expression aggregateArg0 = new MemberExpressionImpl(aggregatedMember0);

        FunctionMetaData functionInformation = Mockito.mock(FunctionMetaData.class);
        OperationAtom functionAtom = new FunctionOperationAtom("dummy");


        Mockito.doReturn(functionAtom).when(functionInformation).operationAtom();


        FunctionDefinition funDef = new AggregateFunDef(functionInformation);
        Expression[] args = new Expression[]{aggregateArg0};
        Type returnType = new DecimalType(1, 1);
        Expression memberExp = new ResolvedFunCallImpl(funDef, args, returnType);

        Mockito.doReturn(memberExp).when(member).getExpression();

        SetEvaluator setEvaluator = Mockito.mock(SetEvaluator.class);
        Mockito.doReturn(setEvaluator)
            .when(mockEvaluator).getSetEvaluator(aggregateArg0, true);
        Mockito.doReturn(
            new UnaryTupleList(endMembers))
            .when(setEvaluator).evaluateTupleIterable();

        assertThat(member.isCalculated()).isTrue();
        assertThat(SqlConstraintUtils.isSupportedCalculatedMember(member)).isTrue();

        return member;
    }

    private Member makeParenthesesExprMember(
        Evaluator evaluator, Member parenthesesInnerMember, String toString)
    {
        Member member = Mockito.mock(Member.class);
        Mockito.doReturn("mock[" + toString + "]").when(member).toString();
        Mockito.doReturn(true).when(member).isCalculated();

        Expression parenthesesArg = new MemberExpressionImpl(parenthesesInnerMember);

        FunctionDefinition funDef = new ParenthesesFunDef(DataType.MEMBER);
        Expression[] args = new Expression[]{parenthesesArg};
        Type returnType = new DecimalType(1, 1);
        Expression memberExp = new ResolvedFunCallImpl(funDef, args, returnType);

        Mockito.doReturn(memberExp).when(member).getExpression();

        assertThat(member.isCalculated()).isTrue();
        assertThat(SqlConstraintUtils.isSupportedCalculatedMember(member)).isTrue();

        return member;
    }

    // ~ Test methods ----------------------------------------------------------
    @Test
    void isSupportedExpressionForCalculatedMember() {
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(null)).as("null expression").isFalse();

        Expression memberExpr = new MemberExpressionImpl(Mockito.mock(Member.class));
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            memberExpr)).as("MemberExpr").isTrue();

        Expression nullFunDefExpr = new ResolvedFunCallImpl(
            new NullFunDef(), new Expression[]{}, NullType.INSTANCE);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            nullFunDefExpr)).as("ResolvedFunCall-NullFunDef").isFalse();

        // ResolvedFunCall arguments
        final Expression argUnsupported = new ResolvedFunCallImpl(
            new NullFunDef(), new Expression[]{}, NullType.INSTANCE);
        final Expression argSupported = new MemberExpressionImpl(Mockito.mock(Member.class));
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            argUnsupported)).isFalse();
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            argSupported)).isTrue();
        final Expression[] noArgs = new Expression[]{};
        final Expression[] args1Unsupported = new Expression[]{argUnsupported};
        final Expression[] args1Supported = new Expression[]{argSupported};
        final Expression[] args2Different = new Expression[]{argUnsupported, argSupported};

        final ParenthesesFunDef parenthesesFunDef =
            new ParenthesesFunDef(DataType.MEMBER);
        Type parenthesesReturnType = new DecimalType(1, 1);
        Expression parenthesesExpr = new ResolvedFunCallImpl(
            parenthesesFunDef, noArgs, parenthesesReturnType);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            parenthesesExpr)).as("ResolvedFunCall-Parentheses()").isTrue();

        parenthesesExpr = new ResolvedFunCallImpl(
            parenthesesFunDef, args1Unsupported, parenthesesReturnType);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            parenthesesExpr)).as("ResolvedFunCall-Parentheses(N)").isFalse();

        parenthesesExpr = new ResolvedFunCallImpl(
            parenthesesFunDef, args1Supported, parenthesesReturnType);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            parenthesesExpr)).as("ResolvedFunCall-Parentheses(Y)").isTrue();

        parenthesesExpr = new ResolvedFunCallImpl(
            parenthesesFunDef, args2Different, parenthesesReturnType);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            parenthesesExpr)).as("ResolvedFunCall-Parentheses(N,Y)").isTrue();

        FunctionMetaData functionInformation = Mockito.mock(FunctionMetaData.class);
        OperationAtom functionAtom = new FunctionOperationAtom("dummy");

        Mockito.doReturn(functionAtom).when(functionInformation).operationAtom();
        FunctionDefinition aggregateFunDef = new AggregateFunDef(functionInformation);
        Type aggregateReturnType = new DecimalType(1, 1);

        Expression aggregateExpr = new ResolvedFunCallImpl(
            aggregateFunDef, noArgs, aggregateReturnType);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            aggregateExpr)).as("ResolvedFunCall-Aggregate()").isTrue();

        aggregateExpr = new ResolvedFunCallImpl(
            aggregateFunDef, args1Unsupported, aggregateReturnType);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            aggregateExpr)).as("ResolvedFunCall-Aggregate(N)").isTrue();

        aggregateExpr = new ResolvedFunCallImpl(
            aggregateFunDef, args1Supported, aggregateReturnType);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            aggregateExpr)).as("ResolvedFunCall-Aggregate(Y)").isTrue();

        aggregateExpr = new ResolvedFunCallImpl(
            aggregateFunDef, args2Different, aggregateReturnType);
        assertThat(SqlConstraintUtils.isSupportedExpressionForCalculatedMember(
            aggregateExpr)).as("ResolvedFunCall-Aggregate(N,Y)").isTrue();
    }

    @Test
    void isSupportedCalculatedMember() {
        Member member = Mockito.mock(Member.class);
        assertThat(member.isCalculated()).isFalse();
        assertThat(SqlConstraintUtils.isSupportedCalculatedMember(member)).isFalse();

        Mockito.doReturn(true).when(member).isCalculated();

        assertThat(member.getExpression()).isNull();
        assertThat(SqlConstraintUtils.isSupportedCalculatedMember(member)).isFalse();

        Mockito.doReturn(makeUnsupportedExpressionForCalculatedMember())
        .when(member).getExpression();
        assertThat(SqlConstraintUtils.isSupportedCalculatedMember(member)).isFalse();

        Mockito.doReturn(makeSupportedExpressionForCalculatedMember())
        .when(member).getExpression();
        assertThat(SqlConstraintUtils.isSupportedCalculatedMember(member)).isTrue();
    }

    @Test
    void expandSupportedCalculatedMemberNotCalculated() {
        // init
        Evaluator evaluator = Mockito.mock(Evaluator.class);

        Member member = makeNoncalculatedMember("0");

        // tested call
        TupleConstraintStruct constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSupportedCalculatedMember(
            member, evaluator, constraint);
        List<Member> r = constraint.getMembers();
        // test
        assertThat(r).isNotNull();
        assertThat(r.size()).isEqualTo(1);
        assertThat(r.get(0)).isSameAs(member);
    }


    @Test
    void expandSupportedCalculatedMemberCalculatedUnsupported() {
        Evaluator evaluator = Mockito.mock(Evaluator.class);

        Member member = makeUnsupportedCalculatedMember("0");

        // tested call
        TupleConstraintStruct constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSupportedCalculatedMember(
            member, evaluator, constraint);
        List<Member> r = constraint.getMembers();
        // test
        assertThat(r).isNotNull();
        assertThat(r.size()).isEqualTo(1);
        assertThat(r.get(0)).isSameAs(member);
    }

    @Test
    void expandSupportedCalculatedMemberCalculatedMemberExpr() {
        Evaluator evaluator = Mockito.mock(Evaluator.class);

        Member resultMember = makeNoncalculatedMember("0");
        Member member = makeMemberExprMember(resultMember);

        // tested call
        TupleConstraintStruct constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSupportedCalculatedMember(
            member, evaluator, constraint);
        List<Member> r = constraint.getMembers();
        // test
        assertThat(r).isNotNull();
        assertThat(r.size()).isEqualTo(1);
        assertThat(r.get(0)).isSameAs(resultMember);
    }

    @Test
    void expandSupportedCalculatedMemberCalculatedAggregate() {
        Evaluator evaluator = Mockito.mock(Evaluator.class);

        Member endMember0 = Mockito.mock(Member.class);
        Member endMember1 = Mockito.mock(Member.class);
        Member endMember2 = Mockito.mock(Member.class);

        Member member = null;
        List<Member> r = null;
        List<Member> aggregatedMembers = null;

        // 0
        aggregatedMembers = Collections.emptyList();
        member = makeAggregateExprMember(evaluator, aggregatedMembers);
        // tested call
        TupleConstraintStruct constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSupportedCalculatedMember(
            member, evaluator, true, constraint);
        r = constraint.getMembers();
        // test
        assertSameContent("",  aggregatedMembers, r);

        // 1
        aggregatedMembers = Collections.singletonList(endMember0);
        member = makeAggregateExprMember(evaluator, aggregatedMembers);
        // tested call
        constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSupportedCalculatedMember(
            member, evaluator, constraint);
        r = constraint.getMembers();
        // test
        assertSameContent("",  aggregatedMembers, r);

        // 2
        aggregatedMembers = Arrays.asList(
            new Member[] {endMember0, endMember1});
        member = makeAggregateExprMember(evaluator, aggregatedMembers);
        // tested call
        constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSupportedCalculatedMember(
            member, evaluator, constraint);
        r = constraint.getMembers();
        // test
        assertSameContent("",  aggregatedMembers, r);

        // 3
        aggregatedMembers = Arrays.asList(
            new Member[] {endMember0, endMember1, endMember2});
        member = makeAggregateExprMember(evaluator, aggregatedMembers);
        // tested call
        constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSupportedCalculatedMember(
            member, evaluator, constraint);
        r = constraint.getMembers();
        // test
        assertSameContent("",  aggregatedMembers, r);
    }

    @Test
    void expandSupportedCalculatedMemberCalculatedParentheses() {
        Evaluator evaluator = Mockito.mock(Evaluator.class);

        Member resultMember = Mockito.mock(Member.class);
        Member member = this.makeParenthesesExprMember(
            evaluator, resultMember, "0");

        // tested call
        TupleConstraintStruct constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSupportedCalculatedMember(
            member, evaluator, constraint);
        List<Member> r = constraint.getMembers();
        // test
        assertThat(r).isNotNull();
        assertThat(r.size()).isEqualTo(1);
        assertThat(r.get(0)).isSameAs(resultMember);
    }

    @Test
    void expandSupportedCalculatedMembers() {
        Evaluator evaluator = Mockito.mock(Evaluator.class);

        Member endMember0 = Mockito.mock(Member.class);
        Member endMember1 = Mockito.mock(Member.class);
        Member endMember2 = Mockito.mock(Member.class);
        Member endMember3 = Mockito.mock(Member.class);

        Member argMember0 = null;
        Member argMember1 = null;

        Member[] argMembers = null;
        Member[] expectedMembers = null;

        // ()
        argMembers = new Member[] {};
        expectedMembers = new Member[] {};
        assertEveryExpandSupportedCalculatedMembers(
            "()", expectedMembers, argMembers, evaluator);

        // (0, 2)
        argMember0 = endMember0;
        argMember1 = endMember2;
        argMembers = new Member[] {argMember0, argMember1};
        expectedMembers = new Member[] {endMember0, endMember2};
        assertEveryExpandSupportedCalculatedMembers(
            "(0, 2)", expectedMembers, argMembers, evaluator);

        // (Aggr(0, 1), 2)
        argMember0 = makeAggregateExprMember(
            evaluator,
            Arrays.asList(new Member[] {endMember0, endMember1}));
        argMember1 = endMember2;
        argMembers = new Member[] {argMember0, argMember1};
        expectedMembers = new Member[] {endMember0, endMember1, endMember2};
        assertEveryExpandSupportedCalculatedMembers(
            "(Aggr(0, 1), 2)", expectedMembers, argMembers, evaluator);

        // (Aggr(0, 1), Aggr(3, 2))
        argMember0 = makeAggregateExprMember(
            evaluator,
            Arrays.asList(new Member[] {endMember0, endMember1}));
        argMember1 = makeAggregateExprMember(
            evaluator,
            Arrays.asList(new Member[] {endMember3, endMember2}));
        argMember1 = endMember2;
        argMembers = new Member[] {argMember0, argMember1};
        expectedMembers = new Member[] {endMember0, endMember1, endMember2};
        assertEveryExpandSupportedCalculatedMembers(
            "(Aggr(0, 1), Aggr(3, 2))", expectedMembers, argMembers, evaluator);
    }


    /**
     * calculation test for disjoint tuples
     */
    @Test
    void getSetFromCalculatedMember() {
        List<Member> listColumn1 = new ArrayList<>();
        List<Member> listColumn2 = new ArrayList<>();

        listColumn1.add(new TestMember("elem1_col1"));
        listColumn1.add(new TestMember("elem2_col1"));
        listColumn2.add(new TestMember("elem1_col2"));
        listColumn2.add(new TestMember("elem2_col2"));

        final List<List<Member>> table = new ArrayList<>();
        table.add(listColumn1);
        table.add(listColumn2);

        List<Member> arrayRes = getCalculatedMember(table, 1).getMembers();

        assertThat(arrayRes.size()).isEqualTo(4);

        assertThat(arrayRes.get(0)).isEqualTo(listColumn1.get(0));
        assertThat(arrayRes.get(1)).isEqualTo(listColumn1.get(1));
        assertThat(arrayRes.get(2)).isEqualTo(listColumn2.get(0));
        assertThat(arrayRes.get(3)).isEqualTo(listColumn2.get(1));
    }

    /**
     * calculation test for disjoint tuples
     */
    @Test
    void getSetFromCalculatedMemberDisjoint() {
        final int ARITY = 2;

        List<Member> listColumn1 = new ArrayList<>();
        List<Member> listColumn2 = new ArrayList<>();

        listColumn1.add(new TestMember("elem1_col1"));
        listColumn1.add(new TestMember("elem2_col1"));
        listColumn2.add(new TestMember("elem1_col2"));
        listColumn2.add(new TestMember("elem2_col2"));

        final List<List<Member>> table = new ArrayList<>();
        table.add(listColumn1);
        table.add(listColumn2);

        TupleConstraintStruct res = getCalculatedMember(table, ARITY);

        TupleList tuple = res.getDisjoinedTupleLists().get(0);

        assertThat(res.getMembers().isEmpty()).isTrue(); // should be empty
        assertThat(ARITY).isEqualTo(tuple.getArity());
        assertThat(listColumn1.get(0)).isEqualTo(tuple.get(0).get(0));
        assertThat(listColumn1.get(1)).isEqualTo(tuple.get(0).get(1));
        assertThat(listColumn2.get(0)).isEqualTo(tuple.get(1).get(0));
        assertThat(listColumn2.get(1)).isEqualTo(tuple.get(1).get(1));
    }

    public TupleConstraintStruct getCalculatedMember(
        final List<List<Member>> table,
        int arity)
    {
        Member memberMock = mock(Member.class);

        Expression[] funCallArgExps = new Expression[0];
        ResolvedFunCallImpl funCallArgMock = new ResolvedFunCallImpl(
            mock(FunctionDefinition.class),
            funCallArgExps, mock(TupleType.class));

        Expression[] funCallExps = {funCallArgMock};
        ResolvedFunCallImpl funCallMock = new ResolvedFunCallImpl(
            mock(FunctionDefinition.class), funCallExps, mock(TupleType.class));

        when(memberMock.getExpression()).thenReturn(funCallMock);

        Evaluator evaluatorMock = mock(Evaluator.class);

        SetEvaluator setEvaluatorMock = mock(
            SetEvaluator.class);

        TupleIterable tupleIterableMock = mock(TupleIterable.class);

        when(tupleIterableMock.iterator()).thenReturn(table.iterator());
        when(tupleIterableMock.getArity()).thenReturn(arity);

        AbstractTupleCursor cursor = new AbstractTupleCursor(arity) {
            Iterator<List<Member>> iterator = table.iterator();
            List<Member> curList;

            @Override
            public boolean forward() {
                boolean hasNext = iterator.hasNext();
                if (hasNext) {
                    curList = iterator.next();
                } else {
                    curList = null;
                }
                return hasNext;
            }

            @Override
            public List<Member> current() {
                return curList;
            }
        };

        when(tupleIterableMock.tupleCursor()).thenReturn(cursor);

        when(setEvaluatorMock.evaluateTupleIterable())
            .thenReturn(tupleIterableMock);

        when(evaluatorMock.getSetEvaluator(eq(funCallArgMock), anyBoolean()))
            .thenReturn(setEvaluatorMock);

        TupleConstraintStruct constraint = new TupleConstraintStruct();
        SqlConstraintUtils.expandSetFromCalculatedMember(
            evaluatorMock, memberMock, constraint);
        return constraint;
    }

    @Test
    void removeCalculatedAndDefaultMembers() {
        Hierarchy hierarchy = mock(Hierarchy.class);

        // create members
        List<Member> members = new ArrayList<>();
        members.add(createMemberMock(false, false, hierarchy)); // 0:passed
        members.add(createMemberMock(true, false, hierarchy)); // 1:not passed
        members.add(createMemberMock(true, true, hierarchy)); // 2:passed
        members.add(createMemberMock(false, true, hierarchy)); // 3:passed
        members.add(createMemberMock(false, true, hierarchy)); // 4:default,
                                                               //   not passed
        members.add(createMemberMock(false, false, hierarchy)); // 5:passed
        members.add(createMemberMock(true, false, hierarchy)); // 6:not passed

        when(hierarchy.getDefaultMember()).thenReturn(members.get(4));

        List<Member> newMembers = SqlConstraintUtils
            .removeCalculatedAndDefaultMembers(members);

        assertThat(newMembers.size()).isEqualTo(4);
        assertThat(newMembers.contains(members.get(0))).isTrue();
        assertThat(newMembers.contains(members.get(2))).isTrue();
        assertThat(newMembers.contains(members.get(3))).isTrue();
        assertThat(newMembers.contains(members.get(5))).isTrue();
    }

    private Member createMemberMock(
        boolean isCalculated,
        boolean isParentChildLeaf,
        Hierarchy hierarchy)
    {
        Member mock = mock(Member.class);
        when(mock.isCalculated()).thenReturn(isCalculated);
        when(mock.isParentChildLeaf()).thenReturn(isParentChildLeaf);
        when(mock.getHierarchy()).thenReturn(hierarchy);
        return mock;
    }


    private void setSlicerContext(RolapEvaluator e, Member m) {
      List<Member> members = new ArrayList<>();
      members.add( m );
      Map<Hierarchy, Set<Member>> membersByHierarchy = new HashMap<>();
      membersByHierarchy.put( m.getHierarchy(), new HashSet<>(members) );
      e.setSlicerContext( members, membersByHierarchy );
    }
    
    
    
    public static class NullFunDef implements FunctionDefinition {
        public NullFunDef() {
        }

        @Override
    	public Expression createCall( Validator validator, Expression[] args ) {
          return null;
        }

        @Override
    	public String getSignature() {
          return "";
        }

        @Override
    	public void unparse( Expression[] args, PrintWriter pw ) {
          //
        }

        @Override
    	public Calc<?> compileCall( ResolvedFunCall call, ExpressionCompiler compiler ) {
          return null;
        }

    	@Override
    	public FunctionMetaData getFunctionMetaData() {
            return new FunctionMetaData() {

                @Override
                public OperationAtom operationAtom() {
                    return new FunctionOperationAtom("");
                }

                @Override
                public String description() {
                    return "";
                }

                @Override
                public DataType returnCategory() {
                      return DataType.UNKNOWN;
                }

                    @Override
                    public DataType[] parameterDataTypes() {
                        return new DataType[ 0 ];
                    }

                    @Override
                    public FunctionParameter[] parameters() {
                        return new FunctionParameter[0];
                    }
           };
    	}
      }
}
