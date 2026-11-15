/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.element;

import org.eclipse.daanse.olap.cache.CachePolicy;
import static java.util.Collections.EMPTY_LIST;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.left;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.right;

import java.sql.SQLException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.OptionalInt;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.SqlStatement;
import org.eclipse.daanse.rolap.common.TupleReader.MemberBuilder;
import org.eclipse.daanse.rolap.common.Utils;
import org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint;
import org.eclipse.daanse.rolap.common.member.MemberCache;
import org.eclipse.daanse.rolap.common.member.MemberCacheImpl;
import org.eclipse.daanse.rolap.common.member.MemberLoadRegistry;
import org.eclipse.daanse.rolap.common.member.MemberReader;
import org.eclipse.daanse.rolap.common.member.NoCacheMemberReader;
import org.eclipse.daanse.rolap.common.member.CachingMemberReader;
import org.eclipse.daanse.rolap.common.member.SqlMemberSource;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.common.star.HierarchyUsage;
import org.eclipse.daanse.rolap.common.util.RelationUtil;

/**
 * Hierarchy that is associated with a specific Cube.
 *
 * @author Will Gorman, 19 October 2007
 */
public class RolapCubeHierarchy extends RolapHierarchy {

    // Assigned in the constructor rather than here: the catalog - and through it
    // the Context that carries the setting - is only reachable once the super
    // constructor has run.
    private final boolean cachingEnabled;

    /** Hierarchy/cube daanse:cache.level-precache-threshold tags; empty = global config. */
    private final OptionalInt levelPreCacheThresholdOverride;

    public OptionalInt levelPreCacheThresholdOverride() {
        return levelPreCacheThresholdOverride;
    }
    private final RolapCubeDimension cubeDimension;
    private final RolapHierarchy rolapHierarchy;
    private final RolapCubeLevel currentNullLevel;
    private RolapCubeMember currentNullMember;
    private RolapCubeMember currentAllMember;
    private final org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource currentRelation;
    private final RolapCubeHierarchyMemberReader reader;
    private HierarchyUsage usage;
    private final Map<String, String> aliases = new HashMap<>();
    private RolapCubeMember currentDefaultMember;
    private final int ordinal;

    /**
     * True if the hierarchy is degenerate - has no dimension table of its own,
     * just drives from the cube's fact table.
     */
    protected final boolean usingCubeFact;

    /**
     * Length of prefix to be removed when translating member unique names, or
     * 0 if no translation is necessary.
     */
    private final int removePrefixLength;

    // redundant copy of {@link #levels} with tigher type
    
    /**
     * Creates a RolapCubeHierarchy.
     *
     * @param cubeDimension Dimension
     * @param cubeDim XML dimension element
     * @param rolapHierarchy Wrapped hierarchy
     * @param subName Name of hierarchy within dimension
     * @param ordinal Ordinal of hierarchy within cube
     */
    public RolapCubeHierarchy(
        RolapCubeDimension cubeDimension,
        org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector cubeDim,
        RolapHierarchy rolapHierarchy,
        String subName,
        int ordinal)
    {
      this(
          cubeDimension,
          cubeDim,
          rolapHierarchy,
          subName,
          ordinal, null);
    }

    /**
     * Creates a RolapCubeHierarchy.
     *
     * @param cubeDimension Dimension
     * @param cubeDim XML dimension element
     * @param rolapHierarchy Wrapped hierarchy
     * @param subName Name of hierarchy within dimension
     * @param ordinal Ordinal of hierarchy within cube
     * @param factCube Optional - specified for virtual cube dimension
     */
    public RolapCubeHierarchy(
        RolapCubeDimension cubeDimension,
        org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector cubeDim,
        RolapHierarchy rolapHierarchy,
        String subName,
        int ordinal,
        RolapCube factCube)
    {
        super(
            cubeDimension,
            subName,
            applyPrefix(cubeDim, rolapHierarchy.getCaption()),
            rolapHierarchy.isVisible(),
            applyPrefix(cubeDim, rolapHierarchy.getDescription()),
            rolapHierarchy.getDisplayFolder(),
            rolapHierarchy.hasAll(),
            null,
            rolapHierarchy.getMetaData());
        this.ordinal = ordinal;
        final boolean cubeIsVirtual = cubeDimension.getCube() instanceof RolapVirtualCube;
        if (!cubeIsVirtual) {
            this.usage =
                new HierarchyUsage(
                    cubeDimension.getCube(), rolapHierarchy, cubeDim);
        }

        this.rolapHierarchy = rolapHierarchy;
        this.cubeDimension = cubeDimension;
        // INVARIANT: this predicate must stay in agreement with
        // CacheControlImpl.execute's edit guard and
        // RolapCatalog.applySharedMemberCachePolicy - the three sites agree
        // today only because a null hierarchy mapping (which would make
        // usesSharedHierarchy diverge) is unreachable for dimension hierarchies
        this.cachingEnabled = org.eclipse.daanse.olap.cache.CachePolicy
            .membersFor(rolapHierarchy.getMetaData(), cubeDimension.getCube().getCachePolicy());
        this.levelPreCacheThresholdOverride = org.eclipse.daanse.olap.cache.CachePolicy
            .levelPreCacheThresholdFor(rolapHierarchy.getMetaData(), cubeDimension.getCube().getCachePolicy());
        this.hierarchyMapping = rolapHierarchy.getHierarchyMapping();
        // this relation should equal the name of the new dimension table
        // The null member belongs to a level with very similar properties to
        // the 'all' level.
        this.currentNullLevel = new RolapCubeLevel(nullLevel, this);

        if (factCube == null) {
          factCube = cubeDimension.getCube();
        }

        usingCubeFact =
            (factCube == null
              || factCube.getFact() == null
              || Utils.equalsQuery(factCube.getFact(),
                  rolapHierarchy.getRelation()));

        // re-alias names if necessary
        if (!cubeIsVirtual && !usingCubeFact) {
            // join expressions are columns only
            assert (usage.getJoinExp() instanceof org.eclipse.daanse.rolap.element.RolapColumn column);
            currentRelation =
                this.cubeDimension.getCube().getStar().getUniqueRelation(
                    rolapHierarchy.getRelation(),
                    usage.getForeignKey().getName(),
                    ((org.eclipse.daanse.rolap.element.RolapColumn)usage.getJoinExp()).getName(),
                    RelationUtil.getAlias(usage.getJoinTable()));
        } else {
            currentRelation = rolapHierarchy.getRelation();
        }
        extractNewAliases(rolapHierarchy.getRelation(), currentRelation);
        this.relation = currentRelation;
        this.levels = new ArrayList<>();
        boolean first = true;
        for (Level l : rolapHierarchy.getLevels()) {
            this.levels.add(
                new RolapCubeLevel(
                    (RolapLevel) l, this));
            if (first && rolapHierarchy.getAllMember() != null) {
                RolapCubeLevel allLevel;
                if (hasAll()) {
                    allLevel = (RolapCubeLevel) this.levels.getFirst();
                } else {
                    // create an all level if one doesn't normally
                    // exist in the hierarchy
                    allLevel =
                        new RolapCubeLevel(
                            rolapHierarchy.getAllMember().getLevel(),
                            this);
                    allLevel.init(cubeDimension.xmlDimension);
                }

                this.currentAllMember =
                    new RolapAllCubeMember(
                        rolapHierarchy.getAllMember(),
                        allLevel);
            }
            first = false;
        }

        // Compute whether the unique names of members of this hierarchy are
        // different from members of the underlying hierarchy. If so, compute
        // the length of the prefix to be removed before this hierarchy's unique
        // name is added. For example, if this.uniqueName is "[Ship Time]" and
        // rolapHierarchy.uniqueName is "[Time]", remove prefixLength will be
        // length("[Ship Time]") = 11.
        if (uniqueName.equals(rolapHierarchy.getUniqueName())) {
            this.removePrefixLength = 0;
        } else {
            this.removePrefixLength = rolapHierarchy.getUniqueName().length();
        }

        if ( !cachingEnabled) {
            this.reader = new NoCacheRolapCubeHierarchyMemberReader();
        } else {
            this.reader = new CacheRolapCubeHierarchyMemberReader();
        }
    }

    /**
     * Applies a prefix to a caption or description of a hierarchy in a shared
     * dimension. Ensures that if a dimension is used more than once in the same
     * cube then the hierarchies are distinguishable.
     *
     * For example, if the [Time] dimension is imported as [Order Time] and
     * [Ship Time], then the [Time].[Weekly] hierarchy would have caption
     * "Order Time.Weekly caption" and description "Order Time.Weekly
     * description".
     *
     * If the dimension usage has a caption, it overrides.
     *
     * If the dimension usage has a null name, or the name is the same
     * as the dimension, and no caption, then no prefix is applied.
     *
     * @param cubeDim Cube dimension (maybe a usage of a shared dimension)
     * @param caption Caption or description
     * @return Caption or description, possibly prefixed by dimension role name
     */
    private static String applyPrefix(
    	org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector cubeDim,
        String caption)
    {
        if (caption == null) {
            return null;
        }
        return caption;
    }


    @Override
	public String getAllMemberName() {
        return rolapHierarchy.getAllMemberName();
    }

    @Override
	public String getSharedHierarchyName() {
        return rolapHierarchy.getSharedHierarchyName();
    }

    @Override
	public String getAllLevelName() {
        return rolapHierarchy.getAllLevelName();
    }

    public boolean isUsingCubeFact() {
        return usingCubeFact;
    }

    public String lookupAlias(String origTable) {
        return aliases.get(origTable);
    }

    public String lookupTableNameByAlias(String origTable) {
        if (!aliases.isEmpty()) {
            Optional<Map.Entry<String, String>> op = aliases.entrySet().stream().filter(e -> e.getValue().equals(origTable)).findAny();
            if (op.isPresent()) {
                return op.get().getKey();
            }
        }
        return origTable;
    }

    public final RolapHierarchy getRolapHierarchy() {
        return rolapHierarchy;
    }

    @Override
	public final int getOrdinalInCube() {
        return ordinal;
    }

    /**
     * Populates the alias map for the old and new relations.
     *
     * This method may be simplified when we obsolete
     * {@link org.eclipse.daanse.rolap.common.star.HierarchyUsage}.
     *
     * @param oldrel Original relation, as defined in the schema
     * @param newrel New star relation, generated by RolapStar, canonical, and
     * shared between all cubes with similar structure
     */
    protected void extractNewAliases(
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource oldrel,
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource newrel)
    {
        if (oldrel != null && newrel != null) {
            if (oldrel instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource oldjoin
                && newrel instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource newjoin) {
                extractNewAliases(left(oldjoin), left(newjoin));
                extractNewAliases(right(oldjoin), right(newjoin));
            } else if (!(oldrel instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource)
                && !(newrel instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource)) {
                aliases.put(
                    RelationUtil.getAlias(oldrel),
                    RelationUtil.getAlias(newrel));
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Override
	public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RolapCubeHierarchy that)) {
            return false;
        }

        return cubeDimension.equalsOlapElement(that.cubeDimension)
            && getUniqueName().equals(that.getUniqueName());
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
	protected int computeHashCode() {
        return Util.hash(super.computeHashCode(), this.cubeDimension.cube);
    }

    @Override
	public Member createMember(
        Member parent,
        Level level,
        String name,
        Formula formula)
    {
        RolapLevel rolapLevel = ((RolapCubeLevel)level).getRolapLevel();
        if (formula == null) {
            RolapMember rolapParent = null;
            if (parent != null) {
                rolapParent = ((RolapCubeMember)parent).getRolapMember();
            }
            RolapMember member =
                new RolapMemberBase(rolapParent, rolapLevel, name);
            return new RolapCubeMember(
                (RolapCubeMember) parent, member,
                (RolapCubeLevel) level);
        } else if (level.getDimension().isMeasures()) {
            RolapCalculatedMeasure member =
                new RolapCalculatedMeasure(
                    (RolapMember) parent, rolapLevel, name, formula);
            return new RolapCubeMember(
                (RolapCubeMember) parent, member,
                (RolapCubeLevel) level);
        } else {
            RolapCalculatedMember member =
                new RolapCalculatedMember(
                    (RolapMember) parent, rolapLevel, name, formula);
            return new RolapCubeMember(
                (RolapCubeMember) parent, member,
                (RolapCubeLevel) level);
        }
    }


    @Override
	public boolean tableExists(String tableName) {
        return rolapHierarchy.tableExists(tableName);
    }

    /**
     * The currentRelation object is derived from the shared relation object
     * it is generated via the RolapStar object, and contains unique aliases
     * for it's particular join path
     *
     * @return rolap cube hierarchy relation
     */
    @Override
	public org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource getRelation() {
        return currentRelation;
    }

    // override with stricter return type; make final, important for performance
    @Override
	public final RolapCubeMember getDefaultMember() {
        if (currentDefaultMember == null) {
            currentDefaultMember =
                bootstrapLookup(
                    (RolapMember) rolapHierarchy.getDefaultMember());
        }
        return currentDefaultMember;
    }

    /**
     * Looks up a {@link RolapCubeMember} corresponding to a {@link RolapMember}
     * of the underlying hierarchy. Safe to be called while the hierarchy is
     * initializing.
     *
     * @param rolapMember Member of underlying hierarchy
     * @return Member of this hierarchy
     */
    private RolapCubeMember bootstrapLookup(RolapMember rolapMember) {
        RolapCubeMember parent = getParent(rolapMember);
        RolapCubeLevel level = (RolapCubeLevel) levels.get(rolapMember.getLevel().getDepth());
        return reader.lookupCubeMember(parent, rolapMember, level);
    }

    private RolapCubeMember getParent(RolapMember rolapMember) {
        if (rolapMember.getParentMember() == null) {
            return null;
        }
            return rolapMember.getParentMember().isAll()
            ? currentAllMember
            : bootstrapLookup(rolapMember.getParentMember());
    }

    @Override
	public Member getNullMember() {
        // use lazy initialization to get around bootstrap issues
        if (currentNullMember == null) {
            currentNullMember =
                new RolapCubeMember(
                    null,
                    (RolapMember) rolapHierarchy.getNullMember(),
                    currentNullLevel);
        }
        return currentNullMember;
    }

    /**
     * Returns the 'all' member.
     */
    @Override
	public RolapCubeMember getAllMember() {
        return currentAllMember;
    }

    @Override
	public void setMemberReader(MemberReader memberReader) {
        rolapHierarchy.setMemberReader(memberReader);
    }

    @Override
	public MemberReader getMemberReader() {
        return reader;
    }

    @Override
	public void setDefaultMember(Member defaultMeasure) {
        // refactor this!
        rolapHierarchy.setDefaultMember(defaultMeasure);

        RolapCubeLevel level =
            new RolapCubeLevel(
                (RolapLevel)rolapHierarchy.getDefaultMember().getLevel(),
                this);
        currentDefaultMember =
            new RolapCubeMember(
                null,
                (RolapMember) rolapHierarchy.getDefaultMember(),
                level);
    }

    @Override
	void init(org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector xmlDimension) {
        // first init shared hierarchy
        rolapHierarchy.init(xmlDimension);
        // second init cube hierarchy
        super.init(xmlDimension);
    }

    /**
     * Converts the unique name of a member of the underlying hierarchy to
     * the appropriate name for this hierarchy.
     *
     * For example, if the shared hierarchy is [Time].[Quarterly] and the
     * hierarchy usage is [Ship Time].[Quarterly], then [Time].[1997].[Q1] would
     * be translated to [Ship Time].[Quarerly].[1997].[Q1].
     *
     * @param memberUniqueName Unique name of member from underlying hierarchy
     * @return Translated unique name
     */
    public final String convertMemberName(String memberUniqueName) {
        if (removePrefixLength > 0
            && !memberUniqueName.startsWith(uniqueName))
        {
            return uniqueName + memberUniqueName.substring(removePrefixLength);
        }
        return memberUniqueName;
    }

    public final RolapCube getCube() {
        return cubeDimension.cube;
    }

    private static RolapCubeMember createAncestorMembers(
        RolapCubeHierarchyMemberReader memberReader,
        RolapCubeLevel level,
        RolapMember member)
    {
        if (member == null) {
            return null;
        }
        RolapCubeMember parent = null;
        if (member.getParentMember() != null) {
            parent =
                createAncestorMembers(
                    memberReader,
                    level.getParentLevel(),
                    member.getParentMember());
        }
        return memberReader.lookupCubeMember(parent, member, level);
    }

    /**
     * The cube-side member reader, and the one place the THREE member
     * caches' roles are laid out:
     *
     * <ul>
     * <li>{@code CachingMemberReader.memberCache} (inherited) - the
     * shared hierarchy's cache: RAW {@code RolapMember}s only, never
     * cube wrappers. The cube reader's children loads write the raw
     * members HERE (deliberately, via the base class's routing) when a
     * fact-table join resolves them.</li>
     * <li>{@code rolapCubeMemberCache} - the cube-level cache of
     * {@code RolapCubeMember} WRAPPERS. NOT redundant with the above:
     * wrapper and raw member must never share a list slot - a wrapper in
     * the shared cache would leak one cube's ordinals into another, a
     * raw member in the cube cache would lose them
     * ({@code childrenCache()} switches between the two).</li>
     * <li>{@code cubeSource} serves {@code getMemberBuilder()} for the
     * native path and uses both.</li>
     * </ul>
     */
    public static interface RolapCubeHierarchyMemberReader
        extends MemberReader
    {
        public RolapCubeMember lookupCubeMember(
            final RolapCubeMember parent,
            final RolapMember member,
            final RolapCubeLevel level);

        public MemberCache getRolapCubeMemberCache();
    }

    // the member-cache role map lives as javadoc on
    // RolapCubeHierarchyMemberReader above


    /**
     *  member reader wrapper - uses existing member reader,
     *  but wraps and caches all intermediate members.
     *
     *  Concurrency comes from the base class: lock-free cache reads,
     *  per-key single-flight loads through the MemberLoadRegistry, and
     *  synchronized flush/remove - there is no memberCache monitor.
      */
    public class CacheRolapCubeHierarchyMemberReader
        extends CachingMemberReader
        implements RolapCubeHierarchyMemberReader
    {
        /**
         * cubeSource is passed as our member builder
         */
        protected final RolapCubeSqlMemberSource cubeSource;

        /**
         * Caches the RolapCubeMember wrappers. The inherited memberCache
         * holds the underlying hierarchy's RolapMembers, created when a
         * lookup involves the cube's fact table.
         */
        // public for the testkit cache probes; final since round 5
        public final MemberCacheImpl rolapCubeMemberCache;

        public CacheRolapCubeHierarchyMemberReader() {
            super(new SqlMemberSource(RolapCubeHierarchy.this));
            rolapCubeMemberCache =
                new MemberCacheImpl(RolapCubeHierarchy.this);
            rolapCubeMemberCache.setLoadRegistry(loadRegistry());

            cubeSource =
                new RolapCubeSqlMemberSource(
                    this,
                    RolapCubeHierarchy.this,
                    rolapCubeMemberCache);

            cubeSource.setCache(getMemberCache());
        }

        @Override
		public MemberBuilder getMemberBuilder() {
            return this.cubeSource;
        }

        @Override
		public MemberCache getRolapCubeMemberCache() {
            return rolapCubeMemberCache;
        }

        /** Flushes the cube-level member cache along with the inherited
         * caches - callers no longer need to flush both by hand. */
        @Override
		public void flushCache() {
            super.flushCache();
            rolapCubeMemberCache.flushCache();
        }

        @Override
		public List<RolapMember> getRootMembers() {
            return memoizedRoots(
                () -> getMembersInLevel((RolapLevel) getLevels().getFirst()));
        }

        @Override
		protected void readMemberChildren(
            List<RolapMember> parentMembers,
            List<RolapMember> children,
            MemberChildrenConstraint constraint)
        {
            List<RolapMember> rolapChildren = new ArrayList<>();
            List<RolapMember> rolapParents = new ArrayList<>();
            Map<String, RolapCubeMember> lookup =
                new HashMap<>();

            // extract RolapMembers from their RolapCubeMember objects
            // populate lookup for reconnecting parents and children
            for (RolapMember member : parentMembers) {
                if (member instanceof VisualTotalMember) {
                    continue;
                }
                if (member instanceof RolapCubeMember cubeMember) {
                    final RolapMember rolapMember = cubeMember.getRolapMember();
                    lookup.put(rolapMember.getUniqueName(), cubeMember);
                    rolapParents.add(rolapMember);
                }
            }

            // get member children from shared member reader if possible,
            // if not get them from our own source
            boolean joinReq =
                (constraint instanceof SqlContextConstraint);
            if (joinReq) {
                super.readMemberChildren(
                    parentMembers, rolapChildren, constraint);
            } else {
                rolapHierarchy.getMemberReader().getMemberChildren(
                    rolapParents, rolapChildren, constraint);
            }

            // now lookup or create RolapCubeMember
            for (RolapMember currMember : rolapChildren) {
                final RolapMember currParent = currMember.getParentMember();
                RolapCubeMember parent = currParent == null ? null
                    : lookup.get(currParent.getUniqueName());
                if (parent != null) {
                RolapCubeLevel level =
                    parent.getLevel().getChildLevel();
                if (level == null) {
                    // most likely a parent child hierarchy
                    level = parent.getLevel();
                }
                RolapCubeMember newmember =
                    lookupCubeMember(
                        parent, currMember, level);
                children.add(newmember);
                }
            }
                        // Put them in a temporary hash table first. Register them later,
            // when we know their size (hence their 'cost' to the cache pool).
            Map<RolapMember, List<RolapMember>> tempMap =
                new HashMap<>();
            for (RolapMember member1 : parentMembers) {
                tempMap.put(member1, Collections.<RolapMember>emptyList());
            }

            // note that this stores RolapCubeMembers in our cache,
            // which also stores RolapMembers.

            for (RolapMember child : children) {
            // todo: We could optimize here. If members.length is small, it's
            // more efficient to drive from members, rather than hashing
            // children.length times. We could also exploit the fact that the
            // result is sorted by ordinal and therefore, unless the "members"
            // contains members from different levels, children of the same
            // member will be contiguous.
                assert child != null : "child";
                final RolapMember parentMember = child.getParentMember();
                List<RolapMember> cacheList = tempMap.get(parentMember);
                if (cacheList == null) {
                    // The list is null if, due to dropped constraints, we now
                    // have a children list of a member we didn't explicitly
                    // ask for it. Adding it to the cache would be viable, but
                    // let's ignore it.
                    continue;
                } else if (cacheList == EMPTY_LIST) {
                    cacheList = new ArrayList<>();
                    tempMap.put(parentMember, cacheList);
                }
                cacheList.add(child);
            }

            for (Map.Entry<RolapMember, List<RolapMember>> entry
                : tempMap.entrySet())
            {
                final RolapMember member = entry.getKey();
                if (rolapCubeMemberCache.getChildrenFromCache(
                        member, constraint) == null)
                {
                    // never publish the immutable empty sentinel: cached
                    // children lists stay mutable for in-place removal
                    final List<RolapMember> cacheList = entry.getValue();
                    rolapCubeMemberCache.putChildren(
                        member, constraint,
                        cacheList == EMPTY_LIST
                            ? new ArrayList<>() : cacheList);
                }
            }
        }

        @Override
        protected MemberCacheImpl childrenCache() {
            return rolapCubeMemberCache;
        }


        @Override
		public List<RolapMember> getMembersInLevel(
            RolapLevel level,
            TupleConstraint constraint)
        {
            List<RolapMember> members =
                rolapCubeMemberCache.getLevelMembersFromCache(
                    level, constraint);
            if (members != null) {
                return members;
            }
            Object constraintKey = constraint.getCacheKey();
            if (constraintKey == null || loadRegistry().inLoad()) {
                return readMembersInLevel(level, constraint);
            }
            MemberLoadRegistry.Claim claim = loadRegistry().claim(
                new MemberLoadRegistry.LevelKey(level, constraintKey));
            if (!claim.loader()) {
                return loadRegistry().await(claim);
            }
            loadRegistry().enterLoad();
            try {
                members = rolapCubeMemberCache.getLevelMembersFromCache(
                    level, constraint);
                if (members == null) {
                    members = readMembersInLevel(level, constraint);
                }
                loadRegistry().complete(claim, members);
                return members;
            } catch (RuntimeException | Error e) {
                loadRegistry().fail(claim, e);
                throw e;
            } finally {
                loadRegistry().exitLoad();
            }
        }

        private List<RolapMember> readMembersInLevel(
            RolapLevel level,
            TupleConstraint constraint)
        {
            // a join constraint needs the RolapCubeLevel; otherwise the
            // underlying hierarchy's reader loads the plain level
            boolean joinReq =
                (constraint instanceof SqlContextConstraint);
            List<RolapMember> list;
            final RolapCubeLevel cubeLevel = (RolapCubeLevel) level;
            if (!joinReq) {
                list =
                    rolapHierarchy.getMemberReader().getMembersInLevel(
                        cubeLevel.getRolapLevel(), constraint);
            } else {
                list =
                    super.getMembersInLevel(
                        level, constraint);
            }
            List<RolapMember> newlist = new ArrayList<>();
            for (RolapMember member : list) {
                RolapCubeMember cubeMember =
                    lookupCubeMemberWithParent(
                        member,
                        cubeLevel);
                newlist.add(cubeMember);
            }
            rolapCubeMemberCache.putChildren(
                level, constraint, newlist);

            return newlist;
        }

        private RolapCubeMember lookupCubeMemberWithParent(
            RolapMember member,
            RolapCubeLevel cubeLevel)
        {
            final RolapMember parentMember = member.getParentMember();
            final RolapCubeMember parentCubeMember;
            if (parentMember == null) {
                parentCubeMember = null;
            } else {
                // In parent-child hierarchies, a member's parent may be in the
                // same level.
                final RolapCubeLevel parentLevel =
                    parentMember.getLevel().getDepth() == member.getLevel().getDepth()
                            || cubeLevel.getParentLevel() == null
                        ? cubeLevel
                        : cubeLevel.getParentLevel();
                parentCubeMember =
                    lookupCubeMemberWithParent(
                        parentMember, parentLevel);
            }
            return lookupCubeMember(
                parentCubeMember, member, cubeLevel);
        }

        @Override
        public RolapMember getMemberByKey(
            RolapLevel level, List<Comparable> keyValues)
        {
            final RolapMember member =
                super.getMemberByKey(level, keyValues);
            return createAncestorMembers(
                this, (RolapCubeLevel) level, member);
        }

        @Override
		public RolapCubeMember lookupCubeMember(
            RolapCubeMember parent,
            RolapMember member,
            RolapCubeLevel level)
        {
            if (member.getKey() == Util.sqlNullValue && member.isAll()) {
                return getAllMember();
            }

            // members=off picks the NoCache reader at construction, so
            // this reader always caches
            Object key =
                rolapCubeMemberCache.makeKey(parent, member.getKey());
            RolapCubeMember cubeMember = (RolapCubeMember)
                rolapCubeMemberCache.getMember(key);
            if (cubeMember == null) {
                // putMember canonicalizes: a concurrent creator wins
                cubeMember = (RolapCubeMember) rolapCubeMemberCache.putMember(
                    key, new RolapCubeMember(parent, member, level));
            } else if (level.hasOrdinalExp()) {
                fixOrdinal(cubeMember, member.getOrdinal());
            }
            return cubeMember;
        }

        private void fixOrdinal(
            RolapCubeMember rlCubeMemberToFix,
            int ordinalToSet)
        {
          rlCubeMemberToFix.setOrdinal(ordinalToSet);
        }

        @Override
		public int getMemberCount() {
            return rolapHierarchy.getMemberReader().getMemberCount();
        }


    }

    /**
     * Same as {@link RolapCubeHierarchyMemberReader} but without caching
     * anything.
     */
    public class NoCacheRolapCubeHierarchyMemberReader
        extends NoCacheMemberReader
        implements RolapCubeHierarchyMemberReader
    {
        /**
         * cubeSource is passed as our member builder
         */
        protected final RolapCubeSqlMemberSource cubeSource;

        /**
         * Caches the RolapCubeMember wrappers. The inherited memberCache
         * holds the underlying hierarchy's RolapMembers, created when a
         * lookup involves the cube's fact table.
         */
        protected final MemberCache rolapCubeMemberCache;

        public NoCacheRolapCubeHierarchyMemberReader() {
            super(new SqlMemberSource(RolapCubeHierarchy.this));
            // the reader ITSELF is the (no-op) member cache: its
            // removeMember bumps the load registry, and flushMember's
            // cube branch reaches exactly getRolapCubeMemberCache() -
            // with a separate NoOpMemberCache here the K5 fence above
            // (role-restricted children cache) was armed on a counter
            // no flush path ever moved
            rolapCubeMemberCache = this;

            cubeSource =
                new RolapCubeSqlMemberSource(
                    this,
                    RolapCubeHierarchy.this,
                    rolapCubeMemberCache);

            cubeSource.setCache(rolapCubeMemberCache);
        }

        @Override
		public MemberBuilder getMemberBuilder() {
            return this.cubeSource;
        }

        @Override
		public MemberCache getRolapCubeMemberCache() {
            return rolapCubeMemberCache;
        }

        @Override
		public List<RolapMember> getRootMembers() {
            return getMembersInLevel((RolapLevel) getLevels().getFirst());
        }

        @Override
		protected void readMemberChildren(
            List<RolapMember> parentMembers,
            List<RolapMember> children,
            MemberChildrenConstraint constraint)
        {
            List<RolapMember> rolapChildren = new ArrayList<>();
            List<RolapMember> rolapParents = new ArrayList<>();
            Map<String, RolapCubeMember> lookup =
                new HashMap<>();

            // extract RolapMembers from their RolapCubeMember objects
            // populate lookup for reconnecting parents and children
            final List<RolapCubeMember> parentRolapCubeMemberList =
                Util.cast(parentMembers);
            for (RolapCubeMember member : parentRolapCubeMemberList) {
                final RolapMember rolapMember = member.getRolapMember();
                lookup.put(rolapMember.getUniqueName(), member);
                rolapParents.add(rolapMember);
            }

            // get member children from shared member reader if possible,
            // if not get them from our own source
            boolean joinReq =
                (constraint instanceof SqlContextConstraint);
            if (joinReq) {
                super.readMemberChildren(
                    parentMembers, rolapChildren, constraint);
            } else {
                rolapHierarchy.getMemberReader().getMemberChildren(
                    rolapParents, rolapChildren, constraint);
            }

            // now lookup or create RolapCubeMember
            for (RolapMember currMember : rolapChildren) {
                final RolapMember currParent = currMember.getParentMember();
                RolapCubeMember parent = currParent == null ? null
                    : lookup.get(currParent.getUniqueName());
                if (parent == null) {
                    // a dropped constraint can surface members outside the
                    // requested parents; nothing to attach them to here
                    continue;
                }
                RolapCubeLevel level =
                    parent.getLevel().getChildLevel();
                if (level == null) {
                    // most likely a parent child hierarchy
                    level = parent.getLevel();
                }
                RolapCubeMember newmember =
                    lookupCubeMember(
                        parent, currMember, level);
                children.add(newmember);
            }

        }

        @Override
		public Map<? extends Member, AccessMember> getMemberChildren(
            List<RolapMember> parentMembers,
            List<RolapMember> children,
            MemberChildrenConstraint constraint)
        {
            List<RolapMember> missed = new ArrayList<>();
            for (RolapMember parentMember : parentMembers) {
                // the null member has no children
                if (!parentMember.isNull()) {
                    missed.add(parentMember);
                }
            }
            if (!missed.isEmpty()) {
                readMemberChildren(missed, children, constraint);
            }
            return Util.toNullValuesMap(children);
        }


        @Override
		public List<RolapMember> getMembersInLevel(
            final RolapLevel level,
            TupleConstraint constraint)
        {

                // if a join is required, we need to pass in the RolapCubeLevel
                // vs. the regular level
                boolean joinReq =
                    (constraint instanceof SqlContextConstraint);
                final List<RolapMember> list;

                if (!joinReq) {
                    list =
                        rolapHierarchy.getMemberReader().getMembersInLevel(
                            ((RolapCubeLevel) level).getRolapLevel(),
                            constraint);
                } else {
                    list =
                        super.getMembersInLevel(
                            level, constraint);
                }

                return new AbstractList<RolapMember>() {
                    @Override
                    public RolapMember get(int index) {
                        return mutate(list.get(index));
                    }

                    @Override
                    public int size() {
                        return list.size();
                    }

                    private RolapMember mutate(final RolapMember member) {
                        RolapCubeMember parent = null;
                        if (member.getParentMember() != null) {
                            parent =
                                createAncestorMembers(
                                    NoCacheRolapCubeHierarchyMemberReader.this,
                                    (RolapCubeLevel) level.getParentLevel(),
                                    member.getParentMember());
                        }
                        return lookupCubeMember(
                            parent, member, (RolapCubeLevel) level);
                    }

                };
        }

        @Override
		public RolapCubeMember lookupCubeMember(
            RolapCubeMember parent,
            RolapMember member,
            RolapCubeLevel level)
        {
            if (member.getKey() == Util.sqlNullValue && member.isAll()) {
                    return getAllMember();
            }

            return new RolapCubeMember(parent, member, level);
        }

        @Override
		public int getMemberCount() {
            return rolapHierarchy.getMemberReader().getMemberCount();
        }
    }

    public static class RolapCubeSqlMemberSource extends SqlMemberSource {

        private final RolapCubeHierarchyMemberReader memberReader;
        private final MemberCache memberSourceCache;

        public RolapCubeSqlMemberSource(
            RolapCubeHierarchyMemberReader memberReader,
            RolapCubeHierarchy hierarchy,
            MemberCache memberSourceCache)
        {
            super(hierarchy);
            this.memberReader = memberReader;
            this.memberSourceCache = memberSourceCache;
        }

        @Override
		public RolapMember makeMember(
            RolapMember parentMember,
            RolapLevel childLevel,
            Object value,
            Object captionValue,
            boolean parentChild,
            SqlStatement stmt,
            Object key,
            int columnOffset)
            throws SQLException
        {
            final RolapCubeMember parentCubeMember =
                (RolapCubeMember) parentMember;
            final RolapCubeLevel childCubeLevel = (RolapCubeLevel) childLevel;
            final RolapMember parent;
            if (parentMember != null) {
                parent = parentCubeMember.getRolapMember();
            } else {
                parent = null;
            }
            RolapMember member =
                super.makeMember(
                    parent,
                    childCubeLevel.getRolapLevel(),
                    value, captionValue, parentChild, stmt, key,
                    columnOffset);
            return
                memberReader.lookupCubeMember(
                    parentCubeMember,
                    member, childCubeLevel);
        }

        @Override
		public MemberCache getMemberCache() {
            // this is a special cache used solely for rolapcubemembers
            return memberSourceCache;
        }

        @Override
		public RolapMember allMember() {
            return getHierarchy().getAllMember();
        }
    }
}
