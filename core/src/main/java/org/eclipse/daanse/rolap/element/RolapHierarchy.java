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

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.getTableAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.left;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.right;
import static org.eclipse.daanse.rolap.common.util.LevelUtil.getKeyExp;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.mdx.model.api.expression.operation.InternalOperationAtom;
import org.eclipse.daanse.mdx.model.api.expression.operation.OperationAtom;
import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.Quoting;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.Validator;
import org.eclipse.daanse.olap.api.access.AccessHierarchy;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.HierarchyAccess;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.access.RollupPolicy;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.calc.todo.TupleListCalc;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.DimensionType;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.LevelType;
import org.eclipse.daanse.olap.api.element.LimitedMember;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.formatter.CellFormatter;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.api.query.component.ResolvedFunCall;
import org.eclipse.daanse.olap.api.type.NumericType;
import org.eclipse.daanse.olap.api.type.SetType;
import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.calc.base.constant.ConstantCalcs;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.AbstractProfilingNestedTupleListCalc;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList;
import org.eclipse.daanse.olap.calc.base.value.CurrentValueUnknownCalc;
import org.eclipse.daanse.olap.common.HierarchyBase;
import org.eclipse.daanse.olap.common.InvalidHierarchyException;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.element.OlapMetaData;
import org.eclipse.daanse.olap.fun.FunUtil;
import org.eclipse.daanse.olap.function.core.FunctionMetaDataR;
import org.eclipse.daanse.olap.function.core.FunctionParameterR;
import org.eclipse.daanse.olap.function.def.AbstractFunctionDefinition;
import org.eclipse.daanse.olap.function.def.aggregate.AggregateCalc;
import org.eclipse.daanse.olap.query.component.HierarchyExpressionImpl;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.olap.query.component.UnresolvedFunCallImpl;
import org.eclipse.daanse.rolap.common.MemberReader;
import org.eclipse.daanse.rolap.common.RolapColumn;
import org.eclipse.daanse.rolap.common.RolapEvaluator;
import org.eclipse.daanse.rolap.common.RolapResult;
import org.eclipse.daanse.rolap.common.RolapSqlExpression;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.SmartRestrictedMemberReader;
import org.eclipse.daanse.rolap.common.SubstitutingMemberReader;
import org.eclipse.daanse.rolap.common.format.FormatterCreateContext;
import org.eclipse.daanse.rolap.common.format.FormatterFactory;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.common.util.LevelUtil;
import org.eclipse.daanse.rolap.common.util.PojoUtil;
import org.eclipse.daanse.rolap.common.util.RelationUtil;
import org.eclipse.daanse.rolap.mapping.api.model.DimensionConnectorMapping;
import org.eclipse.daanse.rolap.mapping.api.model.ExplicitHierarchyMapping;
import org.eclipse.daanse.rolap.mapping.api.model.HierarchyMapping;
import org.eclipse.daanse.rolap.mapping.api.model.InlineTableQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.JoinQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.LevelMapping;
import org.eclipse.daanse.rolap.mapping.api.model.ParentChildHierarchyMapping;
import org.eclipse.daanse.rolap.mapping.api.model.ParentChildLinkMapping;
import org.eclipse.daanse.rolap.mapping.api.model.QueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RelationalQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TableQueryMapping;
import org.eclipse.daanse.rolap.mapping.pojo.JoinQueryMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.JoinedQueryElementMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.PhysicalColumnMappingImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RolapHierarchy implements {@link Hierarchy} for a ROLAP database.
 *
 * The ordinal of a hierarchy <em>within a particular cube</em> is found by
 * calling {@link #getOrdinalInCube()}. Ordinals are contiguous and zero-based.
 * Zero is always the [Measures] dimension.
 *
 * NOTE: It is only valid to call that method on the measures hierarchy, and
 * on members of the {@link RolapCubeHierarchy} subclass. When the measures
 * hierarchy is of that class, we will move the method down.)
 *
 * @author jhyde
 * @since 10 August, 2001
  */
public class RolapHierarchy extends HierarchyBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(RolapHierarchy.class);
    private final static String levelMustHaveNameExpression =
        "Level ''{0}'' must have a name expression (a ''column'' attribute or an <Expression> child";
    private final static String hierarchyHasNoLevels = "Hierarchy ''{0}'' must have at least one level.";
    private final static String hierarchyHasNoParentColumn = "Parent child Hierarchy ''{0}'' must have parent column.";
    private final static String hierarchyLevelNamesNotUnique = "Level names within hierarchy ''{0}'' are not unique; there is more than one level with name ''{1}''.";
    private final static String hierarchyMustNotHaveMoreThanOneSource = "Hierarchy ''{0}'' has more than one source (memberReaderClass, <Table>, <Join> or <View>)";

    /**
     * The raw member reader. For a member reader which incorporates access
     * control and deals with hidden members (if the hierarchy is ragged), use
     * {@link #createMemberReader(Role)}.
     */
    private MemberReader memberReader;
    protected HierarchyMapping hierarchyMapping;
    private String memberReaderClass;
    protected QueryMapping relation;
    private Member defaultMember;
    private String defaultMemberName;
    private RolapNullMember nullMember;

    private String sharedHierarchyName;
    private String uniqueKeyLevelName;

    private Expression aggregateChildrenExpression;

    /**
     * The level that the null member belongs too.
     */
    protected final RolapLevel nullLevel;

    /**
     * The 'all' member of this hierarchy. This exists even if the hierarchy
     * does not officially have an 'all' member.
     */
    private RolapMemberBase allMember;
    private static final String ALL_LEVEL_CARDINALITY = "1";
    private final MetaData metaData;
    final RolapHierarchy closureFor;

    protected String displayFolder = null;
    private final static String invalidHierarchyCondition = "Hierarchy ''{0}'' is invalid (has no members)";

    /**
     * Creates a hierarchy.
     *
     * @param dimension Dimension
     * @param subName Name of this hierarchy
     * @param hasAll Whether hierarchy has an 'all' member
     * @param closureFor Hierarchy for which the new hierarchy is a closure;
     *     null for regular hierarchies
     */
    RolapHierarchy(
        RolapDimension dimension,
        String subName,
        String caption,
        boolean visible,
        String description,
        String displayFolder,
        boolean hasAll,
        RolapHierarchy closureFor,
        MetaData metaData)
    {
        super(dimension, subName, caption, visible, description, hasAll);
        this.displayFolder = displayFolder;
        this.metaData = metaData;
        this.allLevelName = "(All)";
        this.allMemberName =
            subName != null
            && (name.equals(new StringBuilder(subName).append(".").append(subName).toString()))
                ? new StringBuilder("All ").append(subName).append("s").toString()
                : new StringBuilder("All ").append(name).append("s").toString();
        this.closureFor = closureFor;
        if (hasAll) {
            this.levels = new ArrayList<Level>();
            this.levels.add(
                new RolapLevel(
                    this,
                    this.allLevelName,
                    null,
                    true,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    RolapProperty.emptyArray,
                    RolapLevel.FLAG_ALL | RolapLevel.FLAG_UNIQUE,
                    null,
                    null,
                    RolapLevel.HideMemberCondition.Never,
                    LevelType.REGULAR,
                    "",
                    OlapMetaData.empty()));
        } else {
            this.levels = new ArrayList<Level>();
        }

        // The null member belongs to a level with very similar properties to
        // the 'all' level.
        this.nullLevel =
            new RolapLevel(
                this,
                this.allLevelName,
                null,
                true,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RolapProperty.emptyArray,
                RolapLevel.FLAG_ALL | RolapLevel.FLAG_UNIQUE,
                null,
                null,
                RolapLevel.HideMemberCondition.Never,
                LevelType.NULL,
                "",
                OlapMetaData.empty());
    }

    /**
     * Creates a RolapHierarchy.
     *
     * @param dimension the dimension this hierarchy belongs to
     * @param xmlHierarchy the xml object defining this hierarchy
     * @param cubeDimensionMapping the xml object defining the cube
     *   dimension for this object
     */
    public RolapHierarchy(
        RolapCube cube,
        RolapDimension dimension,
        HierarchyMapping xmlHierarchy,
        DimensionConnectorMapping cubeDimensionMapping)
    {
        this(
            dimension,
            xmlHierarchy.getName(),
            xmlHierarchy.getName(),
            xmlHierarchy.isVisible(),
            xmlHierarchy.getDescription(),
            xmlHierarchy.getDisplayFolder(),
            xmlHierarchy.isHasAll(),
            null,
            RolapMetaData.createMetaData(xmlHierarchy.getAnnotations()));

        assert !(this instanceof RolapCubeHierarchy);

        this.hierarchyMapping = xmlHierarchy;
        QueryMapping xmlHierarchyRelation = xmlHierarchy.getQuery();
        if (xmlHierarchy.getQuery() == null
            && xmlHierarchy.getMemberReaderClass() == null
            && cube != null)
        {
          // if cube is virtual than there is no fact in it,
          // so look for it in source cube
          if(cube instanceof RolapVirtualCube) {
            RolapCube sourceCube = cube.getCatalog().lookupCube(cubeDimensionMapping.getPhysicalCube());
            if(sourceCube != null) {
              xmlHierarchyRelation = sourceCube.getFact();
            }
          } else {
            xmlHierarchyRelation = cube.getFact();
          }
        }

        this.relation = xmlHierarchyRelation;
        if (xmlHierarchyRelation instanceof InlineTableQueryMapping inlineTable) {
            this.relation =
                RolapUtil.convertInlineTableToRelation(
                    inlineTable,
                    getRolapCatalog().getInternalConnection().getContext().getDialect());
        }
        this.memberReaderClass = xmlHierarchy.getMemberReaderClass();
        this.uniqueKeyLevelName = xmlHierarchy.getUniqueKeyLevelName();

        // Create an 'all' level even if the hierarchy does not officially
        // have one.
        if (xmlHierarchy.getAllMemberName() != null) {
            this.allMemberName = xmlHierarchy.getAllMemberName();
        }
        if (xmlHierarchy.getAllLevelName() != null) {
            this.allLevelName = xmlHierarchy.getAllLevelName();
        }
        RolapLevel allLevel =
            new RolapLevel(
                this,
                this.allLevelName,
                null,
                true,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RolapProperty.emptyArray,
                RolapLevel.FLAG_ALL | RolapLevel.FLAG_UNIQUE,
                null,
                null,
                RolapLevel.HideMemberCondition.Never,
                LevelType.REGULAR, ALL_LEVEL_CARDINALITY,
                OlapMetaData.empty());
        allLevel.init(cubeDimensionMapping);
        this.allMember = new RolapMemberBase(
            null, allLevel, Util.sqlNullValue,
            allMemberName, Member.MemberType.ALL);
        // assign "all member" caption
        if (xmlHierarchy.getAllMemberCaption() != null
            && xmlHierarchy.getAllMemberCaption().length() > 0)
        {
            this.allMember.setCaption(xmlHierarchy.getAllMemberCaption());
        }
        this.allMember.setOrdinal(0);

        if (xmlHierarchy instanceof ExplicitHierarchyMapping eh) {
            if (eh.getLevels().isEmpty()) {
                throw new OlapRuntimeException(MessageFormat.format(hierarchyHasNoLevels,
                        getUniqueName()));
            }

            Set<String> levelNameSet = new HashSet<>();
            for (LevelMapping level : eh.getLevels()) {
                if (!levelNameSet.add(level.getName())) {
                    throw new OlapRuntimeException(MessageFormat.format(hierarchyLevelNamesNotUnique,
                        getUniqueName(), level.getName()));
                }
            }

            // If the hierarchy has an 'all' member, the 'all' level is level 0.
            if (hasAll) {
                this.levels = new ArrayList<Level>();
                this.levels.add(allLevel);
                int i = 1;
                for (LevelMapping xmlLevel : eh.getLevels()) {
                    if (getKeyExp(xmlLevel) == null
                        && xmlHierarchy.getMemberReaderClass() == null)
                    {
                        throw new OlapRuntimeException(MessageFormat.format(
                            levelMustHaveNameExpression, xmlLevel.getName()));
                    }
                    RolapLevel l = new RolapLevel(this, i, xmlLevel);
                    levels.add(l);
                    i++;
                }
            } else {
                this.levels = new ArrayList<Level>();
                int i = 0;
                for (LevelMapping xmlLevel : eh.getLevels()) {
                    RolapLevel rl = new RolapLevel(this, i, xmlLevel);
                    levels.add(rl);
                    i++;
                }
            }
        } else if (xmlHierarchy instanceof ParentChildHierarchyMapping pch ){
            if (pch.getLevel() == null) {
                throw new OlapRuntimeException(MessageFormat.format(hierarchyHasNoLevels,
                        getUniqueName()));
            }
            if (pch.getParentColumn() == null) {
                throw new OlapRuntimeException(MessageFormat.format(hierarchyHasNoParentColumn,
                        getUniqueName()));
            }
            // If the hierarchy has an 'all' member, the 'all' level is level 0.
            if (hasAll) {
                this.levels = new ArrayList<Level>();
                this.levels.add(allLevel);
                int i = 1;
                LevelMapping xmlLevel = pch.getLevel();
                
                if (getKeyExp(xmlLevel) == null
                    && xmlHierarchy.getMemberReaderClass() == null)
                {
                   throw new OlapRuntimeException(MessageFormat.format(
                       levelMustHaveNameExpression, xmlLevel.getName()));
                }
                
                StringBuilder sb = new StringBuilder();
                sb.append(xmlLevel.getName()).append(i);
                RolapLevel l = new RolapLevel(sb.toString(), LevelUtil.getParentExp(pch), pch.getNullParentValue(), pch.getParentChildLink(), this, i,
                        pch.isParentAsLeafEnable(), pch.getParentAsLeafNameFormat(), xmlLevel);
                levels.add(l);
                Map<Integer, Set<RolapMember>> childMap = getChildMap((RolapLevel)l);
                for (Map.Entry<Integer, Set<RolapMember>> e : childMap.entrySet()) {
                    if (e.getKey() != 0) {
                        i++;
                        sb = new StringBuilder();
                        sb.append(xmlLevel.getName()).append(i);
                        RolapLevel rl = new RolapLevel(sb.toString(), this, i, pch.isParentAsLeafEnable(), pch.getParentAsLeafNameFormat(), l.getLevelMapping());
                        this.levels.add(rl);
                    }
                }
            } else {
                this.levels = new ArrayList<Level>();
                int i = 0;
                LevelMapping xmlLevel = pch.getLevel();
                StringBuilder sb = new StringBuilder();
                sb.append(xmlLevel.getName()).append(i+1);
                RolapLevel l = new RolapLevel(sb.toString(), LevelUtil.getParentExp(pch), pch.getNullParentValue(), pch.getParentChildLink(), this, i,
                        pch.isParentAsLeafEnable(), pch.getParentAsLeafNameFormat(), xmlLevel);
                levels.add(l);
                Map<Integer, Set<RolapMember>> childMap = getChildMap((RolapLevel)l);
                for (Map.Entry<Integer, Set<RolapMember>> e : childMap.entrySet()) {
                    if (e.getKey() != 0) {
                        i++;
                        sb = new StringBuilder();
                        sb.append(xmlLevel.getName()).append(i+1);
                        RolapLevel rl = new RolapLevel(sb.toString(), this, i, pch.isParentAsLeafEnable(), pch.getParentAsLeafNameFormat(), l.getLevelMapping());
                        this.levels.add(rl);
                    }
                }
            }
        }
        String sharedDimensionName = cubeDimensionMapping.getDimension() != null && cubeDimensionMapping.getDimension().getName() != null
        		? cubeDimensionMapping.getDimension().getName() : cubeDimensionMapping.getOverrideDimensionName();
        this.sharedHierarchyName = sharedDimensionName;
        if (subName != null) {
            this.sharedHierarchyName += "." + subName; // e.g. "Time.Weekly"
        }

        /*
        if (xmlCubeDimension instanceof MappingDimensionUsage dimensionUsage) {
            String sharedDimensionName =
                dimensionUsage.source();
            this.sharedHierarchyName = sharedDimensionName;
            if (subName != null) {
                this.sharedHierarchyName += "." + subName; // e.g. "Time.Weekly"
            }
        } else {
            this.sharedHierarchyName = null;
        }
        */
        if (xmlHierarchyRelation != null
            && xmlHierarchy.getMemberReaderClass() != null)
        {
            throw new OlapRuntimeException(MessageFormat.format(
                hierarchyMustNotHaveMoreThanOneSource, getUniqueName()));
        }
        if (!Util.isEmpty(xmlHierarchy.getName())) {
            setCaption(xmlHierarchy.getName());
        }
        defaultMemberName = xmlHierarchy.getDefaultMember();
    }

    private Map<Integer, Set<RolapMember>> getChildMap(RolapLevel l) {
        if (this.memberReader == null) {
            this.memberReader = getRolapCatalog().createMemberReader(
                null, this, memberReaderClass);
        }
        List<RolapMember> members = this.memberReader.getMembers();
        Map<Integer, Set<RolapMember>> childMap = new HashMap<>();
        int i = 0;
        for (RolapMember member : members) {
            if (member.getParentMember() == null || member.getParentMember().getLevel() == null || member.getParentMember().getLevel().isAll()) {
                childMap.computeIfAbsent(i, k -> new HashSet<>()).add(member);
            } else {
                    Optional<Map.Entry<Integer, Set<RolapMember>>> o = childMap.entrySet().stream().filter(e -> e.getValue().contains(member.getParentMember())).findFirst();
                    if (o.isPresent()) {
                        childMap.computeIfAbsent(o.get().getKey() + 1, k -> new HashSet<>()).add(member);
                    } else {
                        childMap.computeIfAbsent(i++, k -> new HashSet<>()).add(member);
                    }
            }
        }
        return childMap;
    }

    @Override
	protected Logger getLogger() {
        return LOGGER;
    }

    @Override
	public String getDisplayFolder() {
        return this.displayFolder;
    }

    @Override
	public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RolapHierarchy that)) {
            return false;
        }

        if (sharedHierarchyName == null || that.sharedHierarchyName == null) {
            return false;
        } else {
            return sharedHierarchyName.equals(that.sharedHierarchyName)
                && getUniqueName().equals(that.getUniqueName());
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public RolapHierarchy getClosureFor() {
		return closureFor;
	}

	@Override
	protected int computeHashCode() {
        return super.computeHashCode()
            ^ (sharedHierarchyName == null
                ? 0
                : sharedHierarchyName.hashCode());
    }

    /**
     * Initializes a hierarchy within the context of a cube.
     */
    void init(DimensionConnectorMapping xmlDimension) {
        // first create memberReader
        if (this.memberReader == null) {
            this.memberReader = getRolapCatalog().createMemberReader(
                xmlDimension != null ? xmlDimension.getDimension() : null, this, memberReaderClass);
        }
        for (Level level : levels) {
            ((RolapLevel) level).init(xmlDimension);
        }
        if (defaultMemberName != null) {
            List<Segment> uniqueNameParts;
            if (defaultMemberName.contains("[")) {
                uniqueNameParts = Util.parseIdentifier(defaultMemberName);
            } else {
                uniqueNameParts =
                    Collections.<Segment>singletonList(
                        new IdImpl.NameSegmentImpl(
                            defaultMemberName,
                            Quoting.UNQUOTED));
            }

            // First look up from within this hierarchy. Works for unqualified
            // names, e.g. [USA].[CA].
            defaultMember = (Member) Util.lookupCompound(
                getRolapCatalog().getCatalogReaderWithDefaultRole(),
                this,
                uniqueNameParts,
                false,
                DataType.MEMBER,
                MatchType.EXACT);

            // Next look up within global context. Works for qualified names,
            // e.g. [Store].[USA].[CA] or [Time].[Weekly].[1997].[Q2].
            if (defaultMember == null) {
                defaultMember = (Member) Util.lookupCompound(
                    getRolapCatalog().getCatalogReaderWithDefaultRole(),
                    new DummyElement(),
                    uniqueNameParts,
                    false,
                    DataType.MEMBER,
                    MatchType.EXACT);
            }
            if (defaultMember == null) {
                throw Util.newInternal(
                    new StringBuilder("Can not find Default Member with name \"")
                        .append(defaultMemberName).append("\" in Hierarchy \"")
                        .append(getName()).append("\"").toString());
            }
        }
    }

    public void setMemberReader(MemberReader memberReader) {
        this.memberReader = memberReader;
    }

    public MemberReader getMemberReader() {
        return memberReader;
    }

    @Override
	public MetaData getMetaData() {
        return metaData;
    }

    public RolapLevel newMeasuresLevel() {
        RolapLevel level =
            new RolapLevel(
                this,
                "MeasuresLevel",
                null,
                true,
                null,
                this.levels.size(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RolapProperty.emptyArray,
                0,
                null,
                null,
                RolapLevel.HideMemberCondition.Never,
                LevelType.REGULAR,
                "",
                OlapMetaData.empty());
        this.levels.add(level);
        return level;
    }

    /**
     * If this hierarchy has precisely one table, returns that table;
     * if this hierarchy has no table, return the cube's fact-table;
     * otherwise, returns null.
     */
    public RelationalQueryMapping getUniqueTable() {
        if (relation instanceof RelationalQueryMapping r) {
            return r;
        } else if (relation instanceof JoinQueryMapping) {
            return null;
        } else {
            throw Util.newInternal(
                "hierarchy's relation is a " + relation.getClass());
        }
    }

    public boolean tableExists(String tableName) {
        return (relation != null) && getTable(tableName, relation) != null;
    }

    RelationalQueryMapping getTable(String tableName) {
        return relation == null ? null : getTable(tableName, relation);
    }

    private static RelationalQueryMapping getTable(
        String tableName,
        QueryMapping relationOrJoin)
    {
        if (relationOrJoin instanceof RelationalQueryMapping relation) {
            if (tableName.equals(RelationUtil.getAlias(relation)) || tableName.equals(RelationUtil.getTableName(relation))) {
                return relation;
            } else {
                return null;
            }
        } else {
            JoinQueryMapping join = (JoinQueryMapping) relationOrJoin;
            RelationalQueryMapping rel = getTable(tableName, left(join));
            if (rel != null) {
                return rel;
            }
            return getTable(tableName, right(join));
        }
    }

    public RolapCatalog getRolapCatalog() {
        return (RolapCatalog) dimension.getCatalog();
    }

    public QueryMapping getRelation() {
        return relation;
    }

    public void setRelation(QueryMapping relation) {
        this.relation = relation;
    }

    public HierarchyMapping getHierarchyMapping() {
        return hierarchyMapping;
    }

    @Override
	public Member getDefaultMember() {
        // use lazy initialization to get around bootstrap issues
        if (defaultMember == null) {
            List<RolapMember> rootMembers = memberReader.getRootMembers();
            final CatalogReader schemaReader =
                getRolapCatalog().getCatalogReaderWithDefaultRole();
            List<RolapMember> calcMemberList =
                Util.cast(schemaReader.getCalculatedMembers(getLevels().getFirst()));


            // Note: We require that the root member is not a hidden member
            // of a ragged hierarchy, but we do not require that it is
            // visible. In particular, if a cube contains no explicit
            // measures, the default measure will be the implicitly defined
            // [Fact Count] measure, which happens to be non-visible.

            // First look on Root
            for (RolapMember rootMember : rootMembers)
                {
                    if (rootMember.isHidden()) {
                        continue;
                    }
                    defaultMember = rootMember;
                    break;
                }

            // then if not found look  calcmembers
            if(defaultMember == null) {
                for (RolapMember calcMember : calcMemberList)
                {
                    if (calcMember.isHidden()) {
                        continue;
                    }
                    defaultMember = calcMember;
                    break;
                }
            }
            if (defaultMember == null) {
                throw new InvalidHierarchyException(MessageFormat.format(invalidHierarchyCondition,
                    this.getUniqueName()));
            }
        }
        return defaultMember;
    }

    @Override
	public Member getNullMember() {
        // use lazy initialization to get around bootstrap issues
        if (nullMember == null) {
            nullMember = new RolapNullMember(nullLevel);
        }
        return nullMember;
    }

    /**
     * Returns the 'all' member.
     */
    @Override
	public RolapMember getAllMember() {
        return allMember;
    }

    @Override
	public Member createMember(
        Member parent,
        Level level,
        String name,
        Formula formula)
    {
        if (formula == null) {
            return new RolapMemberBase(
                (RolapMember) parent, (RolapLevel) level, name);
        } else if (level.getDimension().isMeasures()) {
            return new RolapCalculatedMeasure(
                (RolapMember) parent, (RolapLevel) level, name, formula);
        } else {
            return new RolapCalculatedMember(
                (RolapMember) parent, (RolapLevel) level, name, formula);
        }
    }

    String getAlias() {
        return getName();
    }

    /**
     * Returns the name of the source hierarchy, if this hierarchy is shared,
     * otherwise null.
     *
     * If this hierarchy is a public -- that is, it belongs to a dimension
     * which is a usage of a shared dimension -- then
     * sharedHierarchyName holds the unique name of the shared
     * hierarchy; otherwise it is null.
     *
     *  Suppose this hierarchy is "Weekly" in the dimension "Order Date" of
     * cube "Sales", and that "Order Date" is a usage of the "Time"
     * dimension. Then sharedHierarchyName will be
     * "[Time].[Weekly]".
     */
    public String getSharedHierarchyName() {
        return sharedHierarchyName;
    }

    /**
     * Adds to the FROM clause of the query the tables necessary to access the
     * members of this hierarchy in an inverse join order, used with agg tables.
     * If expression is not null, adds the tables necessary to
     * compute that expression.
     *
     *  This method is idempotent: if you call it more than once, it only
     * adds the table(s) to the FROM clause once.
     *
     * @param query Query to add the hierarchy to
     * @param expression Level to qualify up to; if null, qualifies up to the
     *    topmost ('all') expression, which may require more columns and more
     *    joins
     */
    public void addToFromInverse(SqlQuery query, SqlExpression expression) {
        if (getRelation() == null) {
            throw Util.newError(
                new StringBuilder("cannot add hierarchy ").append(getUniqueName())
                    .append(" to query: it does not have a <Table>, <View> or <Join>").toString());
        }
        final boolean failIfExists = false;
        QueryMapping subRelation = relation;
        if (getRelation() instanceof JoinQueryMapping &&  expression != null) {
                subRelation =
                    relationSubsetInverse(relation, getTableAlias(expression));
        }
        query.addFrom(subRelation, null, failIfExists);
    }

    /**
     * Adds to the FROM clause of the query the tables necessary to access the
     * members of this hierarchy. If expression is not null, adds
     * the tables necessary to compute that expression.
     *
     *  This method is idempotent: if you call it more than once, it only
     * adds the table(s) to the FROM clause once.
     *
     * @param query Query to add the hierarchy to
     * @param expression Level to qualify up to; if null, qualifies up to the
     *    topmost ('all') expression, which may require more columns and more
     *    joins
     */
    public void addToFrom(SqlQuery query, SqlExpression expression) {
        if (getRelation() == null) {
            throw Util.newError(
                new StringBuilder("cannot add hierarchy ").append(getUniqueName())
                    .append(" to query: it does not have a <Table>, <View> or <Join>").toString());
        }
        query.registerRootRelation(getRelation());
        final boolean failIfExists = false;
        QueryMapping subRelation = getRelation();
        if (getRelation() instanceof JoinQueryMapping && expression != null) {
            // Suppose relation is
            //   (((A join B) join C) join D)
            // and the fact table is
            //   F
            // and our expression uses C. We want to make the expression
            //   F left join ((A join B) join C).
            // Search for the smallest subset of the relation which
            // uses C.
            subRelation =
                relationSubset(getRelation(), getTableAlias(expression));
            if (subRelation == null) {
                subRelation = getRelation();
            }
        }
        query.addFrom(
            subRelation,
            expression == null ? null : getTableAlias(expression),
            failIfExists);
    }

    /**
     * Adds a table to the FROM clause of the query.
     * If table is not null, adds the table. Otherwise, add the
     * relation on which this hierarchy is based on.
     *
     *  This method is idempotent: if you call it more than once, it only
     * adds the table(s) to the FROM clause once.
     *
     * @param query Query to add the hierarchy to
     * @param table table to add to the query
     */
    public void addToFrom(SqlQuery query, RolapStar.Table table) {
        if (getRelation() == null) {
            throw Util.newError(
                new StringBuilder("cannot add hierarchy ").append(getUniqueName())
                    .append(" to query: it does not have a <Table>, <View> or <Join>").toString());
        }
        final boolean failIfExists = false;
        QueryMapping subRelation = null;
        if (table != null) {
            // Suppose relation is
            //   (((A join B) join C) join D)
            // and the fact table is
            //   F
            // and the table to add is C. We want to make the expression
            //   F left join ((A join B) join C).
            // Search for the smallest subset of the relation which
            // joins with C.
            subRelation = lookupRelationSubset(getRelation(), table);
        }

        if (subRelation == null) {
            // If no table is found or specified, add the entire base relation.
            subRelation = getRelation();
        }

        boolean tableAdded =
            query.addFrom(
                subRelation,
                table != null ? table.getAlias() : null,
                failIfExists);
        if (tableAdded && table != null) {
            RolapStar.Condition joinCondition;
            do {
                joinCondition = table.getJoinCondition();
                if (joinCondition != null) {
                    query.addWhere(joinCondition);
                }
                table = table.getParentTable();
            } while (joinCondition != null);
        }
    }

    /**
     * Returns the smallest subset of relation which contains
     * the relation alias, or null if these is no relation with
     * such an alias, in inverse join order, used for agg tables.
     *
     * @param relation the relation in which to look for table by its alias
     * @param alias table alias to search for
     * @return the smallest containing relation or null if no matching table
     * is found in relation
     */
    private static QueryMapping relationSubsetInverse(
        QueryMapping relation,
        String alias)
    {
        if (relation instanceof RelationalQueryMapping table) {
            return RelationUtil.getAlias(table).equals(alias)
                ? relation
                : null;

        } else if (relation instanceof JoinQueryMapping join) {
            QueryMapping leftRelation =
                relationSubsetInverse(left(join), alias);
            return (leftRelation == null)
                ? relationSubsetInverse(right(join), alias)
                : join;

        } else {
            throw Util.newInternal("bad relation type " + relation);
        }
    }

    /**
     * Returns the smallest subset of relation which contains
     * the relation alias, or null if these is no relation with
     * such an alias.
     * @param relation the relation in which to look for table by its alias
     * @param alias table alias to search for
     * @return the smallest containing relation or null if no matching table
     * is found in relation
     */
    private static QueryMapping relationSubset(
        QueryMapping relation,
        String alias)
    {
        if (relation instanceof RelationalQueryMapping table) {
            return RelationUtil.getAlias(table).equals(alias)
                ? relation
                : null;

        } else if (relation instanceof JoinQueryMapping join) {
            QueryMapping rightRelation =
                relationSubset(right(join), alias);
            if (rightRelation == null) {
                return relationSubset(left(join), alias);
            } else {
                return SystemWideProperties.instance()
                    .FilterChildlessSnowflakeMembers
                    ? join
                    : rightRelation;
            }
        } else {
            throw Util.newInternal("bad relation type " + relation);
        }
    }

    /**
     * Returns the smallest subset of relation which contains
     * the table targetTable, or null if the targetTable is not
     * one of the joining table in relation.
     *
     * @param relation the relation in which to look for targetTable
     * @param targetTable table to add to the query
     * @return the smallest containing relation or null if no matching table
     * is found in relation
     */
    private static QueryMapping lookupRelationSubset(
        QueryMapping relation,
        RolapStar.Table targetTable)
    {
        if (relation instanceof TableQueryMapping table) {
            if (table.getTable().equals(targetTable.getTable())) {
                return relation;
            } else {
                // Not the same table if table names are different
                return null;
            }
        } else if (relation instanceof JoinQueryMapping join) {
            QueryMapping rightRelation =
                lookupRelationSubset(right(join), targetTable);
            if (rightRelation == null) {
                // Keep searching left.
                return lookupRelationSubset(
                    left(join), targetTable);
            } else {
                // Found a match.
                return join;
            }
        }
        return null;
    }

    /**
     * Creates a member reader which enforces the access-control profile of
     * role.
     *
     * This method may not be efficient, so the caller should take care
     * not to call it too often. A cache is a good idea.
     *
     * @param role Role
     * @return Member reader that implements access control
     *
     *  role != null
     *  return != null
     */
    public MemberReader createMemberReader(Role role) {
        final AccessHierarchy access = role.getAccess(this);
        final OperationAtom internalOperationAtom = new InternalOperationAtom("$x");
        final FunctionMetaData functionMetaData = new FunctionMetaDataR(internalOperationAtom, "x",
                DataType.NUMERIC, new FunctionParameterR[] { });

        switch (access) {
        case NONE:
            role.getAccess(this); // todo: remove
            throw Util.newInternal(
                "Illegal access to members of hierarchy " + this);
        case ALL:
            return (isRagged())
                ? new SmartRestrictedMemberReader(getMemberReader(), role)
                : getMemberReader();

        case CUSTOM:
            final HierarchyAccess hierarchyAccess =
                role.getAccessDetails(this);
            final RollupPolicy rollupPolicy =
                hierarchyAccess.getRollupPolicy();
            final NumericType returnType = NumericType.INSTANCE;
            return switch (rollupPolicy) {
            case FULL -> new SmartRestrictedMemberReader(
                                getMemberReader(), role);
            case PARTIAL -> {
                Type memberType1 =
                    new org.eclipse.daanse.olap.api.type.MemberType(
                        getDimension(),
                        this,
                        null,
                        null);
                SetType setType = new SetType(memberType1);
                TupleListCalc tupleListCalc =
                    new AbstractProfilingNestedTupleListCalc(
                         setType, new Calc[0])
                    {
                        @Override
						public TupleList evaluate(
                            Evaluator evaluator)
                        {
                            return
                                new UnaryTupleList(
                                    getLowestMembersForAccess(
                                        evaluator, hierarchyAccess, null));
                        }

                        @Override
						public boolean dependsOn(Hierarchy hierarchy) {
                            return true;
                        }
                    };
                final Calc partialCalc =
                    new LimitedRollupAggregateCalc(returnType, tupleListCalc);
                final Expression partialExp =
                    new ResolvedFunCallImpl(
                        new AbstractFunctionDefinition(functionMetaData) {
                            @Override
							public Calc compileCall(
								ResolvedFunCall call,
                                ExpressionCompiler compiler)
                            {
                                return partialCalc;
                            }

                            @Override
							public void unparse(Expression[] args, PrintWriter pw) {
                                pw.print("$RollupAccessibleChildren()");
                            }
                        },
                        new Expression[0],
                        returnType);
                yield new LimitedRollupSubstitutingMemberReader(
                                    getMemberReader(), role, hierarchyAccess, partialExp);
            }
            case HIDDEN -> {
                Expression hiddenExp =
                    new ResolvedFunCallImpl(
                        new AbstractFunctionDefinition(functionMetaData) {
                            @Override
							public Calc compileCall(
									ResolvedFunCall call, ExpressionCompiler compiler)
                            {
                                return ConstantCalcs.nullCalcOf(returnType);
                            }

                            @Override
							public void unparse(Expression[] args, PrintWriter pw) {
                                pw.print("$RollupAccessibleChildren()");
                            }
                        },
                        new Expression[0],
                        returnType);
                yield new LimitedRollupSubstitutingMemberReader(
                                    getMemberReader(), role, hierarchyAccess, hiddenExp);
            }
            default -> throw Util.unexpected(rollupPolicy);
            };
        default:
            throw Util.badValue(access);
        }
    }

    /**
     * Goes recursively down a hierarchy and builds a list of the
     * members that should be constrained on because of access controls.
     * It isn't sufficient to constrain on the current level in the
     * evaluator because the actual constraint could be even more limited
     * Example. If we only give access to Seattle but the query is on
     * the country level, we have to constrain at the city level, not state,
     * or else all the values of all cities in the state will be returned.
     */
    public List<Member> getLowestMembersForAccess(
        Evaluator evaluator,
        HierarchyAccess hAccess,
        Map<Member, AccessMember> membersWithAccess)
    {
        if (membersWithAccess == null) {
            membersWithAccess =
                FunUtil.getNonEmptyMemberChildrenWithDetails(
                    evaluator,
                    ((RolapEvaluator) evaluator)
                        .getExpanding());
        }
        boolean goesLower = false;
        for (Map.Entry<Member, AccessMember> entry : membersWithAccess.entrySet()) {
            Member member = entry.getKey();
            AccessMember access = membersWithAccess.get(member);
            if (access == null) {
                access = hAccess.getAccess(member);
            }
            if (access != AccessMember.ALL) {
                goesLower = true;
                break;
            }
        }
        if (goesLower) {
            // We still have to go one more level down.
            Map<Member, AccessMember> newMap =
                new HashMap<>();
            for (Member member : membersWithAccess.keySet()) {
                int savepoint = evaluator.savepoint();
                try {
                    evaluator.setContext(member);
                    newMap.putAll(
                        FunUtil.getNonEmptyMemberChildrenWithDetails(
                            evaluator,
                            member));
                } finally {
                    evaluator.restore(savepoint);
                }
            }
            // Now pass it recursively to this method.
            return getLowestMembersForAccess(
                evaluator, hAccess, newMap);
        }
        return new ArrayList<>(membersWithAccess.keySet());
    }

    /**
     * A hierarchy is ragged if it contains one or more levels with hidden
     * members.
     */
    @Override
	public boolean isRagged() {
        for (Level level : levels) {
            if (((RolapLevel) level).getHideMemberCondition()
                != RolapLevel.HideMemberCondition.Never)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an expression which will compute a member's value by aggregating
     * its children.
     *
     * It is efficient to share one expression between all calculated members
     * in a parent-child hierarchy, so we only need need to validate the
     * expression once.
     */
    public synchronized Expression getAggregateChildrenExpression() {
        if (aggregateChildrenExpression == null) {
            UnresolvedFunCallImpl fc = new UnresolvedFunCallImpl(
            		new InternalOperationAtom("$AggregateChildren"),
                new Expression[] {new HierarchyExpressionImpl(this)});
            Validator validator =
                    Util.createSimpleValidator(getRolapCatalog().getInternalConnection().getContext().getFunctionService());
            aggregateChildrenExpression = fc.accept(validator);
        }
        return aggregateChildrenExpression;
    }

    /**
     * Builds a dimension which maps onto a table holding the transitive
     * closure of the relationship for this parent-child level.
     *
     * This method is triggered by the
     * mondrian.olap.MappingClosure element
     * in a schema, and is only meaningful for a parent-child hierarchy.
     *
     * When a Schema contains a parent-child Hierarchy that has an
     * associated closure table, Mondrian creates a parallel internal
     * Hierarchy, called a "closed peer", that refers to the closure table.
     * This is indicated in the schema at the level of a Level, by including a
     * Closure element. The closure table represents
     * the transitive closure of the parent-child relationship.
     *
     * The peer dimension, with its single hierarchy, and 3 levels (all,
     * closure, item) really 'belong to' the parent-child level. If a single
     * hierarchy had two parent-child levels (however unlikely this might be)
     * then each level would have its own auxiliary dimension.
     *
     * For example, in the demo schema the [HR].[Employee] dimension
     * contains a parent-child hierarchy:
     *
     *
     * &lt;Dimension name="Employees" foreignKey="employee_id"&gt;
     *   &lt;Hierarchy hasAll="true" allMemberName="All Employees"
     *         primaryKey="employee_id"&gt;
     *     &lt;Table name="employee"/&gt;
     *     &lt;Level name="Employee Id" type="Numeric" uniqueMembers="true"
     *            column="employee_id" parentColumn="supervisor_id"
     *            nameColumn="full_name" nullParentValue="0"&gt;
     *       &lt;Closure parentColumn="supervisor_id"
     *                   childColumn="employee_id"&gt;
     *          &lt;Table name="employee_closure"/&gt;
     *       &lt;/Closure&gt;
     *       ...
     *
     * The internal closed peer Hierarchy has this structure:
     *
     * &lt;Dimension name="Employees" foreignKey="employee_id"&gt;
     *     ...
     *     &lt;Hierarchy name="Employees$Closure"
     *         hasAll="true" allMemberName="All Employees"
     *         primaryKey="employee_id" primaryKeyTable="employee_closure"&gt;
     *       &lt;Join leftKey="supervisor_id" rightKey="employee_id"&gt;
     *         &lt;Table name="employee_closure"/&gt;
     *         &lt;Table name="employee"/&gt;
     *       &lt;/Join&gt;
     *       &lt;Level name="Closure"  type="Numeric" uniqueMembers="false"
     *           table="employee_closure" column="supervisor_id"/&gt;
     *       &lt;Level name="Employee" type="Numeric" uniqueMembers="true"
     *           table="employee_closure" column="employee_id"/&gt;
     *     &lt;/Hierarchy&gt;
     *
     *
     * Note that the original Level with the Closure produces two Levels in
     * the closed peer Hierarchy: a simple peer (Employee) and a closed peer
     * (Closure).
     *
     * @param src a parent-child Level that has a Closure clause
     * @param clos a Closure clause
     * @return the closed peer Level in the closed peer Hierarchy
     */
    public RolapDimension createClosedPeerDimension(
        RolapLevel src,
        ParentChildLinkMapping clos)
    {
        // REVIEW (mb): What about attribute primaryKeyTable?

        // Create a peer dimension.
        RolapDimension peerDimension = new RolapDimension(
            dimension.getCatalog(),
            new StringBuilder(dimension.getName()).append("$Closure").toString(),
            null,
            true,
            "Closure dimension for parent-child hierarchy " + getName(),
            DimensionType.STANDARD_DIMENSION,
            OlapMetaData.empty());

        // Create a peer hierarchy.
        RolapHierarchy peerHier = peerDimension.newHierarchy(null, true, this);
        peerHier.allMemberName = getAllMemberName();
        peerHier.allMember = (RolapMemberBase) getAllMember();
        peerHier.allLevelName = getAllLevelName();
        peerHier.sharedHierarchyName = getSharedHierarchyName();
        JoinQueryMappingImpl join = JoinQueryMappingImpl.builder()
        		.withLeft(JoinedQueryElementMappingImpl.builder().withKey((PhysicalColumnMappingImpl) PojoUtil.getColumn(clos.getParentColumn())).withQuery(PojoUtil.copy(clos.getTable())).build())
        		.withRight(JoinedQueryElementMappingImpl.builder().withKey((PhysicalColumnMappingImpl) PojoUtil.getColumn(clos.getChildColumn())).withQuery(PojoUtil.copy(relation)).build())
        		.build();
        peerHier.relation = join;


        // Create the upper level.
        // This represents all groups of descendants. For example, in the
        // Employee closure hierarchy, this level has a row for every employee.
        int index = peerHier.levels.size();
        int flags = src.getFlags() &~ RolapLevel.FLAG_UNIQUE;
        RolapSqlExpression keyExp =
            new RolapColumn(clos.getTable().getTable().getName(), clos.getParentColumn().getName());

        RolapLevel level =
            new RolapLevel(
                peerHier, "Closure", caption, true, description, index++,
                keyExp, null, null, null,
                null, null,  // no longer a parent-child hierarchy
                null,
                RolapProperty.emptyArray,
                flags | RolapLevel.FLAG_UNIQUE,
                src.getDatatype(),
                null,
                src.getHideMemberCondition(),
                src.getLevelType(),
                "",
                OlapMetaData.empty());
        peerHier.levels.add(level);

        // Create lower level.
        // This represents individual items. For example, in the Employee
        // closure hierarchy, this level has a row for every direct and
        // indirect report of every employee (which is more than the number
        // of employees).
        flags = src.getFlags() | RolapLevel.FLAG_UNIQUE;
        keyExp = new RolapColumn(clos.getTable().getTable().getName(), clos.getChildColumn().getName());
        RolapLevel sublevel = new RolapLevel(
            peerHier,
            "Item",
            null,
            true,
            null,
            index++,
            keyExp,
            null,
            null,
            null,
            null,
            null,  // no longer a parent-child hierarchy
            null,
            RolapProperty.emptyArray,
            flags,
            src.getDatatype(),
            src.getInternalType(),
            src.getHideMemberCondition(),
            src.getLevelType(),
            "",
            OlapMetaData.empty());
        peerHier.levels.add(sublevel);

        return peerDimension;
    }

    /**
     * Sets default member of this Hierarchy.
     *
     * @param defaultMember Default member
     */
    public void setDefaultMember(Member defaultMember) {
        if (defaultMember != null) {
            this.defaultMember = defaultMember;
        }
    }


    /**
     * Gets "unique key level name" attribute of this Hierarchy, if set.
     * If set, this property indicates that all level properties are
     * functionally dependent (invariant) on their associated levels,
     * and that the set of levels from the root to the named level (inclusive)
     * effectively defines an alternate key.
     *
     * This allows the GROUP BY to be eliminated from associated queries.
     *
     * @return the name of the "unique key" level, or null if not specified
     */
    public String getUniqueKeyLevelName() {
        return uniqueKeyLevelName;
    }

    /**
     * Returns the ordinal of this hierarchy in its cube.
     *
     * Temporarily defined against RolapHierarchy; will be moved to
     * RolapCubeHierarchy as soon as the measures hierarchy is a
     * RolapCubeHierarchy.
     *
     * @return Ordinal of this hierarchy in its cube
     */
    @Override
    public int getOrdinalInCube() {
        // This is temporary to verify that all calls to this method are for
        // the measures hierarchy. For all other hierarchies, the context will
        // be a RolapCubeHierarchy.
        //
        // In particular, if this method is called from
        // RolapEvaluator.setContext, the caller of that method should have
        // passed in a RolapCubeMember, not a RolapMember.
        assert dimension.isMeasures();
        return 0;
    }


    /**
     * A RolapNullMember is the null member of its hierarchy.
     * Every hierarchy has precisely one. They are yielded by operations such as
     * [Gender].[All].ParentMember. Null members are usually
     * omitted from sets (in particular, in the set constructor operator "{ ...
     * }".
     */
    static class RolapNullMember extends RolapMemberBase {
        RolapNullMember(final RolapLevel level) {
            super(
                null,
                level,
                Util.sqlNullValue,
                RolapUtil.mdxNullLiteral(),
                MemberType.NULL);
            assert level != null;
        }
    }

    /**
     * Calculated member which is also a measure (that is, a member of the
     * [Measures] dimension).
     */
    public class RolapCalculatedMeasure
        extends RolapCalculatedMember
        implements RolapMeasure
    {
        private RolapResult.ValueFormatter cellFormatter;

        public RolapCalculatedMeasure(
            RolapMember parent, RolapLevel level, String name, Formula formula)
        {
            super(parent, level, name, formula);
        }

        @Override
		public synchronized void setProperty(String name, Object value) {
            if (name.equals(StandardProperty.CELL_FORMATTER.getName())) {
                String cellFormatterClass = (String) value;
                FormatterCreateContext formatterContext =
                    new FormatterCreateContext.Builder(getUniqueName())
                        .formatterAttr(cellFormatterClass)
                        .build();
                setCellFormatter(
                    FormatterFactory.instance()
                        .createCellFormatter(formatterContext));
            }
            if (name.equals(StandardProperty.CELL_FORMATTER_SCRIPT.getName())) {
                String language = (String) getPropertyValue(
                		StandardProperty.CELL_FORMATTER_SCRIPT_LANGUAGE.getName());
                String scriptText = (String) value;
                FormatterCreateContext formatterContext =
                    new FormatterCreateContext.Builder(getUniqueName())
                        .script(scriptText, language)
                        .build();
                setCellFormatter(
                    FormatterFactory.instance()
                        .createCellFormatter(formatterContext));
            }
            super.setProperty(name, value);
        }

        @Override
		public RolapResult.ValueFormatter getFormatter() {
            return cellFormatter;
        }

        private void setCellFormatter(CellFormatter cellFormatter) {
            if (cellFormatter != null) {
                this.cellFormatter =
                    new RolapResult.CellFormatterValueFormatter(cellFormatter);
            }
        }
    }

    /**
     * Substitute for a member in a hierarchy whose rollup policy is 'partial'
     * or 'hidden'. The member is calculated using an expression which
     * aggregates only visible descendants.
     *
     * Note that this class extends RolapCubeMember only because other code
     * expects that all members in a RolapCubeHierarchy are RolapCubeMembers.
     * As part of mondrian.util.Bug#BugSegregateRolapCubeMemberFixed,
     * maybe make org.eclipse.daanse.rolap.common.RolapCubeMember an interface.
     *
     * org.eclipse.daanse.olap.api.access.MappingRole.RollupPolicy
     */
    public static class LimitedRollupMember extends RolapCubeMember implements LimitedMember{
        public final RolapMember member;
        private final Expression exp;
        final HierarchyAccess hierarchyAccess;

        public LimitedRollupMember(
            RolapCubeMember member,
            Expression exp,
            HierarchyAccess hierarchyAccess)
        {
            super(
                member.getParentMember(),
                member.getRolapMember(),
                member.getLevel());
            this.hierarchyAccess = hierarchyAccess;
            assert !(member instanceof LimitedRollupMember);
            this.member = member;
            this.exp = exp;
        }

        @Override
		public boolean equals(Object o) {
            return o instanceof LimitedRollupMember limitedRollupMember
                && limitedRollupMember.member.equals(member);
        }

        @Override
		public int hashCode() {
            return member.hashCode();
        }

        @Override
		public Expression getExpression() {
            return exp;
        }

        @Override
		protected boolean computeCalculated(final MemberType memberType) {
            return true;
        }

        @Override
		public boolean isCalculated() {
            return false;
        }

        @Override
		public boolean isEvaluated() {
            return true;
        }

        public RolapMember getSourceMember() { return this.member; }

        @Override
        public Member getMember() {
            return member;
        }

        @Override
        public HierarchyAccess getHierarchyAccess() {
            return hierarchyAccess;
        }
    }

    /**
     * Member reader which wraps a hierarchy's member reader, and if the
     * role has limited access to the hierarchy, replaces members with
     * dummy members which evaluate to the sum of only the accessible children.
     */
    private static class LimitedRollupSubstitutingMemberReader
        extends SubstitutingMemberReader
    {
        private final HierarchyAccess hierarchyAccess;
        private final Expression exp;

        /**
         * Creates a LimitedRollupSubstitutingMemberReader.
         *
         * @param memberReader Underlying member reader
         * @param role Role to enforce
         * @param hierarchyAccess Access this role has to the hierarchy
         * @param exp Expression for hidden member
         */
        public LimitedRollupSubstitutingMemberReader(
            MemberReader memberReader,
            Role role,
            HierarchyAccess hierarchyAccess,
            Expression exp)
        {
            super(
                new SmartRestrictedMemberReader(
                    memberReader, role));
            this.hierarchyAccess = hierarchyAccess;
            this.exp = exp;
        }

        @Override
		public Map<? extends Member, AccessMember> getMemberChildren(
            RolapMember member,
            List<RolapMember> memberChildren,
            MemberChildrenConstraint constraint)
        {
            return memberReader.getMemberChildren(
                member,
                new SubstitutingMemberList(memberChildren),
                constraint);
        }

        @Override
		public Map<? extends Member, AccessMember> getMemberChildren(
            List<RolapMember> parentMembers,
            List<RolapMember> children,
            MemberChildrenConstraint constraint)
        {
            return memberReader.getMemberChildren(
                parentMembers,
                new SubstitutingMemberList(children),
                constraint);
        }

        public RolapMember substitute(RolapMember member, AccessMember access) {
            if (member instanceof MultiCardinalityDefaultMember)
            {
                return new LimitedRollupMember(
                    (RolapCubeMember)
                        ((MultiCardinalityDefaultMember) member)
                            .member.getParentMember(),
                    exp,
                    hierarchyAccess);
            }
            if (member != null
                && (access == AccessMember.CUSTOM || hierarchyAccess
                    .hasInaccessibleDescendants(member)))
            {
                // Member is visible, but at least one of its
                // descendants is not.
                if (member instanceof LimitedRollupMember limitedRollupMember) {
                    member = limitedRollupMember.member;
                }
                return new LimitedRollupMember(
                    (RolapCubeMember) member,
                    exp,
                    hierarchyAccess);
            } else {
                // No need to substitute. Member and all of its
                // descendants are accessible. Total for member
                // is same as for FULL policy.
                return member;
            }
        }

        @Override
		public RolapMember substitute(final RolapMember member) {
            if (member == null) {
                return null;
            }
            return substitute(member, hierarchyAccess.getAccess(member));
        }

        @Override
        public RolapMember desubstitute(RolapMember member) {
            if (member instanceof LimitedRollupMember limitedRollupMember) {
                return limitedRollupMember.member;
            } else {
                return member;
            }
        }
    }

    /**
     * Compiled expression that computes rollup over a set of visible children.
     * The {@code listCalc} expression determines that list of children.
     */
    public static class LimitedRollupAggregateCalc
        extends AggregateCalc
    {
        public LimitedRollupAggregateCalc(
            Type returnType,
            TupleListCalc tupleListCalc)
        {
            super(
                returnType,
                tupleListCalc,
                new CurrentValueUnknownCalc(returnType));
        }
    }

    /**
     * Dummy element that acts as a namespace for resolving member names within
     * shared hierarchies. Acts like a cube that has a single child, the
     * hierarchy in question.
     */
    private class DummyElement implements OlapElement {
        @Override
		public String getUniqueName() {
            throw new UnsupportedOperationException();
        }

        @Override
		public String getName() {
            return "$";
        }

        @Override
		public String getDescription() {
            throw new UnsupportedOperationException();
        }

        @Override
		public OlapElement lookupChild(
            CatalogReader schemaReader,
            Segment s,
            MatchType matchType)
        {
            if (!(s instanceof NameSegment nameSegment)) {
                return null;
            }
            if (Util.equalName(nameSegment.getName(), dimension.getName())) {
                return dimension;
            }
            // Archaic form <dimension>.<hierarchy>, e.g. [Time.Weekly].[1997]
            return null;
        }

        @Override
		public String getQualifiedName() {
            throw new UnsupportedOperationException();
        }

        @Override
		public String getCaption() {
            throw new UnsupportedOperationException();
        }

        @Override
		public Hierarchy getHierarchy() {
            throw new UnsupportedOperationException();
        }

        @Override
		public Dimension getDimension() {
            throw new UnsupportedOperationException();
        }

        @Override
		public boolean isVisible() {
            throw new UnsupportedOperationException();
        }

        @Override
		public String getLocalized(LocalizedProperty prop, Locale locale) {
            throw new UnsupportedOperationException();
        }
    }
}
