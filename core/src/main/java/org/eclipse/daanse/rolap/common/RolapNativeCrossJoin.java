/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.NativeEvaluator;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.function.def.nonemptycrossjoin.NonEmptyCrossJoinFunDef;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.MemberListCrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.eclipse.daanse.rolap.element.RolapVirtualCube;

/**
 * Creates a {@link org.eclipse.daanse.olap.api.NativeEvaluator} that evaluates NON EMPTY
 * CrossJoin in SQL. The generated SQL will join the dimension tables with
 * the fact table and return all combinations that have a
 * corresponding row in the fact table. The current context (slicer) is
 * used for filtering (WHERE clause in SQL). This very effective computes
 * queries like
 *
 *
 *   SELECT ...
 *   NON EMTPY Crossjoin(
 *       [product].[name].members,
 *       [customer].[name].members) ON ROWS
 *   FROM [Sales]
 *   WHERE ([store].[store #14])
 *
 *
 * where both, customer.name and product.name have many members, but the
 * resulting crossjoin only has few.
 *
 * The implementation currently can not handle sets containting
 * parent/child hierarchies, ragged hierarchies, calculated members and
 * the ALL member. Otherwise all
 *
 * @author av
 * @since Nov 21, 2005
 */
public class RolapNativeCrossJoin extends RolapNativeSet {

    public RolapNativeCrossJoin(boolean enableNativeCrossJoin) {
        super.setEnabled(enableNativeCrossJoin);
    }

    /**
     * Constraint that restricts the result to the current context.
     *
     * If the current context contains calculated members, silently ignores
     * them. This means means that too many members are returned, but this does
     * not matter, because the {@link RolapConnection.NonEmptyResult} will
     * filter out these later.
     */
    static class NonEmptyCrossJoinConstraint extends SetConstraint {
        NonEmptyCrossJoinConstraint(
            CrossJoinArg[] args,
            RolapEvaluator evaluator)
        {
            // Cross join ignores calculated members, including the ones from
            // the slicer.
            super(args, evaluator, false);
        }

        public RolapMember findMember(Object key) {
            for (CrossJoinArg arg : args) {
                if (arg instanceof MemberListCrossJoinArg crossJoinArg) {
                    final List<RolapMember> memberList =
                        crossJoinArg.getMembers();
                    for (RolapMember rolapMember : memberList) {
                        if (key.equals(rolapMember.getKey())) {
                            return rolapMember;
                        }
                    }
                }
            }
            return null;
        }
    }

    @Override
	protected boolean restrictMemberTypes() {
        return false;
    }

    @Override
	NativeEvaluator createEvaluator(
        RolapEvaluator evaluator,
        FunctionDefinition fun,
        Expression[] args, final boolean enableNativeFilter)
    {
        if (!isEnabled()) {
            // native crossjoins were explicitly disabled, so no need
            // to alert about not using them
            return null;
        }
        RolapCube cube = evaluator.getCube();

        List<CrossJoinArg[]> allArgs =
            crossJoinArgFactory()
                .checkCrossJoin(evaluator, fun, args, false);

        // checkCrossJoinArg returns a list of CrossJoinArg arrays.  The first
        // array is the CrossJoin dimensions.  The second array, if any,
        // contains additional constraints on the dimensions. If either the list
        // or the first array is null, then native cross join is not feasible.
        if (allArgs == null || allArgs.isEmpty() || allArgs.get(0) == null) {
            // Something in the arguments to the crossjoin prevented
            // native evaluation; may need to alert
            alertCrossJoinNonNative(
                evaluator,
                fun,
                "arguments not supported");
            return null;
        }

        CrossJoinArg[] cjArgs = allArgs.get(0);

        // check if all CrossJoinArgs are "All" members or Calc members
        // "All" members do not have relational expression, and Calc members
        // in the input could produce incorrect results.
        //
        // If NECJ only has AllMembers, or if there is at least one CalcMember,
        // then sql evaluation is not possible.
        int countNonNativeInputArg = 0;

        for (CrossJoinArg arg : cjArgs) {
            if (arg instanceof MemberListCrossJoinArg cjArg) {
                if (cjArg.hasAllMember() || cjArg.isEmptyCrossJoinArg()) {
                    ++countNonNativeInputArg;
                }
                if (cjArg.hasCalcMembers()) {
                    countNonNativeInputArg = cjArgs.length;
                    break;
                }
            }
        }

        if (countNonNativeInputArg == cjArgs.length) {
            // If all inputs contain "All" members; or
            // if all inputs are MemberListCrossJoinArg with empty member list
            // content, then native evaluation is not feasible.
            alertCrossJoinNonNative(
                evaluator,
                fun,
                "either all arguments contain the ALL member, or empty member lists, or one has a calculated member");
            return null;
        }

        if (isPreferInterpreter(cjArgs, true)) {
            // Native evaluation wouldn't buy us anything, so no
            // need to alert
            return null;
        }

        // Verify that args are valid
        List<RolapLevel> levels = new ArrayList<>();
        for (CrossJoinArg cjArg : cjArgs) {
            RolapLevel level = cjArg.getLevel();
            if (level != null) {
                // Only add non null levels. These levels have real
                // constraints.
                levels.add(level);
            }
        }

        if (SqlConstraintUtils.measuresConflictWithMembers(
                evaluator.getQuery().getMeasuresMembers(), cjArgs))
        {
            alertCrossJoinNonNative(
                evaluator,
                fun,
                "One or more calculated measures conflict with crossjoin args");
            return null;
        }

        if (cube instanceof RolapVirtualCube
            && !evaluator.getQuery().nativeCrossJoinVirtualCube())
        {
            // Something in the query at large (namely, some unsupported
            // function on the [Measures] dimension) prevented native
            // evaluation with virtual cubes; may need to alert
            alertCrossJoinNonNative(
                evaluator,
                fun,
                "not all functions on [Measures] dimension supported");
            return null;
        }

        if (!NonEmptyCrossJoinConstraint.isValidContext(
                evaluator,
                false,
                levels.toArray(new RolapLevel[levels.size()]),
                restrictMemberTypes()))
        {
            alertCrossJoinNonNative(
                evaluator,
                fun,
                "Slicer context does not support native crossjoin.");
            return null;
        }

        // join with fact table will always filter out those members
        // that dont have a row in the fact table
        if (!evaluator.isNonEmpty()) {
            return null;
        }

        LOGGER.debug("using native crossjoin");

        // Create a new evaluation context, eliminating any outer context for
        // the dimensions referenced by the inputs to the NECJ
        // (otherwise, that outer context would be incorrectly intersected
        // with the constraints from the inputs).
        final int savepoint = evaluator.savepoint();

        try {
            overrideContext(evaluator, cjArgs, null);

            // Use the combined CrossJoinArg for the tuple constraint,
            // which will be translated to the SQL WHERE clause.
            CrossJoinArg[] cargs = combineArgs(allArgs);

            // Now construct the TupleConstraint that contains both the CJ
            // dimensions and the additional filter on them. It will make a
            // copy of the evaluator.
            TupleConstraint constraint =
                buildConstraint(evaluator, fun, cargs, enableNativeFilter);
            // Use the just the CJ CrossJoiArg for the evaluator context,
            // which will be translated to select list in sql.
            final CatalogReader schemaReader = evaluator.getCatalogReader();
            return new SetEvaluator(cjArgs, schemaReader, constraint);
        } finally {
            evaluator.restore(savepoint);
        }
    }

    private Set<Member> getCJArgMembers(CrossJoinArg[] cjArgs) {
        Set<Member> members = new HashSet<>();
         for (CrossJoinArg arg : cjArgs) {
             if (arg.getMembers() != null) {
                 members.addAll(arg.getMembers());
             }
         }
         return members;
    }


    CrossJoinArg[] combineArgs(
        List<CrossJoinArg[]> allArgs)
    {
        CrossJoinArg[] cjArgs = allArgs.get(0);
        if (allArgs.size() == 2) {
            CrossJoinArg[] predicateArgs = allArgs.get(1);
            if (predicateArgs != null) {
                // Combine the CJ and the additional predicate args.
                return Util.appendArrays(cjArgs, predicateArgs);
            }
        }
        return cjArgs;
    }

    private TupleConstraint buildConstraint(
        final RolapEvaluator evaluator,
        final FunctionDefinition fun,
        final CrossJoinArg[] cargs, final boolean enableNativeFilter)
    {
        CrossJoinArg[] myArgs;
        if (safeToConstrainByOtherAxes(fun)) {
            myArgs = buildArgs(evaluator, cargs, enableNativeFilter);
        } else {
            myArgs = cargs;
        }
        return new NonEmptyCrossJoinConstraint(myArgs, evaluator);
    }

    private CrossJoinArg[] buildArgs(
        final RolapEvaluator evaluator, final CrossJoinArg[] cargs, final boolean enableNativeFilter)
    {
        Set<CrossJoinArg> joinArgs =
            crossJoinArgFactory().buildConstraintFromAllAxes(evaluator, enableNativeFilter);
        joinArgs.addAll(Arrays.asList(cargs));
        return joinArgs.toArray(new CrossJoinArg[joinArgs.size()]);
    }

    private boolean safeToConstrainByOtherAxes(final FunctionDefinition fun) {
        return !(fun instanceof NonEmptyCrossJoinFunDef);
    }

    private void alertCrossJoinNonNative(
        RolapEvaluator evaluator,
        FunctionDefinition fun,
        String reason)
    {
        if (!(fun instanceof NonEmptyCrossJoinFunDef)) {
            // Only alert for an explicit NonEmptyCrossJoin,
            // since query authors use that to indicate that
            // they expect it to be "wicked fast"
            return;
        }
        if (!evaluator.getQuery().shouldAlertForNonNative(fun)) {
            return;
        }
        RolapUtil.alertNonNative("NonEmptyCrossJoin", reason,
            evaluator.getCube().getCatalog().getInternalConnection().getContext()
                .getConfigValue(ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED_DEFAULT_VALUE, String.class));
    }
}

// End RolapNativeCrossJoin.java

