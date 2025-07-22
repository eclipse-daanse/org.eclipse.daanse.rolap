/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
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

import java.util.List;
import java.util.Set;

import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArgFactory;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;

/**
 * Creates the right constraint for common tasks.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class SqlConstraintFactory {

    static boolean enabled;

    private static final SqlConstraintFactory instance =
        new SqlConstraintFactory();

    /**
     * singleton
     */
    private SqlConstraintFactory() {
    }

    private boolean enabled(final Evaluator context) {
        if (context != null) {
            return enabled && context.nativeEnabled();
        }
        return enabled;
    }

    public static SqlConstraintFactory instance() {
        setNativeNonEmptyValue();
        return instance;
    }

    public static void setNativeNonEmptyValue() {
        enabled = SystemWideProperties.instance().EnableNativeNonEmpty;
    }

    public MemberChildrenConstraint getMemberChildrenConstraint(
        Evaluator context)
    {
        if (!enabled(context)
            || !SqlContextConstraint.isValidContext(context, false))
        {
            return DefaultMemberChildrenConstraint.instance();
        }
        return new SqlContextConstraint((RolapEvaluator) context, false);
    }

    public TupleConstraint getLevelMembersConstraint(Evaluator context) {
        return getLevelMembersConstraint(context, null);
    }

    /**
     * Returns a constraint that restricts the members of a level to those that
     * are non-empty in the given context. If the constraint cannot be
     * implemented (say if native constraints are disabled) returns null.
     *
     * @param context Context within which members must be non-empty
     * @param levels  levels being referenced in the current context
     * @return Constraint
     */
    public TupleConstraint getLevelMembersConstraint(
        Evaluator context,
        Level[] levels)
    {
        // consider refactoring to eliminate unnecessary array
        assert levels == null || levels.length == 1
            : "Multi-element Level arrays are not expected here.";
        if (useDefaultTupleConstraint(context, levels)) {
            return DefaultTupleConstraint.instance();
        }
        if (context.isNonEmpty()) {
            Set<CrossJoinArg> joinArgs =
                new CrossJoinArgFactory(false).buildConstraintFromAllAxes(
                    (RolapEvaluator) context, context.getQuery().getConnection().getContext().getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER, ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class));
            if (joinArgs.size() > 0) {
                return new RolapNativeCrossJoin.NonEmptyCrossJoinConstraint(
                    joinArgs.toArray(
                        new CrossJoinArg[joinArgs.size()]),
                    (RolapEvaluator) context);
            }
        }
        return new SqlContextConstraint((RolapEvaluator) context, false);
    }

    private boolean useDefaultTupleConstraint(
        Evaluator context, Level[] levels)
    {
        if (context == null) {
            return true;
        }
        if (!enabled(context)) {
            return true;
        }
        if (!SqlContextConstraint.isValidContext(
                context, false, levels, false))
        {
            return true;
        }
        final int threshold = context.getQuery().getConnection().getContext()
                .getConfigValue(ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD_DEFAULT_VALUE, Integer.class);
        if (threshold <= 0) {
            return false;
        }
        if (levels != null) {
            long totalCard = 1;
            for (Level level : levels) {
                totalCard *=
                    getLevelCardinality((RolapLevel) level);
                if (totalCard > threshold) {
                    return false;
                }
            }
        }
        return true;
    }

    public MemberChildrenConstraint getChildByNameConstraint(
        RolapMember parent,
        NameSegment childName, int levelPreCacheThreshold)
    {
        // Ragged hierarchies span multiple levels, so SQL WHERE does not work
        // there
        if (useDefaultMemberChildrenConstraint(parent, levelPreCacheThreshold)) {
            return DefaultMemberChildrenConstraint.instance();
        }
        return new ChildByNameConstraint(childName);
    }

    public MemberChildrenConstraint getChildrenByNamesConstraint(
        RolapMember parent,
        List<NameSegment> childNames, int levelPreCacheThreshold)
    {
        if (useDefaultMemberChildrenConstraint(parent, levelPreCacheThreshold)) {
            return DefaultMemberChildrenConstraint.instance();
        }
        return new ChildByNameConstraint(childNames);
    }

    private boolean useDefaultMemberChildrenConstraint(RolapMember parent, int levelPreCacheThreshold) {
        return !enabled
            || parent.getHierarchy().isRagged()
            || (!isDegenerate(parent.getLevel())
            && levelPreCacheThreshold > 0
            && getChildLevelCardinality(parent) < levelPreCacheThreshold);
    }

    private boolean isDegenerate(Level level) {
        if (level instanceof RolapCubeLevel) {
            RolapCubeHierarchy hier = (RolapCubeHierarchy)level
                .getHierarchy();
            return hier.isUsingCubeFact();
        }
        return false;
    }

    private int getChildLevelCardinality(RolapMember parent) {
        RolapLevel level = (RolapLevel)parent.getLevel().getChildLevel();
        if (level == null) {
            // couldn't determine child level, give most pessimistic answer
            return Integer.MAX_VALUE;
        }
        return getLevelCardinality(level);
    }

    private int getLevelCardinality(RolapLevel level) {
        return getCatalogReader(level)
            .getLevelCardinality(level, true, true);
    }

    private CatalogReader getCatalogReader(RolapLevel level) {
        return level.getHierarchy().getRolapCatalog().getCatalogReaderWithDefaultRole();
    }

    /**
     * Returns a constraint that allows to read all children of multiple parents
     * at once using a LevelMember query style. This does not work
     * for parent/child hierarchies.
     *
     * @param parentMembers List of parents (all must belong to same level)
     * @param mcc           The constraint that would return the children for
     *                      each single parent
     * @return constraint
     */
    public TupleConstraint getDescendantsConstraint(
        List<RolapMember> parentMembers,
        MemberChildrenConstraint mcc)
    {
        return new DescendantsConstraint(parentMembers, mcc);
    }
}
