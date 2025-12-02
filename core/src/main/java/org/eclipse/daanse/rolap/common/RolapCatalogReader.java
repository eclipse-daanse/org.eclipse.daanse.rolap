/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * Copyright (C) 2021 Sergei Semenkov
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.IdentifierSegment;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.NameResolver;
import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.NativeEvaluator;
import org.eclipse.daanse.olap.api.Parameter;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.access.AccessDatabaseColumn;
import org.eclipse.daanse.olap.api.access.AccessDatabaseSchema;
import org.eclipse.daanse.olap.api.access.AccessDatabaseTable;
import org.eclipse.daanse.olap.api.access.AccessHierarchy;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.HierarchyAccess;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.DatabaseColumn;
import org.eclipse.daanse.olap.api.element.DatabaseSchema;
import org.eclipse.daanse.olap.api.element.DatabaseTable;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.NamedSet;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.type.StringType;
import org.eclipse.daanse.olap.calc.base.compiler.ElevatorSimplifyer;
import org.eclipse.daanse.olap.calc.base.nested.AbstractProfilingNestedUnknownCalc;
import org.eclipse.daanse.olap.common.NameResolverImpl;
import org.eclipse.daanse.olap.common.ParameterImpl;
import org.eclipse.daanse.olap.common.SystemProperty;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.NullLiteralImpl;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapDatabaseSchema;
import org.eclipse.daanse.rolap.element.RolapDatabaseTable;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RolapCatalogReader allows you to read schema objects while
 * observing the access-control profile specified by a given role.
 *
 * @author jhyde
 * @since Feb 24, 2003
 */
public class RolapCatalogReader
    implements CatalogReader,
        RolapNativeSet.CatalogReaderWithMemberReaderAvailable,
        NameResolver.Namespace
{
    protected final Role role;
    private final Map<Hierarchy, MemberReader> hierarchyReaders =
        new ConcurrentHashMap<>();
    protected final RolapCatalog catalog;
    private final SqlConstraintFactory sqlConstraintFactory =
        SqlConstraintFactory.instance();
    private Context<?> context;
    private static final Logger LOGGER =
        LoggerFactory.getLogger(RolapCatalogReader.class);

    /**
     * Creates a RolapCatalogReader.
     *
     * @param role Role for access control, must not be null
     * @param catalog Schema
     */
    public RolapCatalogReader(Context<?> context, Role role, RolapCatalog catalog) {
        assert role != null : "precondition: role != null";
        assert catalog != null;
        assert context != null;
        this.context=context;
        this.role = role;
        this.catalog = catalog;
    }

    @Override
	public Role getRole() {
        return role;
    }

    @Override
	public List<Member> getHierarchyRootMembers(Hierarchy hierarchy) {
        final HierarchyAccess hierarchyAccess =
            role.getAccessDetails(hierarchy);
        final List<? extends Level> levels = hierarchy.getLevels();
        final Level firstLevel;
        if (hierarchyAccess == null) {
            firstLevel = levels.getFirst();
        } else {
            firstLevel = levels.get(hierarchyAccess.getTopLevelDepth());
        }
        return getLevelMembers(firstLevel, true);
    }

    /**
     * This method uses a double-checked locking idiom to avoid making the
     * method fully synchronized, or potentially creating the same MemberReader
     * more than once.  Double-checked locking can cause issues if
     * a second thread accesses the field without either a shared lock in
     * place or the field being specified as volatile.
     * In this case, hierarchyReaders is a ConcurrentHashMap,
     * which internally uses volatile load semantics for read operations.
     * This assures values written by one thread will be visible when read by
     * others.
     * http://en.wikipedia.org/wiki/Double-checked_locking
     */
    @Override
	public MemberReader getMemberReader(Hierarchy hierarchy) {
        MemberReader memberReader = hierarchyReaders.get(hierarchy);
        if (memberReader == null) {
            synchronized (this) {
                memberReader = hierarchyReaders.get(hierarchy);
                if (memberReader == null) {
                    memberReader =
                        ((RolapHierarchy) hierarchy).createMemberReader(role);
                    hierarchyReaders.put(hierarchy, memberReader);
                }
            }
        }
        return memberReader;
    }


    @Override
	public Member substitute(Member member) {
        final MemberReader memberReader =
            getMemberReader(member.getHierarchy());
        return memberReader.substitute((RolapMember) member);
    }

    @Override
	public void getMemberRange(
        Level level, Member startMember, Member endMember, List<Member> list)
    {
        getMemberReader(level.getHierarchy()).getMemberRange(
            (RolapLevel) level, (RolapMember) startMember,
            (RolapMember) endMember, Util.<RolapMember>cast(list));
    }

    @Override
	public int compareMembersHierarchically(Member m1, Member m2) {
        RolapMember member1 = (RolapMember) m1;
        RolapMember member2 = (RolapMember) m2;
        final RolapHierarchy hierarchy = member1.getHierarchy();
        Util.assertPrecondition(hierarchy == m2.getHierarchy());
        return getMemberReader(hierarchy).compare(member1, member2, true);
    }

    @Override
	public Member getMemberParent(Member member) {
        return getMemberReader(member.getHierarchy()).getMemberParent(
            (RolapMember) member);
    }

    @Override
	public int getMemberDepth(Member member) {
        final HierarchyAccess hierarchyAccess =
            role.getAccessDetails(member.getHierarchy());
        if (hierarchyAccess != null) {
            final int memberDepth = member.getLevel().getDepth();
            final int topLevelDepth = hierarchyAccess.getTopLevelDepth();
            return memberDepth - topLevelDepth;
        } else if (((RolapLevel) member.getLevel()).isParentChild()) {
            // For members of parent-child hierarchy, members in the same level
            // may have different depths.
            int depth = 0;
            for (Member m = member.getParentMember();
                m != null;
                m = m.getParentMember())
            {
                depth++;
            }
            return depth;
        } else {
            return member.getLevel().getDepth();
        }
    }


    @Override
	public List<Member> getMemberChildren(Member member) {
        return getMemberChildren(member, null);
    }

    @Override
	public List<Member> getMemberChildren(Member member, Evaluator context) {
        MemberChildrenConstraint constraint =
            sqlConstraintFactory.getMemberChildrenConstraint(context);
        List<RolapMember> memberList =
            internalGetMemberChildren(member, constraint);
        return Util.cast(memberList);
    }

    /**
     * Helper for getMemberChildren.
     *
     * @param member Member
     * @param constraint Constraint
     * @return List of children
     */
    private List<RolapMember> internalGetMemberChildren(
        Member member, MemberChildrenConstraint constraint)
    {
        List<RolapMember> children = new ArrayList<>();
        final Hierarchy hierarchy = member.getHierarchy();
        final MemberReader memberReader = getMemberReader(hierarchy);
        memberReader.getMemberChildren(
            (RolapMember) member, children, constraint);
        return children;
    }

    @Override
	public void getParentChildContributingChildren(
        Member dataMember,
        Hierarchy hierarchy,
        List<Member> list)
    {
        final List<RolapMember> rolapMemberList = Util.cast(list);
        list.add(dataMember);
        ((RolapHierarchy) hierarchy).getMemberReader().getMemberChildren(
            (RolapMember) dataMember, rolapMemberList);
    }

    @Override
	public int getChildrenCountFromCache(Member member) {
        final Hierarchy hierarchy = member.getHierarchy();
        final MemberReader memberReader = getMemberReader(hierarchy);
        if (memberReader instanceof
            RolapCubeHierarchy.RolapCubeHierarchyMemberReader)
        {
            List list =
                ((RolapCubeHierarchy.RolapCubeHierarchyMemberReader)
                 memberReader)
                    .getRolapCubeMemberCacheHelper()
                    .getChildrenFromCache((RolapMember) member, null);
            if (list == null) {
                return -1;
            }
            return list.size();
        }

        if (memberReader instanceof SmartMemberReader) {
            List list = ((SmartMemberReader) memberReader).getMemberCache()
                .getChildrenFromCache((RolapMember) member, null);
            if (list == null) {
                return -1;
            }
            return list.size();
        }
        if (!(memberReader instanceof MemberCache)) {
            return -1;
        }
        List list = ((MemberCache) memberReader)
            .getChildrenFromCache((RolapMember) member, null);
        if (list == null) {
            return -1;
        }
        return list.size();
    }

    /**
     * Returns number of members in a level,
     * if the information can be retrieved from cache.
     * Otherwise {@link Integer#MIN_VALUE}.
     *
     * @param level Level
     * @return number of members in level
     */
    private int getLevelCardinalityFromCache(Level level) {
        final Hierarchy hierarchy = level.getHierarchy();
        final MemberReader memberReader = getMemberReader(hierarchy);
        if (memberReader instanceof
            RolapCubeHierarchy.RolapCubeHierarchyMemberReader)
        {
            final MemberCacheHelper cache =
                ((RolapCubeHierarchy.RolapCubeHierarchyMemberReader)
                    memberReader).getRolapCubeMemberCacheHelper();
            if (cache == null) {
                return Integer.MIN_VALUE;
            }
            final List<RolapMember> list =
                cache.getLevelMembersFromCache(
                    (RolapLevel) level, null);
            if (list == null) {
                return Integer.MIN_VALUE;
            }
            return list.size();
        }

        if (memberReader instanceof SmartMemberReader) {
            List<RolapMember> list =
                ((SmartMemberReader) memberReader)
                    .getMemberCache()
                    .getLevelMembersFromCache(
                        (RolapLevel) level, null);
            if (list == null) {
                return Integer.MIN_VALUE;
            }
            return list.size();
        }

        if (memberReader instanceof MemberCache) {
            List<RolapMember> list =
                ((MemberCache) memberReader)
                    .getLevelMembersFromCache(
                        (RolapLevel) level, null);
            if (list == null) {
                return Integer.MIN_VALUE;
            }
            return list.size();
        }

        return Integer.MIN_VALUE;
    }

    @Override
	public int getLevelCardinality(
        Level level,
        boolean approximate,
        boolean materialize)
    {
        if (!this.role.canAccess(level)) {
            return 1;
        }

        int rowCount = Integer.MIN_VALUE;
        if (approximate) {
            // See if the schema has an approximation.
            rowCount = level.getApproxRowCount();
        }

        if (rowCount == Integer.MIN_VALUE) {
            // See if the precise row count is available in cache.
            rowCount = getLevelCardinalityFromCache(level);
        }

        if (rowCount == Integer.MIN_VALUE) {
            if (materialize) {
                // Either the approximate row count hasn't been set,
                // or they want the precise row count.
                final MemberReader memberReader =
                    getMemberReader(level.getHierarchy());
                rowCount =
                    memberReader.getLevelMemberCount((RolapLevel) level);
                // Cache it for future.
                ((RolapLevel) level).setApproxRowCount(rowCount);
            }
        }
        return rowCount;
    }

    @Override
	public List<Member> getMemberChildren(List<Member> members) {
        return getMemberChildren(members, null);
    }

    @Override
	public List<Member> getMemberChildren(
        List<Member> members,
        Evaluator context)
    {
        if (members.size() == 0) {
            return Collections.emptyList();
        } else {
            MemberChildrenConstraint constraint =
                sqlConstraintFactory.getMemberChildrenConstraint(context);
            final Hierarchy hierarchy = members.get(0).getHierarchy();
            final MemberReader memberReader = getMemberReader(hierarchy);
            final List<RolapMember> rolapMemberList = Util.cast(members);
            final List<RolapMember> children = new ArrayList<>();
            memberReader.getMemberChildren(
                rolapMemberList,
                children,
                constraint);
            return Util.cast(children);
        }
    }

    @Override
	public void getMemberAncestors(Member member, List<Member> ancestorList) {
        Member parentMember = getMemberParent(member);
        while (parentMember != null) {
            ancestorList.add(parentMember);
            parentMember = getMemberParent(parentMember);
        }
    }

    @Override
	public CatalogReader withoutAccessControl() {
        assert this.getClass() == RolapCatalogReader.class
            : new StringBuilder("Subclass ").append(getClass()).append(" must override").toString();
        if (role == catalog.getDefaultRole()) {
            return this;
        }
        return new RolapCatalogReader(context,catalog.getDefaultRole(), catalog);
    }

    @Override
	public OlapElement getElementChild(OlapElement parent, Segment name) {
        return getElementChild(parent, name, MatchType.EXACT);
    }

    @Override
	public OlapElement getElementChild(
            OlapElement parent, Segment name, MatchType matchType)
    {
        return parent.lookupChild(this, name, matchType);
    }

    @Override
	public final Member getMemberByUniqueName(
        List<Segment> uniqueNameParts,
        boolean failIfNotFound)
    {
        return getMemberByUniqueName(
            uniqueNameParts, failIfNotFound, MatchType.EXACT);
    }

    @Override
	public Member getMemberByUniqueName(
        List<Segment> uniqueNameParts,
        boolean failIfNotFound,
        MatchType matchType)
    {
        // In general, this schema reader doesn't have a cube, so we cannot
        // start looking up members.
        return null;
    }

    @Override
	public OlapElement lookupCompound(
        OlapElement parent,
        List<Segment> names,
        boolean failIfNotFound,
        DataType category)
    {
        return lookupCompound(
            parent, names, failIfNotFound, category, MatchType.EXACT);
    }

    @Override
	public final OlapElement lookupCompound(
        OlapElement parent,
        List<Segment> names,
        boolean failIfNotFound,
        DataType category,
        MatchType matchType)
    {
            return new NameResolverImpl().resolve(
                parent,
                Util.toOlap4j(names),
                failIfNotFound,
                category,
                matchType,
                getNamespaces());
    }

    public final OlapElement lookupCompoundInternal(
        OlapElement parent,
        List<Segment> names,
        boolean failIfNotFound,
        DataType category,
        MatchType matchType)
    {
        return Util.lookupCompound(
            this, parent, names, failIfNotFound, category, matchType);
    }

    @Override
	public List<NameResolver.Namespace> getNamespaces() {
        return Collections.<NameResolver.Namespace>singletonList(this);
    }

    @Override
	public OlapElement lookupChild(
        OlapElement parent,
        IdentifierSegment segment)
    {
        return lookupChild(parent, segment, MatchType.EXACT);
    }

    @Override
	public OlapElement lookupChild(
        OlapElement parent,
        IdentifierSegment segment,
        MatchType matchType)
    {
        OlapElement element = getElementChild(
            parent,
            Util.convert(segment),
            matchType);
        if (element != null) {
            return element;
        }
        if (parent instanceof Cube) {
            // Named sets defined at the schema level do not, of course, belong
            // to a cube. But if parent is a cube, this indicates that the name
            // has not been qualified.
            element = catalog.getNamedSet(segment);
        }
        return element;
    }

    @Override
	public Member lookupMemberChildByName(
            Member parent, Segment childName, MatchType matchType)
    {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                new StringBuilder("looking for child \"").append(childName).append("\" of ").append(parent).toString());
        }
        assert !(parent instanceof RolapHierarchy.LimitedRollupMember);
        try {
            MemberChildrenConstraint constraint;
            if (childName instanceof NameSegment
                && matchType.isExact())
            {
                constraint = sqlConstraintFactory.getChildByNameConstraint(
                    (RolapMember) parent, (NameSegment) childName,
                    context.getConfigValue(ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD_DEFAULT_VALUE, Integer.class));
            } else {
                constraint =
                    sqlConstraintFactory.getMemberChildrenConstraint(null);
            }
            List<RolapMember> children =
                internalGetMemberChildren(parent, constraint);
            if (children.size() > 0) {
                return
                    RolapUtil.findBestMemberMatch(
                        children,
                        (RolapMember) parent,
                        children.get(0).getLevel(),
                        childName,
                        matchType);
            }
        } catch (NumberFormatException e) {
            // this was thrown in SqlQuery#quote(boolean numeric, Object
            // value). This happens when Mondrian searches for unqualified Olap
            // Elements like [Month], because it tries to look up a member with
            // that name in all dimensions. Then it generates for example
            // "select .. from time where year = Month" which will result in a
            // NFE because "Month" can not be parsed as a number. The real bug
            // is probably, that Mondrian looks at members at all.
            //
            // @see RolapCube#lookupChild()
            LOGGER.debug(
                "NumberFormatException in lookupMemberChildByName for parent = \"{}\", childName=\"{}\", exception: {}",
                parent, childName, e.getMessage());
        }
        return null;
    }

    @Override
	public List<Member> lookupMemberChildrenByNames(
        Member parent, List<NameSegment> childNames, MatchType matchType)
    {
        MemberChildrenConstraint constraint = sqlConstraintFactory
            .getChildrenByNamesConstraint(
                (RolapMember) parent, childNames,
                context
                .getConfigValue(ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD_DEFAULT_VALUE, Integer.class));
        List<RolapMember> children =
            internalGetMemberChildren(parent, constraint);
        List<Member> childMembers = new ArrayList<>();
        childMembers.addAll(children);
        return childMembers;
    }

    @Override
	public Member getCalculatedMember(List<Segment> nameParts) {
        // There are no calculated members defined against a schema.
        return null;
    }

    @Override
	public NamedSet getNamedSet(List<Segment> nameParts) {
        if (nameParts.size() != 1) {
            return null;
        }
        if (!(nameParts.get(0) instanceof NameSegment)) {
            return null;
        }
        final String name = ((NameSegment) nameParts.get(0)).getName();
        return catalog.getNamedSet(name);
    }

    @Override
	public Member getLeadMember(Member member, int n) {
        final MemberReader memberReader =
            getMemberReader(member.getHierarchy());
        return memberReader.getLeadMember((RolapMember) member, n);
    }

    @Override
	public List<Member> getLevelMembers(Level level, boolean includeCalculated)
    {
        return getLevelMembers(level, includeCalculated, null);
    }

    @Override
	public List<Member> getLevelMembers(Level level, boolean includeCalculated, Evaluator context)
    {
        List<Member> members = getLevelMembers(level, context);
        if (!includeCalculated) {
            members = SqlConstraintUtils.removeCalculatedMembers(members);
        }
        return members;
    }

    @Override
	public List<Member> getLevelMembers(Level level, Evaluator context) {
        TupleConstraint constraint =
            sqlConstraintFactory.getLevelMembersConstraint(
                context,
                new Level[] {level});
        final MemberReader memberReader =
            getMemberReader(level.getHierarchy());
        List<RolapMember> membersInLevel =
            memberReader.getMembersInLevel(
                (RolapLevel) level, constraint);
        return Util.cast(membersInLevel);
    }

    @Override
	public List<Dimension> getCubeDimensions(Cube cube) {
        assert cube != null;
        final List<Dimension> dimensions = new ArrayList<>();
        for (Dimension dimension : cube.getDimensions()) {
            switch (role.getAccess(dimension)) {
            case NONE:
                continue;
            default:
                dimensions.add(dimension);
                break;
            }
        }
        return dimensions;
    }

    @Override
	public List<Hierarchy> getDimensionHierarchies(Dimension dimension) {
        assert dimension != null;
        final List<Hierarchy> hierarchies = new ArrayList<>();
        for (Hierarchy hierarchy : dimension.getHierarchies()) {
            switch (role.getAccess(hierarchy)) {
            case NONE:
                continue;
            default:
                hierarchies.add(hierarchy);
                break;
            }
        }
        return hierarchies;
    }

    @Override
	public List<Level> getHierarchyLevels(Hierarchy hierarchy) {
        assert hierarchy != null;
        final HierarchyAccess hierarchyAccess =
            role.getAccessDetails(hierarchy);
        final List<Level> levels = (List<Level>) hierarchy.getLevels();
        if (hierarchyAccess == null) {
            return  levels;
        }
        Level topLevel = levels.get(hierarchyAccess.getTopLevelDepth());
        Level bottomLevel = levels.get(hierarchyAccess.getBottomLevelDepth());
        List<Level> restrictedLevels =
            levels.subList(
                topLevel.getDepth(), bottomLevel.getDepth() + 1);
        assert restrictedLevels.size() >= 1 : "postcondition";
        return restrictedLevels;
    }

    @Override
	public Member getHierarchyDefaultMember(Hierarchy hierarchy) {
        assert hierarchy != null;
        // If the whole hierarchy is inaccessible, return the intrinsic default
        // member. This is important to construct a evaluator.
        if (role.getAccess(hierarchy) == AccessHierarchy.NONE) {
            return hierarchy.getDefaultMember();
        }
        return getMemberReader(hierarchy).getDefaultMember();
    }

    @Override
	public boolean isDrillable(Member member) {
        final RolapLevel level = (RolapLevel) member.getLevel();
        if (level.getParentExp() != null) {
            // This is a parent-child level, so its children, if any, come from
            // the same level.
            //
            // todo: More efficient implementation
            return getMemberChildren(member).size() > 0;
        } else {
            // This is a regular level. It has children iff there is a lower
            // level.
            final Level childLevel = level.getChildLevel();
            return (childLevel != null)
                && (role.getAccess(childLevel) != AccessMember.NONE);
        }
    }

    @Override
	public boolean isVisible(Member member) {
        return !member.isHidden() && role.canAccess(member);
    }

    @Override
	public List<Cube> getCubes() {
     return   catalog.getCubes().stream().filter(role::canAccess).toList();
    }

    @Override
    public List<? extends DatabaseSchema> getDatabaseSchemas() {
     org.eclipse.daanse.olap.api.element.Catalog catalog = this.getCatalog();
     return catalog.getDatabaseSchemas().stream().filter(d -> role.canAccess(d, catalog)).map(d -> getDatabaseSchema(d, role.getAccess(d, catalog)) ).toList();
    }

    private DatabaseSchema getDatabaseSchema(DatabaseSchema ds, AccessDatabaseSchema access) {
        if (AccessDatabaseSchema.ALL.equals(access)) {
            return ds;
        }
        RolapDatabaseSchema rolapDbSchema = new RolapDatabaseSchema();
        rolapDbSchema.setName(ds.getName());
        List<DatabaseTable> rolapDbTables = new ArrayList<>();
        for (DatabaseTable t : ds.getDbTables()) {
            AccessDatabaseTable accessDatabaseTable  = role.getAccess(t, access);
            if (AccessDatabaseTable.ALL.equals(accessDatabaseTable)) {
                rolapDbTables.add(t);
            }
            if (AccessDatabaseTable.CUSTOM.equals(accessDatabaseTable)) {
                RolapDatabaseTable rolapDbTable = new RolapDatabaseTable();
                rolapDbTable.setName(t.getName());
                rolapDbTable.setDescription(t.getDescription());
                List<DatabaseColumn> rolapDbColumns = new ArrayList<>();
                rolapDbTable.setDbColumns(rolapDbColumns);
                rolapDbTables.add(rolapDbTable);
                for (DatabaseColumn c : t.getDbColumns()) {
                    AccessDatabaseColumn accessDatabaseColumn  = role.getAccess(c, accessDatabaseTable);
                    if (AccessDatabaseColumn.ALL.equals(accessDatabaseColumn)) {
                        rolapDbColumns.add(c);
                    }
                }
            }
        }
        rolapDbSchema.setDbTables(rolapDbTables);
        return rolapDbSchema;
    }

    @Override
	public List<Member> getCalculatedMembers(Hierarchy hierarchy) {
        return Collections.emptyList();
    }

    @Override
	public List<Member> getCalculatedMembers(Level level) {
        return Collections.emptyList();
    }

    @Override
	public List<Member> getCalculatedMembers() {
        return Collections.emptyList();
    }

    @Override
	public NativeEvaluator getNativeSetEvaluator(
        FunctionDefinition fun, Expression[] args, Evaluator evaluator, Calc calc)
    {
        RolapEvaluator revaluator = (RolapEvaluator)
ElevatorSimplifyer.simplifyEvaluator(calc, evaluator);
        if (evaluator.nativeEnabled()) {
            return catalog.getNativeRegistry().createEvaluator(
                revaluator, fun, args, context.getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER, ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class));
        }
        return null;
    }

    @Override
	public Parameter getParameter(String name) {
        // Scan through schema parameters.
        for (Parameter parameter : catalog.getParameters()) {
            if (Util.equalName(parameter.getName(), name)) {
                return parameter;
            }
        }

        // Scan through mondrian properties.
        List<SystemProperty> propertyList =
            SystemWideProperties.instance().getPropertyList();
        for (SystemProperty property : propertyList) {
            if (property.getPath().equals(name)) {
                return new SystemPropertyParameter(name, false);
            }
        }

        return null;
    }

    @Override
	public DataSource getDataSource() {
        return catalog.getInternalConnection().getDataSource();
    }

    @Override
	public RolapCatalog getCatalog() {
        return catalog;
    }

    @Override
	public CatalogReader withLocus() {
        return Util.executionCatalogReader(
            catalog.getInternalConnection(),
            this);
    }

    @Override
	public Map<? extends Member, AccessMember> getMemberChildrenWithDetails(
        Member member,
        Evaluator evaluator)
    {
        MemberChildrenConstraint constraint =
            sqlConstraintFactory.getMemberChildrenConstraint(evaluator);
        final Hierarchy hierarchy = member.getHierarchy();
        final MemberReader memberReader = getMemberReader(hierarchy);
        final ArrayList<RolapMember> memberChildren =
            new ArrayList<>();

        return memberReader.getMemberChildren(
            (RolapMember) member,
            memberChildren,
            constraint);
    }

    /**
     * Implementation of {@link Parameter} which is sourced from mondrian
     * properties (see {@link SystemWideProperties}.
     *
     * The name of the property is the same as the key into the
     * {@link java.util.Properties} object; for example "mondrian.trace.level".
     */
    private static class SystemPropertyParameter
        extends ParameterImpl
    {
        /**
         * true if source is a system property;
         * false if source is a mondrian property.
         */
        private final boolean system;
        /**
         * Definition of mondrian property, or null if system property.
         */
        private final SystemProperty propertyDefinition;

        public SystemPropertyParameter(String name, boolean system) {
            super(
                name,
                NullLiteralImpl.nullValue,
                new StringBuilder("System property '").append(name).append("'").toString(),
                StringType.INSTANCE);
            this.system = system;
            this.propertyDefinition =
                system
                ? null
                : SystemWideProperties.instance().getPropertyDefinition(name);
        }

        @Override
		public Scope getScope() {
            return Scope.System;
        }

        @Override
		public boolean isModifiable() {
            return false;
        }

        @Override
		public Calc compile(ExpressionCompiler compiler) {
            return new AbstractProfilingNestedUnknownCalc(getType()) {
            	//"SystemPropertyCalc"
                @Override
				public Calc[] getChildCalcs() {
                    return new Calc[0];
                }

                @Override
				public Object evaluateInternal(Evaluator evaluator) {
                    if (system) {
                        final String name =
                            SystemPropertyParameter.this.getName();
                        return System.getProperty(name);
                    } else {
                        return propertyDefinition.stringValue();
                    }
                }
            };
        }
    }

    @Override
    public Context getContext() {
        return context;
    }
}
