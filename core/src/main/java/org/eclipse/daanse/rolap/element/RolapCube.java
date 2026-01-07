/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2021 Hitachi Vantara and others
 * Copyright (C) 2021-2022 Sergei Semenkov
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

import static org.eclipse.daanse.rolap.common.util.CalculatedMemberUtil.getFormatString;
import static org.eclipse.daanse.rolap.common.util.CalculatedMemberUtil.getFormula;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.changeLeftRight;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.getLeftAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.getRightAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.left;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.right;
import static org.eclipse.daanse.rolap.common.util.PojoUtil.copy;
import static org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.mdx.model.api.expression.operation.FunctionOperationAtom;
import org.eclipse.daanse.mdx.model.api.expression.operation.OperationAtom;
import org.eclipse.daanse.olap.access.RoleImpl;
import org.eclipse.daanse.olap.api.CacheControl;
import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.OlapAction;
import org.eclipse.daanse.olap.api.Parameter;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.Statement;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.DimensionType;
import org.eclipse.daanse.olap.api.element.DrillThroughAction;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.KPI;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.NamedSet;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.query.component.CellProperty;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.api.query.component.MemberProperty;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.query.component.ResolvedFunCall;
import org.eclipse.daanse.olap.api.result.AllocationPolicy;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.element.CubeBase;
import org.eclipse.daanse.olap.element.OlapMetaData;
import org.eclipse.daanse.olap.element.SetBase;
import org.eclipse.daanse.olap.exceptions.CalcMemberNotUniqueException;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.function.core.FunctionMetaDataR;
import org.eclipse.daanse.olap.function.core.FunctionParameterR;
import org.eclipse.daanse.olap.function.def.AbstractFunctionDefinition;
import org.eclipse.daanse.olap.impl.StatementImpl;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.query.component.FormulaImpl;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.olap.query.component.QueryAxisImpl;
import org.eclipse.daanse.olap.query.component.QueryImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.rolap.common.AbstractRolapAction;
import org.eclipse.daanse.rolap.common.EnumConvertor;
import org.eclipse.daanse.rolap.common.HierarchyUsage;
import org.eclipse.daanse.rolap.common.MemberCacheHelper;
import org.eclipse.daanse.rolap.common.MemberReader;
import org.eclipse.daanse.rolap.common.RelNode;
import org.eclipse.daanse.rolap.common.RolapCubeCatalogReader;
import org.eclipse.daanse.rolap.common.RolapCubeComparator;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.RolapWritebackTable;
import org.eclipse.daanse.rolap.common.SmartMemberReader;
import org.eclipse.daanse.rolap.common.Utils;
import org.eclipse.daanse.rolap.common.WritebackUtil;
import org.eclipse.daanse.rolap.common.aggmatcher.ExplicitRules;
import org.eclipse.daanse.rolap.common.cache.SoftSmartCache;
import org.eclipse.daanse.rolap.common.util.DimensionUtil;
import org.eclipse.daanse.rolap.common.util.PojoUtil;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.mapping.model.SqlStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RolapCube implements {@link Cube} for a ROLAP database.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public abstract class RolapCube extends CubeBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(RolapCube.class);
    public static final String BAD_RELATION_TYPE = "bad relation type ";
    private final static String noTimeDimensionInCube =
            "Cannot use the function ''{0}'', no time dimension is available for this cube.";
    private final static String calcMemberHasBadDimension =
        "Unknown hierarchy ''{0}'' for calculated member ''{1}'' in cube ''{2}''";
    private final static String calcMemberHasDifferentParentAndHierarchy =
        "The calculated member ''{0}'' in cube ''{1}'' is defined for hierarchy ''{2}'' but its parent member is not part of that hierarchy";
    private final static String calcMemberHasUnknownParent =
        "Cannot find a parent with name ''{0}'' for calculated member ''{1}'' in cube ''{2}''";
    private final static String exprAndValueForMemberProperty =
        "Member property must not have both a value and an expression. (Property ''{0}'' of member ''{1}'' of cube ''{2}''.)";
    private final static String hierarchyMustHaveForeignKey =
        "Hierarchy ''{0}'' in cube ''{1}'' must have a foreign key, since it is not based on the cube''s fact table.";
    private final static String namedSetNotUnique = "Named set ''{0}'' already exists in cube ''{1}''";
    private final static String neitherExprNorValueForCalcMemberProperty =
        "Member property must have a value or an expression. (Property ''{0}'' of member ''{1}'' of cube ''{2}''.)";
    private final static String unknownNamedSetHasBadFormula = "Named set in cube ''{0}'' has bad formula";

    private final RolapCatalog catalog;

    private final MetaData metaData;
    private final RolapHierarchy measuresHierarchy;

    private org.eclipse.daanse.rolap.mapping.model.RelationalQuery restoreFact = null;

    /** For SQL generator. Fact table. */
    private org.eclipse.daanse.rolap.mapping.model.RelationalQuery fact;

    /** Schema reader which can see this cube and nothing else. */
    private CatalogReader schemaReader;

    /**
     * List of calculated members.
     */
    private final List<Formula> calculatedMemberList = new ArrayList<>();

    private List<KPI> kpis=new ArrayList<>();
    /**
     * Role-based cache of calculated members
     */
    private final SoftSmartCache<Role, List<Member>>
        roleToAccessibleCalculatedMembers = new SoftSmartCache<>(); //TODO not used

    /**
     * List of named sets.
     */
    private final List<Formula> namedSetList = new ArrayList<>();

    /** Contains {@link HierarchyUsage}s for this cube */
    private final List<HierarchyUsage> hierarchyUsages;

    private RolapStar star;
    private ExplicitRules.Group aggGroup;

    private final Map<Hierarchy, HierarchyUsage> firstUsageMap =
        new HashMap<>();

    public RolapBaseCubeMeasure factCountMeasure;

    final List<RolapHierarchy> hierarchyList =
        new ArrayList<>();

    private Map<RolapLevel, RolapCubeLevel> virtualToBaseMap =
        new HashMap<>();

    BitKey closureColumnBitKey = null;


	final List<AbstractRolapAction> actionList =
            new ArrayList<>();

    private Optional<RolapWritebackTable> writebackTable = Optional.empty();

    /**
     * Used for virtual cubes.
     * Contains a list of all base cubes related to a virtual cube
     */
    private List<RolapCube> baseCubes;

    private Context context;

    protected RolapCube(
            RolapCatalog catalog,
            org.eclipse.daanse.rolap.mapping.model.Catalog catalogMapping,
            org.eclipse.daanse.rolap.mapping.model.PhysicalCube cubeMapping,
            boolean isCache,
            org.eclipse.daanse.rolap.mapping.model.RelationalQuery fact,
            Context context)
        {
        this(
                catalog,
                catalogMapping,
                cubeMapping.getName(),
                cubeMapping.isVisible(),
                cubeMapping.getName(),
                cubeMapping.getDescription(),
                isCache,
                fact,
                cubeMapping.getDimensionConnectors(),
                RolapMetaData.createMetaData(cubeMapping.getAnnotations()),
                context);
        catalog.addCube(cubeMapping, this);
        fillKpiIfExist(cubeMapping);
        logMessage();
    }

    protected RolapCube(
            RolapCatalog catalog,
            org.eclipse.daanse.rolap.mapping.model.Catalog catalogMapping,
            org.eclipse.daanse.rolap.mapping.model.VirtualCube cubeMapping,
            boolean isCache,
            org.eclipse.daanse.rolap.mapping.model.RelationalQuery fact,
            Context context)
        {
        this(
                catalog,
                catalogMapping,
                cubeMapping.getName(),
                cubeMapping.isVisible(),
                cubeMapping.getName(),
                cubeMapping.getDescription(),
                isCache,
                fact,
                cubeMapping.getDimensionConnectors(),
                RolapMetaData.createMetaData(cubeMapping.getAnnotations()),
                context);
        catalog.addCube(cubeMapping, this);
        fillKpiIfExist(cubeMapping);
        logMessage();
    }

    /**
     * Private constructor used by both normal cubes and virtual cubes.
     *
     * @param catalog Schema cube belongs to
     * @param name Name of cube
     * @param caption Caption
     * @param description Description
     * @param fact Definition of fact table*
     * @param metaData Annotations
     */
    private RolapCube(
        RolapCatalog catalog,
        org.eclipse.daanse.rolap.mapping.model.Catalog catalogMapping,
        String name,
        boolean visible,
        String caption,
        String description,
        boolean isCache,
        org.eclipse.daanse.rolap.mapping.model.RelationalQuery fact,
        List<? extends org.eclipse.daanse.rolap.mapping.model.DimensionConnector> dimensions,
        MetaData metaData,
        Context context)
    {
        super(
            name,
            caption,
            visible,
            description,
            new ArrayList<>());

        assert metaData != null;
        this.catalog = catalog;
        this.metaData = metaData;
        this.caption = caption;
        this.fact = fact;
        this.hierarchyUsages = new ArrayList<>();
        this.context = context;

        if (getFact() != null && this instanceof RolapPhysicalCube) {
            this.star = catalog.getRolapStarRegistry().getOrCreateStar(getFact());
            // only set if different from default (so that if two cubes share
            // the same fact table, either can turn off caching and both are
            // effected).
            if (! isCache) {
                star.setCacheAggregations(isCache);
            }
        }

        RolapDimension measuresDimension =
            new RolapDimension(
                catalog,
                Dimension.MEASURES_NAME,
                null,
                true,
                null,
                DimensionType.MEASURES_DIMENSION,
                OlapMetaData.empty());

        this.dimensions.add(measuresDimension);

        this.measuresHierarchy =
            measuresDimension.newHierarchy(null, false, null);
        hierarchyList.add(measuresHierarchy);

        if (!Util.isEmpty(catalogMapping.getMeasuresDimensionName())) {
            measuresDimension.setCaption(catalogMapping.getMeasuresDimensionName());
            this.measuresHierarchy.setCaption(catalogMapping.getMeasuresDimensionName());
        }

        for (int i = 0; i < dimensions.size(); i++) {
        	org.eclipse.daanse.rolap.mapping.model.DimensionConnector mappingCubeDimension = dimensions.get(i);

            // Look up usages of shared dimensions in the schema before
            // consulting the XML schema (which may be null).
            RolapCubeDimension dimension =
                getOrCreateDimension(
                    mappingCubeDimension, catalog, catalogMapping, i + 1, hierarchyList);
            if (getLogger().isDebugEnabled()) {
                String msg = new StringBuilder("RolapCube<init>: dimension=").append(dimension.getName()).toString();
                getLogger().debug(msg);
            }
            this.dimensions.add(dimension);

            if (getFact() != null && this instanceof RolapPhysicalCube) {
                createUsages(dimension, mappingCubeDimension);
            }

            // the register Dimension call was moved here
            // to keep the RolapStar in sync with the realiasing
            // within the RolapCubeHierarchy objects.
            registerDimension(dimension);
        }
    }

    public List<Formula> getCalculatedMemberList() {
        return calculatedMemberList;
    }

    public List<Formula> getNamedSetList() {
        return this.namedSetList;
    }

    public void setStar(RolapStar star) {
        this.star = star;
    }

    protected void setClosureColumnBitKey(BitKey closureColumnBitKey) {
        this.closureColumnBitKey = closureColumnBitKey;
    }
    /**
     * Returns this cube's fact table, null if the cube is virtual.
     */
    public org.eclipse.daanse.rolap.mapping.model.RelationalQuery getFact() {
        return fact;
    }

    public void setFact(org.eclipse.daanse.rolap.mapping.model.RelationalQuery fact) {
        this.fact = fact;
    }

    /**
     * Returns the system measure that counts the number of fact table rows in
     * a given cell.
     *
     * Never null, because if there is no count measure explicitly defined,
     * the system creates one.
     */
    public RolapMeasure getFactCountMeasure() {
        return factCountMeasure;
    }

    /**
     * Returns the system measure that counts the number of atomic cells in
     * a given cell.
     *
     * A cell is atomic if all dimensions are at their lowest level.
     * If the fact table has a primary key, this measure is equivalent to the
     * {@link #getFactCountMeasure() fact count measure}.
     */
    public RolapMeasure getAtomicCellCountMeasure() {
        // TODO: separate measure
        return factCountMeasure;
    }

    public BitKey getClosureColumnBitKey() {
		return closureColumnBitKey;
	}
    
    private void fillKpiIfExist(org.eclipse.daanse.rolap.mapping.model.Cube cube) {
        if (cube != null && cube.getKpis() != null) {
            cube.getKpis().stream().forEach(kpiMapping -> {
                RolapKPI kpi = new RolapKPI();
                kpi.setName(kpiMapping.getName());
                kpi.setDisplayFolder(kpiMapping.getDisplayFolder());
                kpi.setCurrentTimeMember(kpiMapping.getCurrentTimeMember());
                kpi.setTrend(kpiMapping.getTrend());
                kpi.setWeight(kpiMapping.getWeight());
                kpi.setTrendGraphic(kpiMapping.getTrendGraphic());
                kpi.setStatusGraphic(kpiMapping.getStatusGraphic());
                kpi.setValue(kpiMapping.getValue());
                kpi.setGoal(kpiMapping.getGoal());
                kpi.setStatus(kpiMapping.getStatus());
                kpi.setDescription(kpiMapping.getDescription());
                kpis.add(kpi);
            });
            cube.getKpis().stream().forEach(kpiMapping -> {
                if (kpiMapping.getParentKpi() != null) {
                    Optional<KPI> oKpi = kpis.stream().filter(k -> k.getName().equals(kpiMapping.getName())).findFirst();
                    if (oKpi.isPresent()) {
                        Optional<KPI> oKpiParent = kpis.stream().filter(k -> k.getName().equals(kpiMapping.getParentKpi().getName())).findFirst();
                        if (oKpiParent.isPresent()) {
                            ((RolapKPI)oKpi.get()).setParentKpi(oKpiParent.get());
                        }
                    }
                }
            });
        }
    }

    /**
     * Makes sure that the schemaReader cache is invalidated.
     * Problems can occur if the measure hierarchy member reader is out
     * of sync with the cache.
     *
     * @param memberReader new member reader for measures hierarchy
     */
    public void setMeasuresHierarchyMemberReader(MemberReader memberReader) {
        this.measuresHierarchy.setMemberReader(memberReader);
        // this invalidates any cached schema reader
        this.schemaReader = null;
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

    @Override
    public MetaData getMetaData() {
        return metaData;
    }

    public boolean hasAggGroup() {
        return aggGroup != null;
    }

    public ExplicitRules.Group getAggGroup() {
        return aggGroup;
    }

    void loadAggGroup(org.eclipse.daanse.rolap.mapping.model.PhysicalCube mappingCube) {
        aggGroup = ExplicitRules.Group.make(this, mappingCube);
    }

    /**
     * Creates a dimension from its XML definition. If the XML definition is
     * a &lt;DimensionUsage&gt;, and the shared dimension is cached in the
     * schema, returns that.
     *
     * @param mappingCubeDimension XML Dimension or DimensionUsage
     * @param schema Schema
     * @param mappingSchema XML Schema
     * @param dimensionOrdinal Ordinal of dimension
     * @param cubeHierarchyList List of hierarchies in cube
     * @return A dimension
     */
    private RolapCubeDimension getOrCreateDimension(
    		org.eclipse.daanse.rolap.mapping.model.DimensionConnector mappingCubeDimension,
        RolapCatalog schema,
        org.eclipse.daanse.rolap.mapping.model.Catalog mappingSchema,
        int dimensionOrdinal,
        List<RolapHierarchy> cubeHierarchyList)
    {
        RolapDimension dimension = null;

        final RolapHierarchy sharedHierarchy = schema.getSharedHierarchy(mappingCubeDimension.getDimension());
        if (sharedHierarchy != null) {
            dimension =
                (RolapDimension) sharedHierarchy.getDimension();
        }


        if (dimension == null) {
        	org.eclipse.daanse.rolap.mapping.model.Dimension mappingDimension = mappingCubeDimension.getDimension();
            if (mappingDimension == null) {
            	if (mappingCubeDimension.getPhysicalCube() != null) { //for virtual cube
            		mappingDimension = DimensionUtil.getDimension(mappingCubeDimension.getPhysicalCube(), mappingSchema, mappingCubeDimension.getOverrideDimensionName());
            	}
            }
            dimension =
            		new RolapDimension(
            				schema, this, mappingDimension, mappingCubeDimension);
        }

        // wrap the shared or regular dimension with a
        // rolap cube dimension object
        return new RolapCubeDimension(
            this, dimension, mappingCubeDimension,
            RolapDimension.getDimensionName(mappingCubeDimension), dimensionOrdinal,
            cubeHierarchyList);
    }

    /**
     * Adds a collection of calculated members and named sets to this cube.
     * The members and sets can refer to each other.
     *
     * @param list XML objects representing members
     * @param mappingNamedSets Array of XML definition of named set
     * @param memberList Output list of org.eclipse.daanse.olap.api.element.Member objects
     * @param cube the cube that the calculated members originate from
     * @param errOnDups throws an error if a duplicate member is found
     */
    public void createCalcMembersAndNamedSets(
    	List<? extends org.eclipse.daanse.rolap.mapping.model.CalculatedMember> list,
        List<? extends org.eclipse.daanse.rolap.mapping.model.NamedSet> mappingNamedSets,
        List<RolapMember> memberList,
        RolapCube cube,
        boolean errOnDups)
    {
        final Query queryExp =
            resolveCalcMembers(
                list,
                mappingNamedSets,
                cube,
                errOnDups);
        if (queryExp == null) {
            return;
        }

        // Now pick through the formulas.
        Util.assertTrue(
            queryExp.getFormulas().length
            == list.size() + mappingNamedSets.size());
        for (int i = 0; i < list.size(); i++) {
            postCalcMember(list, i, queryExp, memberList);
        }
        for (int i = 0; i < mappingNamedSets.size(); i++) {
            postNamedSet(
                mappingNamedSets, list.size(), i, queryExp);
        }
    }

    protected Query resolveCalcMembers(
    	List<? extends org.eclipse.daanse.rolap.mapping.model.CalculatedMember> list,
        List<? extends org.eclipse.daanse.rolap.mapping.model.NamedSet> mappingNamedSets,
        RolapCube cube,
        boolean errOnDups)
    {
        // If there are no objects to create, our generated SQL will be so
        // silly, the parser will laugh.
        if (list.isEmpty() && mappingNamedSets.isEmpty()) {
            return null;
        }

        StringBuilder buf = new StringBuilder(256);
        buf.append("WITH").append(Util.NL);

        // Check the members individually, and generate SQL.
        final Set<String> fqNames = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            preCalcMember(list, i, buf, cube, errOnDups, fqNames);
        }

        // Check the named sets individually (for uniqueness) and generate SQL.
        Set<String> nameSet = new HashSet<>();
        for (Formula namedSet : namedSetList) {
            nameSet.add(namedSet.getName());
        }
        for (org.eclipse.daanse.rolap.mapping.model.NamedSet mappingNamedSet : mappingNamedSets) {
            preNamedSet(mappingNamedSet, nameSet, buf);
        }

        buf.append("SELECT FROM ").append(cube.getUniqueName());

        // Parse and validate this huge MDX query we've created.
        final String queryString = buf.toString();
        try {
            final Connection conn = catalog.getInternalConnection();
            StatementImpl statement = (StatementImpl) conn.getInternalStatement();
            ExecutionImpl execution = new ExecutionImpl(statement,
                    ExecuteDurationUtil.executeDurationValue(conn.getContext()));
            return ExecutionContext.where(execution.asContext(), () -> {
                final Query queryExp = conn.parseQuery(queryString);
                queryExp.resolve();
                return queryExp;
            });
        } catch (Exception e) {
            throw new OlapRuntimeException(MessageFormat.format(unknownNamedSetHasBadFormula, getName()), e);
        }
    }

    private void postNamedSet(
        List<? extends org.eclipse.daanse.rolap.mapping.model.NamedSet> mappingNamedSets,
        final int offset,
        int i,
        final Query queryExp)
    {
    	org.eclipse.daanse.rolap.mapping.model.NamedSet mappingNamedSet = mappingNamedSets.get(i);
//        discard(xmlNamedSet);
        Formula formula = queryExp.getFormulas()[offset + i];
        final SetBase namedSet = (SetBase) formula.getNamedSet();
        if (mappingNamedSet.getName() != null
            && mappingNamedSet.getName().length() > 0)
        {
            namedSet.setCaption(mappingNamedSet.getName());
        }

        if (mappingNamedSet.getDescription() != null
            && mappingNamedSet.getDescription().length() > 0)
        {
            namedSet.setDescription(mappingNamedSet.getDescription());
        }

        if (mappingNamedSet.getDisplayFolder() != null
                && mappingNamedSet.getDisplayFolder().length() > 0)
        {
            namedSet.setDisplayFolder(mappingNamedSet.getDisplayFolder());
        }

        namedSet.setMetadata(
            RolapMetaData.createMetaData(mappingNamedSet.getAnnotations()));

        namedSetList.add(formula);
    }

    private void preNamedSet(
    		org.eclipse.daanse.rolap.mapping.model.NamedSet mappingNamedSet,
        Set<String> nameSet,
        StringBuilder buf)
    {
        if (!nameSet.add(mappingNamedSet.getName())) {
            throw new OlapRuntimeException(MessageFormat.format(namedSetNotUnique,
                mappingNamedSet.getName(), getName()));
        }

        buf.append("SET ")
            .append(Util.makeFqName(mappingNamedSet.getName()))
            .append(Util.NL)
            .append(" AS ");
        Util.singleQuoteString(mappingNamedSet.getFormula(), buf);
        buf.append(Util.NL);
    }

    private void postCalcMember(
        List<? extends org.eclipse.daanse.rolap.mapping.model.CalculatedMember> mappingCalcMembers,
        int i,
        final Query queryExp,
        List<RolapMember> memberList)
    {
    	org.eclipse.daanse.rolap.mapping.model.CalculatedMember mappingCalcMember = mappingCalcMembers.get(i);

        final Formula formula = queryExp.getFormulas()[i];

        calculatedMemberList.add(formula);

        final RolapMember member = (RolapMember) formula.getMdxMember();

        Boolean visible = mappingCalcMember.isVisible();
        if (visible == null) {
            visible = Boolean.TRUE;
        }
        member.setProperty(StandardProperty.VISIBLE.getName(), visible);

        member.setProperty(StandardProperty.DISPLAY_FOLDER.getName(), mappingCalcMember.getDisplayFolder());

        if (mappingCalcMember.getName() != null
            && mappingCalcMember.getName().length() > 0)
        {
            member.setProperty(
                StandardProperty.CAPTION.getName(), mappingCalcMember.getName());
        }

        if (mappingCalcMember.getDescription() != null
            && mappingCalcMember.getDescription().length() > 0)
        {
            member.setProperty(
                StandardProperty.DESCRIPTION_PROPERTY.getName(), mappingCalcMember.getDescription());
        }

        if (getFormatString(mappingCalcMember) != null
            && getFormatString(mappingCalcMember).length() > 0)
        {
            member.setProperty(
                StandardProperty.FORMAT_STRING.getName(), getFormatString(mappingCalcMember));
        }

        final RolapMember member1 = RolapUtil.strip(member);
        ((RolapCalculatedMember) member1).setMetadata(
            RolapMetaData.createMetaData(mappingCalcMember.getAnnotations()));

        memberList.add(member);
    }

    private void preCalcMember(
    	List<? extends org.eclipse.daanse.rolap.mapping.model.CalculatedMember> list,
        int j,
        StringBuilder buf,
        RolapCube cube,
        boolean errOnDup,
        Set<String> fqNames)
    {
    	org.eclipse.daanse.rolap.mapping.model.CalculatedMember mappingCalcMember = list.get(j);

        // Lookup dimension
        Hierarchy hierarchy = null;
//        String dimName = null;
        if (mappingCalcMember.getHierarchy() == null) {
            hierarchy = measuresHierarchy;
        } else {
            // with new mapping
        	org.eclipse.daanse.rolap.mapping.model.Hierarchy hierarchyMappingOfCalcMember = mappingCalcMember.getHierarchy();
            hierarchy = hierarchyList.stream(
                    ).filter(h -> hierarchyMappingOfCalcMember.equals(h.hierarchyMapping))
                    .findAny().orElse(null);

        }
//        if (mappingCalcMember.getHierarchy() != null && mappingCalcMember.getHierarchy().getName() !=null) {
//
//            dimName = mappingCalcMember.getHierarchy().getName();
//            hierarchy = (Hierarchy)
//                getCatalogReader().withLocus().lookupCompound(
//                    this,
//                    Util.parseIdentifier(dimName),
//                    false,
//                    DataType.HIERARCHY);
//        }
        if (hierarchy == null) {
            throw new OlapRuntimeException(MessageFormat.format(calcMemberHasBadDimension,
                    mappingCalcMember.getHierarchy().getName(),   mappingCalcMember.getName(), getName()));
        }

        // Root of fully-qualified name.
        String parentFqName;
        if (mappingCalcMember.getParent() != null) {
            parentFqName = mappingCalcMember.getParent();
        } else {
            parentFqName = hierarchy.getUniqueNameSsas();
        }

        if (!hierarchy.getDimension().isMeasures()) {
            // Check if the parent exists.
            final OlapElement parent =
                Util.lookupCompound(
                    getCatalogReader().withLocus(),
                    this,
                    Util.parseIdentifier(parentFqName),
                    false,
                    DataType.UNKNOWN);

            if (parent == null) {
                throw new OlapRuntimeException(MessageFormat.format(
                    calcMemberHasUnknownParent,
                        parentFqName, mappingCalcMember.getName(), getName()));
            }

            if (parent.getHierarchy() != hierarchy) {
                throw  new OlapRuntimeException(MessageFormat.format(
                calcMemberHasDifferentParentAndHierarchy,
                    mappingCalcMember.getName(), getName(), hierarchy.getUniqueName()));
            }
        }

        // If we're processing a virtual cube, it's possible that we've
        // already processed this calculated member because it's
        // referenced in another measure; in that case, remove it from the
        // list, since we'll add it back in later; otherwise, in the
        // non-virtual cube case, throw an exception
        final String fqName = Util.makeFqName(parentFqName, mappingCalcMember.getName());
        for (int i = 0; i < calculatedMemberList.size(); i++) {
            Formula formula = calculatedMemberList.get(i);
            if (formula.getName().equals(mappingCalcMember.getName())
                && formula.getMdxMember().getHierarchy().equals(
                    hierarchy))
            {
                if (errOnDup) {
                    throw new CalcMemberNotUniqueException(
                        fqName,
                        getName());
                } else {
                    calculatedMemberList.remove(i);
                    --i;
                }
            }
        }

        // Check this calc member doesn't clash with one earlier in this
        // batch.
        if (!fqNames.add(fqName)) {
            throw new CalcMemberNotUniqueException(fqName, getName());
        }

        final List<? extends org.eclipse.daanse.rolap.mapping.model.CalculatedMemberProperty> mappingProperties =
                mappingCalcMember.getCalculatedMemberProperties();
        List<String> propNames = new ArrayList<>();
        List<String> propExprs = new ArrayList<>();
        validateMemberProps(
            mappingProperties, propNames, propExprs, mappingCalcMember.getName());

        final int measureCount =
            cube.measuresHierarchy.getMemberReader().getMemberCount();

        // Generate SQL.
        assert fqName.startsWith("[");
        buf.append("MEMBER ")
            .append(fqName)
            .append(Util.NL)
            .append("  AS ");
        Util.singleQuoteString(getFormula(mappingCalcMember), buf);

        if (mappingCalcMember.getCellFormatter() != null) {
            if (mappingCalcMember.getCellFormatter().getRef() != null) {
                propNames.add(StandardProperty.CELL_FORMATTER.getName());
                propExprs.add(
                    Util.quoteForMdx(mappingCalcMember.getCellFormatter().getRef()));
            }

            //no scripting
//            if (mappingCalcMember.getCellFormatter().script() != null) {
//                if (mappingCalcMember.getCellFormatter().script().language() != null) {
//                    propNames.add(Property.CELL_FORMATTER_SCRIPT_LANGUAGE.name);
//                    propExprs.add(
//                        Util.quoteForMdx(
//                            mappingCalcMember.getCellFormatter().script().language()));
//                }
//                propNames.add(Property.CELL_FORMATTER_SCRIPT.name);
//                propExprs.add(
//                    Util.quoteForMdx(mappingCalcMember.getCellFormatter().script().cdata()));
//            }
        }

        assert propNames.size() == propExprs.size();
//        processFormatStringAttribute(mappingCalcMember, buf);

        for (int i = 0; i < propNames.size(); i++) {
            String name = propNames.get(i);
            String expr = propExprs.get(i);
            buf.append(",").append(Util.NL);
            expr = removeSurroundingQuotesIfNumericProperty(name, expr);
            buf.append(name).append(" = ").append(expr);
        }
        // Flag that the calc members are defined against a cube; will
        // determine the value of Member.isCalculatedInQuery
        buf.append(",")
            .append(Util.NL);
        Util.quoteMdxIdentifier(StandardProperty.MEMBER_SCOPE.getName(), buf);
        buf.append(" = 'CUBE'");

        // Assign the member an ordinal higher than all of the stored measures.
        if (!propNames.contains(StandardProperty.MEMBER_ORDINAL.getName())) {
            buf.append(",")
                .append(Util.NL)
                .append(StandardProperty.MEMBER_ORDINAL)
                .append(" = ")
                .append(measureCount + j);
        }
        buf.append(Util.NL);
    }

    private String removeSurroundingQuotesIfNumericProperty(
        String name,
        String expr)
    {
        Property prop = StandardProperty.lookup(name, false);
        if (prop != null
            && prop.getType().isNumeric()
            && isSurroundedWithQuotes(expr)
            && expr.length() > 2)
        {
            return expr.substring(1, expr.length() - 1);
        }
        return expr;
    }

    private boolean isSurroundedWithQuotes(String expr) {
        return expr.startsWith("\"") && expr.endsWith("\"");
    }

    public void processFormatStringAttribute(
    		org.eclipse.daanse.rolap.mapping.model.CalculatedMember mappingCalcMember,
        StringBuilder buf)
    {
        if (getFormatString(mappingCalcMember) != null) {
            buf.append(",")
                .append(Util.NL)
                .append(StandardProperty.FORMAT_STRING.getName())
                .append(" = ")
                .append(Util.quoteForMdx(getFormatString(mappingCalcMember)));
        }
    }

    /**
     * Validates an array of member properties, and populates a list of names
     * and expressions, one for each property.
     *
     * @param list Array of property definitions.
     * @param propNames Output array of property names.
     * @param propExprs Output array of property expressions.
     * @param memberName Name of member which the properties belong to.
     */
    protected void validateMemberProps(
        final List<? extends org.eclipse.daanse.rolap.mapping.model.CalculatedMemberProperty> list,
        List<String> propNames,
        List<String> propExprs,
        String memberName)
    {
        if (list == null) {
            return;
        }
        for (org.eclipse.daanse.rolap.mapping.model.CalculatedMemberProperty mappingProperty : list) {
            if (mappingProperty.getExpression() == null && mappingProperty.getValue() == null) {
                throw  new OlapRuntimeException(MessageFormat.format(
                    neitherExprNorValueForCalcMemberProperty,
                        mappingProperty.getName(), memberName, getName()));
            }
            if (mappingProperty.getExpression() != null && mappingProperty.getValue() != null) {
                throw new OlapRuntimeException(
                    MessageFormat.format(exprAndValueForMemberProperty, mappingProperty.getName(), memberName, getName()));
            }
            propNames.add(mappingProperty.getName());
            if (mappingProperty.getExpression() != null) {
                propExprs.add(mappingProperty.getExpression());
            } else {
                propExprs.add(Util.quoteForMdx(mappingProperty.getValue()));
            }
        }
    }

    @Override
	public RolapCatalog getCatalog() {
        return catalog;
    }

    /**
     * Returns the named sets of this cube.
     */
    @Override
	public NamedSet[] getNamedSets() {
        NamedSet[] namedSetsArray = new NamedSet[namedSetList.size()];
        for (int i = 0; i < namedSetList.size(); i++) {
            namedSetsArray[i] = namedSetList.get(i).getNamedSet();
        }
        return namedSetsArray;
    }

    /**
     * Returns the schema reader which enforces the appropriate access-control
     * context. schemaReader is cached, and needs to stay in sync with
     * any changes to the cube.
     *
     *  return != null
     * @see #getCatalogReader(Role)
     */
    public synchronized CatalogReader getCatalogReader() {
        if (schemaReader == null) {
            schemaReader =
                new RolapCubeCatalogReader(context, RoleImpl.createRootRole(catalog), this);
        }
        return schemaReader;
    }

    @Override
	public CatalogReader getCatalogReader(Role role) {
        if (role == null) {
            return getCatalogReader();
        } else {
            return new RolapCubeCatalogReader(context, role, this);
        }
    }

    private org.eclipse.daanse.rolap.mapping.model.DimensionConnector lookup(
        List<? extends org.eclipse.daanse.rolap.mapping.model.DimensionConnector> mappingDimensions,
        String name)
    {
        for (org.eclipse.daanse.rolap.mapping.model.DimensionConnector cd : mappingDimensions) {
            if (name.equals(cd.getOverrideDimensionName())) {
                return cd;
            }
        }
        // TODO: this ought to be a fatal error.
        return null;
    }

    protected void init(List<? extends org.eclipse.daanse.rolap.mapping.model.DimensionConnector> mappingDimensions) {
        for (Dimension dimension1 : dimensions) {
            final RolapDimension dimension = (RolapDimension) dimension1;
            dimension.init(lookup(mappingDimensions, dimension.getName()));
        }
        register();
    }

    public void register() {
        if (this instanceof RolapVirtualCube) {
            return;
        }
        List<RolapBaseCubeMeasure> storedMeasures =
            new ArrayList<>();
        for (Member measure : getMeasures()) {
            if (measure instanceof RolapBaseCubeMeasure rolapBaseCubeMeasure) {
                storedMeasures.add(rolapBaseCubeMeasure);
            }
        }

        RolapStar starInner = getStar();
        RolapStar.Table table = starInner.getFactTable();

        // create measures (and stars for them, if necessary)
        for (RolapBaseCubeMeasure storedMeasure : storedMeasures) {
            table.makeMeasure(storedMeasure);
        }
    }

    /**
     * Returns true if this Cube is either virtual or if the Cube's
     * RolapStar is caching aggregates.
     *
     * @return Whether this Cube's RolapStar should cache aggregations
     */
    public boolean isCacheAggregations() {
        return this instanceof RolapVirtualCube || star.isCacheAggregations();
    }

    /**
     * Set if this (non-virtual) Cube's RolapStar should cache
     * aggregations.
     *
     * @param cache Whether this Cube's RolapStar should cache aggregations
     */
    public void setCacheAggregations(boolean cache) {
        if (this instanceof RolapPhysicalCube) {
            star.setCacheAggregations(cache);
        }
    }

    /**
     * Clear the in memory aggregate cache associated with this Cube, but
     * only if Disabling Caching has been enabled.
     */
    public void clearCachedAggregations() {
        clearCachedAggregations(false);
    }

    /**
     * Clear the in memory aggregate cache associated with this Cube.
     */
    public void clearCachedAggregations(boolean forced) {
        if (this instanceof RolapVirtualCube) {
            // TODO:
            // Currently a virtual cube does not keep a list of all of its
            // base cubes, so we need to iterate through each and flush
            // the ones that should be flushed. Could use a CacheControl
            // method here.
            for (RolapStar star1 : catalog.getRolapStarRegistry().getStars()) {
                // this will only flush the star's aggregate cache if
                // 1) DisableCaching is true or 2) the star's cube has
                // cacheAggregations set to false in the schema.
                star1.clearCachedAggregations(forced);
            }
        } else {
            star.clearCachedAggregations(forced);
        }
    }

    /**
     * Returns this cube's underlying star schema.
     */
    public RolapStar getStar() {
        if (this instanceof RolapPhysicalCube ) {
            if (star != null && star.getFactTable().getRelation().equals(getFact())) {
                return star;
            }
            star = catalog.getRolapStarRegistry().makeRolapStar(getFact());
        }
        return star;
    }

    private void createUsages(
        RolapCubeDimension dimension,
        org.eclipse.daanse.rolap.mapping.model.DimensionConnector mappingCubeDimension)
    {
        // RME level may not be in all hierarchies
        // If one uses the DimensionUsage attribute "level", which level
        // in a hierarchy to join on, and there is more than one hierarchy,
        // then a HierarchyUsage can not be created for the hierarchies
        // that do not have the level defined.
        List<RolapCubeHierarchy> hierarchies =
            (List<RolapCubeHierarchy>) dimension.getHierarchies();

        if (hierarchies.size() == 1) {
            // Only one, so let lower level error checking handle problems
            createUsage(hierarchies.getFirst(), mappingCubeDimension);

        } else if (mappingCubeDimension.getLevel() != null) {
            int cnt = 0;

            for (RolapCubeHierarchy hierarchy : hierarchies) {
                if (getLogger().isDebugEnabled()) {
                    String msg = new StringBuilder("RolapCube<init>: hierarchy=")
                        .append(hierarchy.getName()).toString();
                    getLogger().debug(msg);
                }
                RolapLevel joinLevel = (RolapLevel)
                    Util.lookupHierarchyLevel(hierarchy, mappingCubeDimension.getLevel().getName());
                if (joinLevel == null) {
                    continue;
                }
                createUsage(hierarchy, mappingCubeDimension);
                cnt++;
            }

            if (cnt == 0) {
                // None of the hierarchies had the level, let lower level
                // detect and throw error
                createUsage(hierarchies.getFirst(), mappingCubeDimension);
            }

        } else {
            // just do it
            for (RolapCubeHierarchy hierarchy : hierarchies) {
                if (getLogger().isDebugEnabled()) {
                    String msg = new StringBuilder("RolapCube<init>: hierarchy=")
                        .append(hierarchy.getName()).toString();
                    getLogger().debug(msg);
                }
                createUsage(hierarchy, mappingCubeDimension);
            }
        }
    }

    synchronized void createUsage(
        RolapCubeHierarchy hierarchy,
        org.eclipse.daanse.rolap.mapping.model.DimensionConnector cubeDim)
    {
        HierarchyUsage usage = new HierarchyUsage(this, hierarchy, cubeDim);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "RolapCube.createUsage: cube={}, hierarchy={}, usage={}",
                getName(), hierarchy.getName(), usage);
        }
        for (HierarchyUsage hierUsage : hierarchyUsages) {
            if (hierUsage.equals(usage)) {
                String msg = new StringBuilder("RolapCube.createUsage: duplicate ").append(hierUsage).toString();
                getLogger().warn(msg);
                return;
            }
        }
        if (getLogger().isDebugEnabled()) {
            String msg = new StringBuilder("RolapCube.createUsage: register ").append(usage).toString();
            getLogger().debug(msg);
        }
        this.hierarchyUsages.add(usage);
    }

    private synchronized HierarchyUsage getUsageByName(String name) {
        for (HierarchyUsage hierUsage : hierarchyUsages) {
            if (hierUsage.getFullName().equals(name)) {
                return hierUsage;
            }
        }
        return null;
    }

    /**
     * A Hierarchy may have one or more HierarchyUsages. This method returns
     * an array holding the one or more usages associated with a Hierarchy.
     * The HierarchyUsages hierarchyName attribute always equals the name
     * attribute of the Hierarchy.
     *
     * @param hierarchy Hierarchy
     * @return an HierarchyUsages array with 0 or more members.
     */
    public synchronized HierarchyUsage[] getUsages(Hierarchy hierarchy) {
        String name = hierarchy.getName();
        if (!name.equals(hierarchy.getDimension().getName()))
        {
            name = new StringBuilder(hierarchy.getDimension().getName()).append(".").append(name).toString();
        }
        if (getLogger().isDebugEnabled()) {
            String msg = new StringBuilder("RolapCube.getUsages: name=").append(name).toString();
            getLogger().debug(msg);
        }

        HierarchyUsage hierUsage = null;
        List<HierarchyUsage> list = null;

        for (HierarchyUsage hu : hierarchyUsages) {
            if (hu.getHierarchyName().equals(name)) {
                if (list != null) {
                    if (getLogger().isDebugEnabled()) {
                        getLogger().debug(
                            new StringBuilder("RolapCube.getUsages: ")
                            .append("add list HierarchyUsage.name=").append(hu.getName()).toString());
                    }
                    list.add(hu);
                } else if (hierUsage == null) {
                    hierUsage = hu;
                } else {
                    list = new ArrayList<>();
                    if (getLogger().isDebugEnabled()) {
                        getLogger().debug(
                            new StringBuilder("RolapCube.getUsages: ")
                            .append("add list hierUsage.name=")
                                .append(hierUsage.getName())
                                .append(", hu.name=")
                                .append(hu.getName()).toString());
                    }
                    list.add(hierUsage);
                    list.add(hu);
                    hierUsage = null;
                }
            }
        }
        if (hierUsage != null) {
            return new HierarchyUsage[] { hierUsage };
        } else if (list != null) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("RolapCube.getUsages: return list");
            }
            return list.toArray(new HierarchyUsage[list.size()]);
        } else {
            return new HierarchyUsage[0];
        }
    }

    public synchronized HierarchyUsage getFirstUsage(Hierarchy hier) {
        HierarchyUsage hierarchyUsage = firstUsageMap.get(hier);
        if (hierarchyUsage == null) {
            HierarchyUsage[] hierarchyUsagesInner = getUsages(hier);
            if (hierarchyUsagesInner.length != 0) {
                hierarchyUsage = hierarchyUsagesInner[0];
                firstUsageMap.put(hier, hierarchyUsage);
            }
        }
        return hierarchyUsage;
    }

    /**
     * Looks up all of the HierarchyUsages with the same "source" returning
     * an array of HierarchyUsage of length 0 or more.
     *
     * This method is currently only called if an error occurs in lookupChild(),
     * so that more information can be displayed in the error log.
     *
     * @param source Name of shared dimension
     * @return array of HierarchyUsage (HierarchyUsage[]) - never null.
     */
    private synchronized HierarchyUsage[] getUsagesBySource(String source) {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("RolapCube.getUsagesBySource: source={}", source);
        }

        HierarchyUsage hierUsage = null;
        List<HierarchyUsage> list = null;

        for (HierarchyUsage hu : hierarchyUsages) {
            String s = hu.getSource();
            if ((s != null) && s.equals(source)) {
                if (list != null) {
                    if (getLogger().isDebugEnabled()) {
                        getLogger().debug(
                            new StringBuilder("RolapCube.getUsagesBySource: ")
                                .append("add list HierarchyUsage.name=")
                                .append(hu.getName()).toString());
                    }
                    list.add(hu);
                } else if (hierUsage == null) {
                    hierUsage = hu;
                } else {
                    list = new ArrayList<>();
                    if (getLogger().isDebugEnabled()) {
                        getLogger().debug(
                            new StringBuilder("RolapCube.getUsagesBySource: ")
                                .append("add list hierUsage.name=")
                                .append(hierUsage.getName())
                                .append(", hu.name=")
                                .append(hu.getName()).toString());
                    }
                    list.add(hierUsage);
                    list.add(hu);
                    hierUsage = null;
                }
            }
        }
        if (hierUsage != null) {
            return new HierarchyUsage[] { hierUsage };
        } else if (list != null) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("RolapCube.getUsagesBySource: return list");
            }
            return list.toArray(new HierarchyUsage[list.size()]);
        } else {
            return new HierarchyUsage[0];
        }
    }


    /**
     * Understand this and you are no longer a novice.
     *
     * @param dimension Dimension
     */
    void registerDimension(RolapCubeDimension dimension) {
        RolapStar starInner = getStar();

        List<? extends Hierarchy> hierarchies = dimension.getHierarchies();

        for (Hierarchy hierarchy1 : hierarchies) {
            RolapHierarchy hierarchy = (RolapHierarchy) hierarchy1;

            org.eclipse.daanse.rolap.mapping.model.Query relation = hierarchy.getRelation();
            if (relation == null) {
                continue; // e.g. [Measures] hierarchy
            }
            List<RolapCubeLevel> levels = (List<RolapCubeLevel>) hierarchy.getLevels();

            HierarchyUsage[] hierarchyUsagesInner = getUsages(hierarchy);
            if (hierarchyUsagesInner.length == 0) {
                if (getLogger().isDebugEnabled()) {
                    StringBuilder buf = new StringBuilder(64);
                    buf.append("RolapCube.registerDimension: ");
                    buf.append("hierarchyUsages == null for cube=\"");
                    buf.append(this.name);
                    buf.append("\", hierarchy=\"");
                    buf.append(hierarchy.getName());
                    buf.append("\"");
                    getLogger().debug(buf.toString());
                }
                continue;
            }

            for (HierarchyUsage hierarchyUsage : hierarchyUsagesInner) {
                String usagePrefix = hierarchyUsage.getUsagePrefix();
                RolapStar.Table table = starInner.getFactTable();

                String levelName = hierarchyUsage.getLevelName();

                // RME
                // If a DimensionUsage has its level attribute set, then
                // one wants joins to occur at that level and not below (not
                // at a finer level), i.e., if you have levels: Year, Quarter,
                // Month, and Day, and the level attribute is set to Month, the
                // you do not want aggregate joins to include the Day level.
                // By default, it is the lowest level that the fact table
                // joins to, the Day level.
                // To accomplish this, we reorganize the relation and then
                // copy it (so that elsewhere the original relation can
                // still be used), and finally, clip off those levels below
                // the DimensionUsage level attribute.
                // Note also, if the relation (Relation) is not
                // a Join, i.e., the dimension is not a snowflake,
                // there is a single dimension table, then this is currently
                // an unsupported configuation and all bets are off.
                if (relation instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery) {
                    // RME
                    // take out after things seem to be working
                	org.eclipse.daanse.rolap.mapping.model.Query relationTmp1 = relation;

                    relation = reorder(relation, levels);

                    if (relation == null && getLogger().isDebugEnabled()) {
                        getLogger().debug(
                            "RolapCube.registerDimension: after reorder relation==null");
                        String msg = new StringBuilder("RolapCube.registerDimension: reorder relationTmp1=")
                            .append(format(relationTmp1)).toString();
                        getLogger().debug(msg);
                    }
                }

                org.eclipse.daanse.rolap.mapping.model.Query relationTmp2 = relation;

                if (levelName != null) {
                    // When relation is a table, this does nothing. Otherwise
                    // it tries to arrange the joins so that the fact table
                    // in the RolapStar will be joining at the lowest level.
                    //

                    // Make sure the level exists
                    RolapLevel level =
                        RolapLevel.lookupLevel(levels, levelName);
                    if (level == null) {
                        StringBuilder buf = new StringBuilder(64);
                        buf.append("For cube \"");
                        buf.append(getName());
                        buf.append("\" and HierarchyUsage [");
                        buf.append(hierarchyUsage);
                        buf.append("], there is no level with given");
                        buf.append(" level name \"");
                        buf.append(levelName);
                        buf.append("\"");
                        throw Util.newInternal(buf.toString());
                    }

                    // If level has child, not the lowest level, then snip
                    // relation between level and its child so that
                    // joins do not include the lower levels.
                    // If the child level is null, then the DimensionUsage
                    // level attribute was simply set to the default, lowest
                    // level and we do nothing.
                    if (relation instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery) {
                        RolapLevel childLevel =
                            (RolapLevel) level.getChildLevel();
                        if (childLevel != null) {
                            String tableName = childLevel.getTableName();
                            if (tableName != null) {
                                relation = snip(relation, tableName);

                                if (relation == null
                                    && getLogger().isDebugEnabled())
                                {
                                    getLogger().debug(
                                        "RolapCube.registerDimension: after snip relation==null");
                                    String msg = new StringBuilder("RolapCube.registerDimension: snip relationTmp2=")
                                        .append(format(relationTmp2)).toString();
                                    getLogger().debug(msg);
                                }
                            }
                        }
                    }
                }

                // cube and dimension usage are in different tables
                if (relation != null && !Utils.equalsQuery(relation, table.getRelation())) {
                    // HierarchyUsage should have checked this.
                    if (hierarchyUsage.getForeignKey() == null) {
                        throw new OlapRuntimeException(MessageFormat.format(
                            hierarchyMustHaveForeignKey,
                                hierarchy.getName(), getName()));
                    }
                    // parameters:
                    //   fact table,
                    //   fact table foreign key,
                    org.eclipse.daanse.rolap.element.RolapColumn column =
                        new org.eclipse.daanse.rolap.element.RolapColumn(
                            table.getAlias(),
                            hierarchyUsage.getForeignKey().getName());
                    // parameters:
                    //   left column
                    //   right column
                    RolapStar.Condition joinCondition =
                        new RolapStar.Condition(
                            column,
                            hierarchyUsage.getJoinExp());

                    if (hierarchy.getHierarchyMapping() != null
                            && hierarchy.getHierarchyMapping().getPrimaryKey() != null
                            && hierarchy.getHierarchyMapping().getPrimaryKey()
                            .getTable() != null
                            && relation instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery join
                            && right(join) instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery tqm
                            && getAlias(tqm) != null
                            && getAlias(tqm)
                            .equals(
                                hierarchy.getHierarchyMapping().getPrimaryKey()
                              .getTable().getName()))
                    {
                        org.eclipse.daanse.rolap.mapping.model.JoinQuery newRelation = RolapMappingFactory.eINSTANCE.createJoinQuery();
                        org.eclipse.daanse.rolap.mapping.model.JoinedQueryElement leftElement = RolapMappingFactory.eINSTANCE.createJoinedQueryElement();
                        org.eclipse.daanse.rolap.mapping.model.JoinedQueryElement rightElement = RolapMappingFactory.eINSTANCE.createJoinedQueryElement();
                        
                        leftElement.setAlias(getRightAlias(join));
                        leftElement.setKey(join.getRight().getKey());
                        leftElement.setQuery(PojoUtil.copy(right(join)));

                        rightElement.setAlias(getLeftAlias(join));
                        rightElement.setKey(join.getLeft().getKey());
                        rightElement.setQuery(PojoUtil.copy(left(join)));

                        newRelation.setLeft(leftElement);
                        newRelation.setRight(rightElement);
                        
                        relation = newRelation;
                    }

                    table = table.addJoin(this, relation, joinCondition);
                }

                // The parent Column is used so that non-shared dimensions
                // which use the fact table (not a separate dimension table)
                // can keep a record of what other columns are in the
                // same set of levels.
                RolapStar.Column parentColumn = null;

                // RME
                // If the level name is not null, then we need only register
                // those columns for that level and above.
                if (levelName != null) {
                    for (RolapCubeLevel level : levels) {
                        if (level.getKeyExp() != null) {
                            parentColumn = makeColumns(
                                table, level, parentColumn, usagePrefix);
                        }
                        if (levelName.equals(level.getName())) {
                            break;
                        }
                    }
                } else {
                    // This is the normal case, no level attribute so register
                    // all columns.
                    for (RolapCubeLevel level : levels) {
                        if (level.getKeyExp() != null) {
                            parentColumn = makeColumns(
                                table, level, parentColumn, usagePrefix);
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a column to the appropriate table in the {@link RolapStar}.
     * Note that if the RolapLevel has a table attribute, then the associated
     * column needs to be associated with that table.
     */
    protected RolapStar.Column makeColumns(
        RolapStar.Table table,
        RolapCubeLevel level,
        RolapStar.Column parentColumn,
        String usagePrefix)
    {
        // If there is a table name, then first see if the table name is the
        // table parameter's name or alias and, if so, simply add the column
        // to that table. On the other hand, find the ancestor of the table
        // parameter and if found, then associate the new column with
        // that table.
        //
        // Lastly, if the ancestor can not be found, i.e., there is no table
        // with the level's table name, what to do.  Here we simply punt and
        // associated the new column with the table parameter which might
        // be an error. We do issue a warning in any case.
        String tableName = level.getTableName();
        if (tableName != null) {
            if (table.getAlias().equals(tableName)) {
                parentColumn = table.makeColumns(
                    this, level, parentColumn, usagePrefix);
            } else if (table.equalsTableName(tableName)) {
                parentColumn = table.makeColumns(
                    this, level, parentColumn, usagePrefix);
            } else {
                RolapStar.Table t = table.findAncestor(tableName);
                if (t != null) {
                    parentColumn = t.makeColumns(
                        this, level, parentColumn, usagePrefix);
                } else {
                    // Issue warning and keep going.
                    String msg = new StringBuilder("RolapCube.makeColumns: for cube \"")
                        .append(getName())
                        .append("\" the Level \"")
                        .append(level.getName())
                        .append("\" has a table name attribute \"")
                        .append(tableName)
                        .append("\" but the associated RolapStar does not")
                        .append(" have a table with that name.").toString();
                    getLogger().warn(msg);

                    parentColumn = table.makeColumns(
                        this, level, parentColumn, usagePrefix);
                }
            }
        } else {
            // level's expr is not a Column (this is used by tests)
            // or there is no table name defined
            parentColumn = table.makeColumns(
                this, level, parentColumn, usagePrefix);
        }

        return parentColumn;
    }

    // The following code deals with handling the DimensionUsage level attribute
    // and snowflake dimensions only.

    /**
     * Formats a {@link QueryMapping}, indenting
     * joins for readability.
     *
     * @param relation A table or a join
     */
    private static String format(org.eclipse.daanse.rolap.mapping.model.Query relation) {
        StringBuilder buf = new StringBuilder();
        format(relation, buf, "");
        return buf.toString();
    }

    private static void format(
        org.eclipse.daanse.rolap.mapping.model.Query relation,
        StringBuilder buf,
        String indent)
    {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery table) {
            buf.append(indent);
            buf.append(table.getTable().getName());
            if (table.getAlias() != null) {
                buf.append('(');
                buf.append(table.getAlias());
                buf.append(')');
            }
            buf.append(Util.NL);
        } else {
        	org.eclipse.daanse.rolap.mapping.model.JoinQuery join = (org.eclipse.daanse.rolap.mapping.model.JoinQuery) relation;
            String subindent = new StringBuilder(indent).append("  ").toString();

            buf.append(indent);
            buf.append(getLeftAlias(join));
            buf.append('.');
            buf.append(join.getLeft().getKey());
            buf.append('=');
            buf.append(getRightAlias(join));
            buf.append('.');
            buf.append(join.getRight().getKey());
            buf.append(Util.NL);
            format(left(join), buf, subindent);
            format(right(join), buf, indent);
        }
    }

    /**
     * Returns a list of all hierarchies in this cube, in order of dimension.
     *
     * TODO: Make this method return RolapCubeHierarchy, when the measures
     * hierarchy is a RolapCubeHierarchy.
     *
     * @return List of hierarchies
     */
    @Override
    public List<Hierarchy> getHierarchies() {
        return hierarchyList.stream().map(Hierarchy.class::cast).collect(Collectors.toList());
    }

    public boolean isLoadInProgress() {
        return getCatalog().getCatalogLoadDate() == null;
    }

    /**
     * Attempts to transform a {@link QueryMapping}
     * into the "canonical" form.
     *
     * What is the canonical form? It is only relevant
     * when the relation is a snowflake (nested joins), not simply a table.
     * The canonical form has lower levels to the left of higher levels (Day
     * before Month before Quarter before Year) and the nested joins are always
     * on the right side of the parent join.
     *
     * The canonical form is (using a Time dimension example):
     *
     *            |
     *    ----------------
     *    |             |
     *   Day      --------------
     *            |            |
     *          Month      ---------
     *                     |       |
     *                   Quarter  Year
     *
     *
     * When the relation looks like the above, then the fact table joins to the
     * lowest level table (the Day table) which joins to the next level (the
     * Month table) which joins to the next (the Quarter table) which joins to
     * the top level table (the Year table).
     *
     * This method supports the transformation of a subset of all possible
     * join/table relation trees (and anyone who whats to generalize it is
     * welcome to). It will take any of the following and convert them to
     * the canonical.
     *
     *            |
     *    ----------------
     *    |             |
     *   Year     --------------
     *            |            |
     *         Quarter     ---------
     *                     |       |
     *                   Month    Day
     *
     *                  |
     *           ----------------
     *           |              |
     *        --------------   Year
     *        |            |
     *    ---------     Quarter
     *    |       |
     *   Day     Month
     *
     *                  |
     *           ----------------
     *           |              |
     *        --------------   Day
     *        |            |
     *    ---------      Month
     *    |       |
     *   Year   Quarter
     *
     *            |
     *    ----------------
     *    |             |
     *   Day      --------------
     *            |            |
     *          Month      ---------
     *                     |       |
     *                   Quarter  Year
     *
     *
     *
     * In addition, at any join node, it can exchange the left and right
     * child relations so that the lower level depth is to the left.
     * For example, it can also transform the following:
     *
     *                |
     *         ----------------
     *         |              |
     *      --------------   Day
     *      |            |
     *    Month     ---------
     *              |       |
     *             Year   Quarter
     *
     * 
     * What it can not handle are cases where on both the left and right side of
     * a join there are child joins:
     *
     *                |
     *         ----------------
     *         |              |
     *      ---------     ----------
     *      |       |     |        |
     *    Month    Day   Year    Quarter
     *
     *                |
     *         ----------------
     *         |              |
     *      ---------     ----------
     *      |       |     |        |
     *    Year     Day   Month   Quarter
     *
     * 
     * When does this method do nothing? 1) when there are less than 2 levels,
     * 2) when any level does not have a table name, and 3) when for every table
     * in the relation there is not a level. In these cases, this method simply
     * return the original relation.
     *
     * @param relation A table or a join
     * @param levels Levels in hierarchy
     */
    private static org.eclipse.daanse.rolap.mapping.model.Query reorder(
        org.eclipse.daanse.rolap.mapping.model.Query relation,
        List<RolapCubeLevel> levels)
    {
        // Need at least two levels, with only one level theres nothing to do.
        if (levels.size() < 2) {
            return relation;
        }

        Map<String, RelNode> nodeMap = new HashMap<>();

        // Create RelNode in top down order (year -> day)
        for (int i = 0; i < levels.size(); i++) {
            RolapLevel level = levels.get(i);

            if (level.isAll()) {
                continue;
            }

            // this is the table alias
            String tableName = level.getTableName();
            if (tableName == null) {
                // punt, no table name
                return relation;
            }
            RelNode rnode = new RelNode(tableName, i);
            nodeMap.put(tableName, rnode);
        }
        if (! validateNodes(relation, nodeMap)) {
            return relation;
        }
        org.eclipse.daanse.rolap.mapping.model.Query relationImpl = copy(relation);

        // Put lower levels to the left of upper levels
        leftToRight(relationImpl, nodeMap);

        // Move joins to the right side
        topToBottom(relationImpl);

        return relationImpl;
    }

    /**
     * The map has to be validated against the relation because there are
     * certain cases where we do not want to (read: can not) do reordering, for
     * instance, when closures are involved.
     *
     * @param relation A table or a join
     * @param map Names of tables and {@link RelNode} pairs
     */
    private static boolean validateNodes(
        org.eclipse.daanse.rolap.mapping.model.Query relation,
        Map<String, RelNode> map)
    {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.RelationalQuery table) {
            RelNode relNode = RelNode.lookup(table, map);
            return (relNode != null);

        } else if (relation instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery join) {
            return validateNodes(left(join), map)
                && validateNodes(right(join), map);

        } else {
            throw Util.newInternal(BAD_RELATION_TYPE + relation);
        }
    }

    /**
     * Transforms the Relation moving the tables associated with
     * lower levels (greater level depth, i.e., Day is lower than Month) to the
     * left of tables with high levels.
     *
     * @param relation is a table or a join
     * @param map Names of tables and {@link RelNode} pairs
     */
    private static int leftToRight(
        org.eclipse.daanse.rolap.mapping.model.Query relation,
        Map<String, RelNode> map)
    {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.RelationalQuery table) {
            RelNode relNode = RelNode.lookup(table, map);
            // Associate the table with its RelNode!!!! This is where this
            // happens.
            relNode.setTable(table);

            return relNode.getDepth();

        } else if (relation instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery join) {
            int leftDepth = leftToRight((org.eclipse.daanse.rolap.mapping.model.Query)left(join), map);
            int rightDepth = leftToRight((org.eclipse.daanse.rolap.mapping.model.Query)right(join), map);

            // we want the right side to be less than the left
            if (rightDepth > leftDepth) {
                // switch
                String leftAlias = getLeftAlias(join);
                org.eclipse.daanse.rolap.mapping.model.Column leftKey = join.getLeft().getKey();
                org.eclipse.daanse.rolap.mapping.model.Query left = copy(left(join));
                org.eclipse.daanse.rolap.mapping.model.Query right = copy(right(join));
                join.getLeft().setAlias(getRightAlias(join));
                join.getLeft().setKey(join.getRight().getKey());
                changeLeftRight(join, right, left);
                join.getRight().setAlias(leftAlias);
                join.getRight().setKey(leftKey);
            }
            // Does not currently matter which is returned because currently we
            // only support structures where the left and right depth values
            // form an inclusive subset of depth values, that is, any
            // node with a depth value between the left or right values is
            // a child of this current join.
            return leftDepth;

        } else {
            throw Util.newInternal(BAD_RELATION_TYPE + relation);
        }
    }

    /**
     * Transforms so that all joins have a table as their left child and either
     * a table of child join on the right.
     *
     * @param relation A table or a join
     */
    private static void topToBottom(org.eclipse.daanse.rolap.mapping.model.Query relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery) {
            // nothing

        } else if (relation instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery join) {
            while (left(join) instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery leftJoin) {
                org.eclipse.daanse.rolap.mapping.model.JoinQuery jleft = leftJoin;
                org.eclipse.daanse.rolap.mapping.model.JoinQuery joinQuery = RolapMappingFactory.eINSTANCE.createJoinQuery();

                org.eclipse.daanse.rolap.mapping.model.JoinedQueryElement leftElement = RolapMappingFactory.eINSTANCE.createJoinedQueryElement();
                leftElement.setAlias(getLeftAlias(join));
                leftElement.setKey(join.getLeft().getKey());
                leftElement.setQuery(PojoUtil.copy(right(jleft)));

                org.eclipse.daanse.rolap.mapping.model.JoinedQueryElement rightElement = RolapMappingFactory.eINSTANCE.createJoinedQueryElement();
                rightElement.setAlias(getRightAlias(join));
                rightElement.setKey(join.getRight().getKey());
                rightElement.setQuery(PojoUtil.copy(right(join)));
                
                joinQuery.setLeft(leftElement);
                joinQuery.setRight(rightElement);
                
                changeLeftRight(join, copy(left(jleft)), joinQuery);
                org.eclipse.daanse.rolap.mapping.model.JoinedQueryElement right = join.getRight();
                org.eclipse.daanse.rolap.mapping.model.JoinedQueryElement left = join.getLeft();
                right.setAlias(getRightAlias(jleft));
                right.setKey((org.eclipse.daanse.rolap.mapping.model.PhysicalColumn) jleft.getRight().getKey());
                left.setAlias(getLeftAlias(jleft));
                left.setKey((org.eclipse.daanse.rolap.mapping.model.PhysicalColumn) jleft.getLeft().getKey());
            }
        }
    }



    /**
     * Takes a relation in canonical form and snips off the
     * the tables with the given tableName (or table alias). The matching table
     * only appears once in the relation.
     *
     * @param relation A table or a join
     * @param tableName Table name in relation
     */
    private static org.eclipse.daanse.rolap.mapping.model.Query snip(
        org.eclipse.daanse.rolap.mapping.model.Query relation,
        String tableName)
    {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery table) {
            // Return null if the table's name or alias matches tableName
            if ((table.getAlias() != null) && table.getAlias().equals(tableName)) {
                return null;
            } else {
                return table.getTable().getName().equals(tableName) ? null : table;
            }

        } else if (relation instanceof org.eclipse.daanse.rolap.mapping.model.JoinQuery join) {
            // snip left
        	org.eclipse.daanse.rolap.mapping.model.Query left = snip(left(join), tableName);
            if (left == null) {
                // left got snipped so return the right
                // (the join is no longer a join).
                return right(join);

            } else {
                // whatever happened on the left, save it
                changeLeftRight((org.eclipse.daanse.rolap.mapping.model.JoinQuery)copy(join), copy(left), copy(right(join)));

                // snip right
                org.eclipse.daanse.rolap.mapping.model.Query right = snip(right(join), tableName);
                if (right == null) {
                    // right got snipped so return the left.
                    return left(join);

                } else {
                    // save the right, join still has right and left children
                    // so return it.
                    changeLeftRight((org.eclipse.daanse.rolap.mapping.model.JoinQuery)copy(join), copy(left(join)), copy(right));
                    return join;
                }
            }


        } else {
            throw Util.newInternal(BAD_RELATION_TYPE + relation);
        }
    }

    @Override
	public Member[] getMembersForQuery(String query, List<Member> calcMembers) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the time hierarchy for this cube. If there is no time hierarchy,
     * throws.
     */
    @Override
    public Hierarchy getTimeHierarchy(String funName) {
        for (RolapHierarchy hierarchy : hierarchyList) {
            if (hierarchy.getDimension().getDimensionType()
                == DimensionType.TIME_DIMENSION)
            {
                return hierarchy;
            }
        }

        throw new OlapRuntimeException(MessageFormat.format(noTimeDimensionInCube, funName));
    }

    /**
     * Finds out non joining dimensions for this cube.
     * Useful for finding out non joining dimensions for a stored measure from
     * a base cube.
     *
     * @param tuple array of members
     * @return Set of dimensions that do not exist (non joining) in this cube
     */
    @Override
	public Set<Dimension> nonJoiningDimensions(Member[] tuple) {
        Set<Dimension> otherDims = new HashSet<>();
        for (Member member : tuple) {
            if (!member.isCalculated()) {
                otherDims.add(member.getDimension());
            }
        }
        return nonJoiningDimensions(otherDims);
    }

    /**
     * Finds out non joining dimensions for this cube.  Equality test for
     * dimensions is done based on the unique name. Object equality can't be
     * used.
     *
     * @param otherDims Set of dimensions to be tested for existence in this
     * cube
     * @return Set of dimensions that do not exist (non joining) in this cube
     */
    @Override
	public Set<Dimension> nonJoiningDimensions(Set<Dimension> otherDims) {
        List<? extends Dimension> baseCubeDimensions = getDimensions();
        Set<String>  baseCubeDimNames = new HashSet<>();
        for (Dimension baseCubeDimension : baseCubeDimensions) {
            baseCubeDimNames.add(baseCubeDimension.getUniqueName());
        }
        Set<Dimension> nonJoiningDimensions = new HashSet<>();
        for (Dimension otherDim : otherDims) {
            if (!baseCubeDimNames.contains(otherDim.getUniqueName())) {
                nonJoiningDimensions.add(otherDim);
            }
        }
        return nonJoiningDimensions;
    }

    @Override
	public List<Member> getMeasures() {
        Level measuresLevel = dimensions.getFirst().getHierarchies().getFirst().getLevels().getFirst();
        return getCatalogReader().getLevelMembers(measuresLevel, true);
    }

    protected abstract void logMessage();
    /**
     * Locates the base cube hierarchy for a particular virtual hierarchy.
     * If not found, return null. This may be converted to a map lookup
     * or cached in some way in the future to increase performance
     * with cubes that have large numbers of hierarchies
     *
     * @param hierarchy virtual hierarchy
     * @return base cube hierarchy if found
     */
    public RolapHierarchy findBaseCubeHierarchy(RolapHierarchy hierarchy) {
        for (int i = 0; i < getDimensions().size(); i++) {
            Dimension dimension = getDimensions().get(i);
            if (dimension.getName().equals(
                    hierarchy.getDimension().getName()))
            {
                for (Hierarchy hier : dimension.getHierarchies()) {
                    if (hier.getName().equals(hierarchy.getName())) {
                        return (RolapHierarchy)hier;
                    }
                }
            }
        }
        return null;
    }


    /**
     * Locates the base cube level for a particular virtual level.
     * If not found, return null. This may be converted to a map lookup
     * or cached in some way in the future to increase performance
     * with cubes that have large numbers of hierarchies and levels
     *
     * @param level virtual level
     * @return base cube level if found
     */
    public RolapCubeLevel findBaseCubeLevel(RolapLevel level) {
        if (virtualToBaseMap.containsKey(level)) {
            return virtualToBaseMap.get(level);
        }
        String levelDimName = level.getDimension().getName();
        String levelHierName = level.getHierarchy().getName();

        // Closures are not in the dimension list so we need special logic for
        // locating the level.
        //
        // REVIEW: jhyde, 2009/7/21: This may no longer be the case, and we may
        // be able to improve performance. RolapCube.hierarchyList now contains
        // all hierarchies, including closure hierarchies; and
        // RolapHierarchy.closureFor indicates the base hierarchy for a closure
        // hierarchy.

        boolean isClosure = false;
        String closDimName = null;
        String closHierName = null;
        if (levelDimName.endsWith("$Closure")) {
            isClosure = true;
            closDimName = levelDimName.substring(0, levelDimName.length() - 8);
            closHierName =
                levelHierName.substring(0, levelHierName.length() - 8);
        }

        for (Dimension dimension : getDimensions()) {
            final String dimensionName = dimension.getName();
            if (dimensionName.equals(levelDimName)
                || (isClosure && dimensionName.equals(closDimName)))
            {
                for (Hierarchy hier : dimension.getHierarchies()) {
                    final String hierarchyName = hier.getName();
                    if (hierarchyName.equals(levelHierName)
                        || (isClosure && hierarchyName.equals(closHierName)))
                    {
                        if (isClosure) {
                            final RolapCubeLevel baseLevel =
                                ((RolapCubeLevel)
                                    hier.getLevels().get(1)).getClosedPeer();
                            virtualToBaseMap.put(level, baseLevel);
                            return baseLevel;
                        }
                        for (Level lvl : hier.getLevels()) {
                            if (lvl.getName().equals(level.getName())) {
                                final RolapCubeLevel baseLevel =
                                    (RolapCubeLevel) lvl;
                                virtualToBaseMap.put(level, baseLevel);
                                return baseLevel;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
	public OlapElement lookupChild(
        CatalogReader schemaReader, Segment s, MatchType matchType)
    {
        if (!(s instanceof NameSegment nameSegment)) {
            return null;
        }
        // Note that non-exact matches aren't supported at this level,
        // so the matchType is ignored
        String status = null;
        OlapElement oe = null;
        if (matchType == MatchType.EXACT_SCHEMA) {
            oe = super.lookupChild(
                schemaReader, nameSegment, MatchType.EXACT_SCHEMA);
        } else {
            oe = super.lookupChild(
                schemaReader, nameSegment, MatchType.EXACT);
        }

        if (oe == null) {
            HierarchyUsage[] usages = getUsagesBySource(nameSegment.getName());
            if (usages.length > 0) {
                StringBuilder buf = new StringBuilder(64);
                buf.append("RolapCube.lookupChild: ");
                buf.append("In cube \"");
                buf.append(getName());
                buf.append("\" use of unaliased Dimension name \"");
                buf.append(nameSegment);
                if (usages.length == 1) {
                    // ERROR: this will work but is bad coding
                    buf.append("\" rather than the alias name ");
                    buf.append("\"");
                    buf.append(usages[0].getName());
                    buf.append("\" ");
                    String msg = buf.toString();
                    getLogger().error(msg);
                    throw new OlapRuntimeException(buf.toString());
                } else {
                    // ERROR: this is not allowed
                    buf.append("\" rather than one of the alias names ");
                    for (HierarchyUsage usage : usages) {
                        buf.append("\"");
                        buf.append(usage.getName());
                        buf.append("\" ");
                    }
                    String msg = buf.toString();
                    getLogger().error(msg);
                    throw new OlapRuntimeException(buf.toString());
                }
            }
        }

        if (getLogger().isDebugEnabled()) {
            if (!nameSegment.matches("Measures")) {
                HierarchyUsage hierUsage = getUsageByName(nameSegment.getName());
                if (hierUsage == null) {
                    status = "hierUsage == null";
                } else {
                    status =
                        "hierUsage == "
                        + (hierUsage.isShared() ? "shared" : "not shared");
                }
            }
            StringBuilder buf = new StringBuilder(64);
            buf.append("RolapCube.lookupChild: ");
            buf.append("name=");
            buf.append(getName());
            buf.append(", childname=");
            buf.append(nameSegment);
            if (status != null) {
                buf.append(", status=");
                buf.append(status);
            }
            if (oe == null) {
                buf.append(" returning null");
            } else {
                buf.append(" returning elementname=").append(oe.getName());
            }
            getLogger().debug(buf.toString());
        }

        return oe;
    }

    /**
     * Returns the the measures hierarchy.
     */
    public RolapHierarchy getMeasuresHierarchy() {
        return measuresHierarchy;
    }

    public List<RolapMember> getMeasuresMembers() {
        return measuresHierarchy.getMemberReader().getMembers();
    }



    /**
     * Creates a calculated member.
     *
     * The member will be called [{dimension name}].[{name}].
     *
     * Not for public use.
     *
     * @param hierarchy Hierarchy the calculated member belongs to
     * @param name Name of member
     * @param calc Compiled expression
     */
    public RolapMember createCalculatedMember(
        Hierarchy hierarchy,
        String name,
        Calc<?> calc
    )
    {
        final List<Segment> segmentList = new ArrayList<>(Util.parseIdentifier(hierarchy.getUniqueName()));
        segmentList.add(new IdImpl.NameSegmentImpl(name));
        final Formula formula = new FormulaImpl(
            new IdImpl(segmentList),
            createDummyExp(calc),
            new MemberProperty[0]);
        final Statement statement =
            catalog.getInternalConnection().getInternalStatement();
        try {
            final QueryImpl query =
                new QueryImpl(
                    statement,
                    this,
                    new Formula[] {formula},
                    new QueryAxisImpl[0],
                    null,
                    new CellProperty[0],
                    new Parameter[0],
                    false);
            query.createValidator().validate(formula);
            calculatedMemberList.add(formula);
            return (RolapMember) formula.getMdxMember();
        } finally {
            statement.close();
        }
    }

    @Override
    public void createNamedSet(
            Formula formula)
    {
        final Statement statement =
                catalog.getInternalConnection().getInternalStatement();
        try {
            final QueryImpl query =
                    new QueryImpl(
                            statement,
                            this,
                            new Formula[] {formula},
                            new QueryAxisImpl[0],
                            null,
                            new CellProperty[0],
                            new Parameter[0],
                            false);
            query.createValidator().validate(formula);
            namedSetList.add(formula);
        } finally {
            statement.close();
        }
    }

    public RolapMember createCalculatedMember(
            Formula formula)
    {
        final Statement statement =
                catalog.getInternalConnection().getInternalStatement();
        try {
            final QueryImpl query =
                    new QueryImpl(
                            statement,
                            this,
                            new Formula[] {formula},
                            new QueryAxisImpl[0],
                            null,
                            new CellProperty[0],
                            new Parameter[0],
                            false);
            query.createValidator().validate(formula);
            calculatedMemberList.add(formula);
            return (RolapMember) formula.getMdxMember();
        } finally {
            statement.close();
        }
    }

    /**
     * Creates an expression that compiles to a given compiled expression.
     *
     * Use this for synthetic expressions that do not correspond to anything
     * in an MDX parse tree, and just need to compile to a particular compiled
     * expression. The expression has minimal amounts of metadata, for example
     * type information, but the function has no name or description.
     *
     * @see org.eclipse.daanse.olap.util.type.TypeWrapperExp
     */
    static Expression createDummyExp(final Calc<?> calc) {
        OperationAtom functionAtom = new FunctionOperationAtom("dummy");

        FunctionMetaData functionMetaData = new FunctionMetaDataR(functionAtom, null,
                DataType.NUMERIC, new FunctionParameterR[] { });

        return new ResolvedFunCallImpl(
            new AbstractFunctionDefinition(functionMetaData) {
                @Override
                public Calc<?> compileCall(
                    ResolvedFunCall call, ExpressionCompiler compiler)
                {
                    return calc;
                }
            },
            new Expression[0],
            calc.getType());
    }

    /**Returns the list of base cubes associated with this cube
     * if this one is a virtual cube,
     * otherwise return just this cube
     *
     * @return the list of base cubes
     */
    public List<RolapCube> getBaseCubes() {
      if (baseCubes == null) {
        baseCubes = findBaseCubes(this);
      }
      return baseCubes;
    }

    /**
     * Locates all base cubes associated with the virtual cube.
     */
    private static List<RolapCube> findBaseCubes(RolapCube cube) {
      if (cube instanceof RolapPhysicalCube ) {
        return Collections.singletonList(cube);
      }
      List<RolapCube> cubesList = new ArrayList<>();
      Set<RolapCube> cubes = new TreeSet<>(new RolapCubeComparator());
      for (Member member : cube.getMeasures()) {
        if (member instanceof RolapStoredMeasure rolapStoredMeasure) {
          cubes.add(rolapStoredMeasure.getCube());
        } else if (member instanceof RolapHierarchy.RolapCalculatedMeasure rolapCalculatedMeasure) {
          RolapCube baseCube =
              rolapCalculatedMeasure.getBaseCube();
          if (baseCube != null) {
            cubes.add(baseCube);
          }
        }
      }
      cubesList.addAll(cubes);
      return cubesList;
    }

    public void flushCache(Connection rolapConnection) {
        //Data cache exists in connection context
        final CacheControl cacheControl = rolapConnection.getCacheControl(null);
        cacheControl.flush(cacheControl.createMeasuresRegion(this));


        for(RolapHierarchy rolapHierarchy: this.hierarchyList){
            if (rolapHierarchy instanceof RolapCubeHierarchy rolapCubeHierarchy) {
                MemberReader memberReader = rolapCubeHierarchy.getMemberReader();
                if(memberReader instanceof RolapCubeHierarchy.CacheRolapCubeHierarchyMemberReader crhmr) {
                    ((MemberCacheHelper)crhmr.getMemberCache()).flushCache();
                    crhmr.getRolapCubeMemberCacheHelper().flushCache();
                }

                RolapHierarchy sharedRolapHierarchy = rolapCubeHierarchy.getRolapHierarchy();
                memberReader = sharedRolapHierarchy.getMemberReader();
                if (memberReader instanceof SmartMemberReader smartMemberReader) {
                    final MemberCacheHelper memberCacheHelper = (MemberCacheHelper) smartMemberReader.getMemberCache();
                    memberCacheHelper.flushCache();
                }
            }
        }
    }

    @Override
    public RolapDrillThroughAction getDefaultDrillThroughAction() {
        for(OlapAction action: this.actionList) {
            if(action instanceof RolapDrillThroughAction rolapDrillThroughAction
                && rolapDrillThroughAction.getIsDefault()) {
                return rolapDrillThroughAction;
            }
        }
        return null;
    }

    @Override
    public List<DrillThroughAction> getDrillThroughActions() {
        List<DrillThroughAction> res = new ArrayList<>();
        for (AbstractRolapAction action : this.actionList) {
            if (action instanceof DrillThroughAction drillThroughAction) {
                res.add(drillThroughAction);
            }
        }
        return res;
    }

    @Override
    public List<Member> getLevelMembers(Level level, boolean includeCalculated) {
        return getCatalogReader().withLocus().getLevelMembers(level, true);
    }

    @Override
    public int getLevelCardinality(
        Level level, boolean approximate, boolean materialize){
        return getCatalogReader().withLocus().getLevelCardinality(level, approximate, materialize);
    }

    public Context getContext() {
        return context;
    }

    public Optional<RolapWritebackTable> getWritebackTable() {
        return writebackTable;
    }

    public void setWritebackTable(Optional<RolapWritebackTable> writebackTable) {
        this.writebackTable = writebackTable;
    }

	public Hierarchy lookupHierarchy(org.eclipse.daanse.rolap.mapping.model.Hierarchy hierarchy) {
        return hierarchyList.stream(
                ).filter(h -> hierarchy.equals(h.hierarchyMapping))
                .findAny().orElse(null);
	}

	public Level lookupLevel(org.eclipse.daanse.rolap.mapping.model.Level level, Hierarchy h) {
		if (level != null && h != null && h.getLevels() != null) {
			for (Level l : h.getLevels()) {
				if (l instanceof RolapLevel rl) {
					if (level.equals(rl.levelMapping)) {
						return rl;
					}
				}
			}
		}
		return null;
	}

	@Override
	public List<? extends KPI> getKPIs() {
		return kpis;
	}

	@Override
    public void modifyFact(List<Map<String, Entry<DataTypeJdbc, Object>>> sessionValues) {
		org.eclipse.daanse.rolap.mapping.model.RelationalQuery fact = getFact();
            restoreFact = fact;
            Optional<RolapWritebackTable> oWritebackTable = getWritebackTable();
            Dialect dialect = getContext().getDialect();
            if (oWritebackTable.isPresent()) {
                RolapWritebackTable writebackTable = oWritebackTable.get();
                if (getWritebackTable() != null && getWritebackTable().isPresent()) {
                    List<Map<String, Entry<Datatype, Object>>> rolapSessionValues = EnumConvertor.convertSessionValues(sessionValues);
                    if (fact instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery mappingTable) {
                        String alias = mappingTable.getAlias() != null ? mappingTable.getAlias() : mappingTable.getTable().getName();
                        StringBuilder sql = new StringBuilder("select ").append(writebackTable.getColumns().stream().map( c -> c.getColumn().getName() )
                        .collect(Collectors.joining(", "))).append(" from ").append(mappingTable.getTable().getName());
                        sql.append(getWriteBackSql(dialect, writebackTable, rolapSessionValues));
                        org.eclipse.daanse.rolap.mapping.model.SqlStatement sqlStatement = RolapMappingFactory.eINSTANCE.createSqlStatement();
                        sqlStatement.setSql(sql.toString());
                        sqlStatement.getDialects().add("generic");
                        sqlStatement.getDialects().add(dialect.getDialectName());
                        
                        org.eclipse.daanse.rolap.mapping.model.DatabaseSchema schema = RolapMappingFactory.eINSTANCE.createDatabaseSchema();
                        schema.setName(mappingTable.getTable().getSchema().getName());
                        org.eclipse.daanse.rolap.mapping.model.SqlView sqlView = RolapMappingFactory.eINSTANCE.createSqlView();
                        sqlView.getSqlStatements().add(sqlStatement);
                        sqlView.setSchema(schema);
                        org.eclipse.daanse.rolap.mapping.model.SqlSelectQuery sqlSelectQuery = RolapMappingFactory.eINSTANCE.createSqlSelectQuery();
                        sqlSelectQuery.setSql(sqlView);
                        sqlSelectQuery.setAlias(alias);
                        changeFact(sqlSelectQuery);
                    }
                    if (fact instanceof org.eclipse.daanse.rolap.mapping.model.InlineTableQuery mappingInlineTable) {
                    	List<String> columns =  writebackTable.getColumns().stream().map(c -> c.getColumn().getName()).toList();
                    	org.eclipse.daanse.rolap.mapping.model.RelationalQuery mappingRelation = RolapUtil.convertInlineTableToRelation(mappingInlineTable, getContext().getDialect(), columns);
                        if (mappingRelation instanceof org.eclipse.daanse.rolap.mapping.model.SqlSelectQuery mappingView) {
                            changeFact(mappingView, dialect, writebackTable, rolapSessionValues);
                        }
                    }
                    if (fact instanceof org.eclipse.daanse.rolap.mapping.model.SqlSelectQuery mappingView) {
                        changeFact(mappingView, dialect, writebackTable, rolapSessionValues);
                    }
                }
            }
    }

    private void changeFact(org.eclipse.daanse.rolap.mapping.model.SqlSelectQuery mappingView, Dialect dialect, RolapWritebackTable writebackTable, List<Map<String, Map.Entry<Datatype, Object>>> sessionValues) {
        if (mappingView.getSql() != null && mappingView.getSql().getSqlStatements() != null) {
            List<? extends org.eclipse.daanse.rolap.mapping.model.SqlStatement> statements = mappingView.getSql().getSqlStatements().stream()
                .map(sql -> {
                    SqlStatement sqlStatement = RolapMappingFactory.eINSTANCE.createSqlStatement();
                    sqlStatement.setSql(new StringBuilder(sql.getSql()).append(getWriteBackSql(dialect, writebackTable, sessionValues)).toString());
                    sqlStatement.getDialects().addAll(sql.getDialects());
                    return sqlStatement;
                })
                .toList();
            org.eclipse.daanse.rolap.mapping.model.SqlView sqlView = RolapMappingFactory.eINSTANCE.createSqlView();
            sqlView.getSqlStatements().addAll(statements);
            sqlView.setSchema(mappingView.getSql().getSchema());
            org.eclipse.daanse.rolap.mapping.model.SqlSelectQuery sqlSelectQuery = RolapMappingFactory.eINSTANCE.createSqlSelectQuery();
            sqlSelectQuery.setSql(sqlView);
            sqlSelectQuery.setAlias(mappingView.getAlias());
            changeFact(sqlSelectQuery);
        }
    }

    private void changeFact(org.eclipse.daanse.rolap.mapping.model.SqlSelectQuery sqls) {
        setFact(sqls);
        register();
    }

    private CharSequence getWriteBackSql(Dialect dialect, RolapWritebackTable writebackTable, List<Map<String, Map.Entry<Datatype, Object>>> sessionValues) {
        StringBuilder sql = new StringBuilder();
        sql.append(" union all select ");
        sql.append(writebackTable.getColumns().stream().map( c -> c.getColumn().getName() )
            .collect(Collectors.joining(", "))).append(" from ")
            .append(writebackTable.getName());
        if (sessionValues != null && !sessionValues.isEmpty()) {
            sql.append(dialect.generateUnionAllSql(sessionValues));
        }
        return sql;
    }


    @Override
    public void restoreFact() {
        if (restoreFact != null) {
            setFact(restoreFact);
            restoreFact = null;
            register();
        }

    }

    @Override
    public void commit(List<Map<String, Map.Entry<DataTypeJdbc, Object>>> sessionValues, String userId) {
        WritebackUtil.commit(this, catalog.getInternalConnection(), EnumConvertor.convertSessionValues(sessionValues), userId);
    }

    @Override
    public List<Map<String, Entry<DataTypeJdbc, Object>>> getAllocationValues(String tupleString, Object value, AllocationPolicy allocationPolicy) {
        return WritebackUtil.getAllocationValues(this,
                tupleString,
                value,
                allocationPolicy);
    }

}
