/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2018 Hitachi Vantara and others
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

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.genericExpression;
import static org.eclipse.daanse.rolap.common.util.LevelUtil.getPropertyExp;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.MatchType;
import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.DimensionType;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.LevelType;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.formatter.MemberPropertyFormatter;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.element.AbstractProperty;
import org.eclipse.daanse.olap.element.LevelBase;
import org.eclipse.daanse.olap.exceptions.NonTimeLevelInTimeHierarchyException;
import org.eclipse.daanse.olap.exceptions.TimeLevelInNonTimeHierarchyException;
import org.eclipse.daanse.olap.format.FormatterCreateContext;
import org.eclipse.daanse.olap.format.FormatterFactory;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.rolap.common.RolapSqlExpression;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.util.ExpressionUtil;
import org.eclipse.daanse.rolap.common.util.LevelUtil;
import org.eclipse.daanse.rolap.common.util.RelationUtil;
import org.eclipse.daanse.rolap.mapping.model.ColumnInternalDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RolapLevel implements {@link Level} for a ROLAP database.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public class RolapLevel extends LevelBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(RolapLevel.class);

    /**
     * The column or expression which yields the level's key.
     */
    protected SqlExpression keyExp;

    /**
     * The column or expression which yields the level's ordinal.
     */
    protected SqlExpression ordinalExp;

    /**
     * The column or expression which yields the level members' caption.
     */
    protected SqlExpression captionExp;

    private final Datatype datatype;

    private final int flags;

    public static final int FLAG_ALL = 0x02;

    /**
     * For SQL generator. Whether values of "column" are unique globally
     * unique (as opposed to unique only within the context of the parent
     * member).
     */
    public static final int FLAG_UNIQUE = 0x04;

    private RolapLevel closedPeerLevel;

    protected RolapProperty[] properties;
    private final RolapProperty[] inheritedProperties;

    /**
     * Ths expression which gives the name of members of this level. If null,
     * members are named using the key expression.
     */
    protected SqlExpression nameExp;
    /** The expression which joins to the parent member in a parent-child
     * hierarchy, or null if this is a regular hierarchy. */
    protected SqlExpression parentExp;
    /** Value which indicates a null parent in a parent-child hierarchy. */
    private final String nullParentValue;

    /** Condition under which members are hidden. */
    private final HideMemberCondition hideMemberCondition;
    protected final org.eclipse.daanse.rolap.mapping.model.ParentChildLink xmlClosure;
    private final MetaData metaData;
    private final BestFitColumnType internalType; // may be null

    private final boolean parentAsLeafEnable;
    private final String parentAsLeafNameFormat;
    protected org.eclipse.daanse.rolap.mapping.model.Level levelMapping;

    /**
     * Creates a level.
     *
     *  parentExp != null || nullParentValue == null
     *  properties != null
     *  levelType != null
     *  hideMemberCondition != null
     */
    public RolapLevel(
        RolapHierarchy hierarchy,
        String name,
        String caption,
        boolean visible,
        String description,
        int depth,
        SqlExpression keyExp,
        SqlExpression nameExp,
        SqlExpression captionExp,
        SqlExpression ordinalExp,
        SqlExpression parentExp,
        String nullParentValue,
        org.eclipse.daanse.rolap.mapping.model.ParentChildLink mappingClosure,
        RolapProperty[] properties,
        int flags,
        Datatype datatype,
        BestFitColumnType internalType,
        HideMemberCondition hideMemberCondition,
        LevelType levelType,
        String approxRowCount,
        MetaData metaData)
    {
        this(
                hierarchy,
                name,
                caption,
                visible,
                description,
                depth,
                keyExp,
                nameExp,
                captionExp,
                ordinalExp,
                parentExp,
                nullParentValue,
                mappingClosure,
                properties,
                flags,
                datatype,
                internalType,
                hideMemberCondition,
                levelType,
                approxRowCount,
                metaData, false, null);
    }

    /**
     * Creates a level.
     *
     *  parentExp != null || nullParentValue == null
     *  properties != null
     *  levelType != null
     *  hideMemberCondition != null
     */
    protected RolapLevel(
        RolapHierarchy hierarchy,
        String name,
        String caption,
        boolean visible,
        String description,
        int depth,
        SqlExpression keyExp,
        SqlExpression nameExp,
        SqlExpression captionExp,
        SqlExpression ordinalExp,
        SqlExpression parentExp,
        String nullParentValue,
        org.eclipse.daanse.rolap.mapping.model.ParentChildLink mappingClosure,
        RolapProperty[] properties,
        int flags,
        Datatype datatype,
        BestFitColumnType internalType,
        HideMemberCondition hideMemberCondition,
        LevelType levelType,
        String approxRowCount,
        MetaData metaData,
        boolean parentAsLeafEnable,
        String parentAsLeafNameFormat)
    {
        super(
            hierarchy, name, caption, visible, description, depth, levelType);
        assert metaData != null;
        Util.assertPrecondition(properties != null, "properties != null");
        Util.assertPrecondition(
            hideMemberCondition != null,
            "hideMemberCondition != null");
        Util.assertPrecondition(levelType != null, "levelType != null");

        if (keyExp instanceof org.eclipse.daanse.rolap.element.RolapColumn column) {
            checkColumn(column);
        }
        this.metaData = metaData;
        this.approxRowCount = loadApproxRowCount(approxRowCount);
        this.flags = flags;
        this.datatype = datatype;
        this.keyExp = keyExp;
        if (nameExp != null) {
            if (nameExp instanceof RolapColumn rc) {
                checkColumn(rc);
            }
        }
        this.nameExp = nameExp;
        if (captionExp != null) {
            if (captionExp instanceof RolapColumn rc) {
                checkColumn(rc);
            }
        }
        this.captionExp = captionExp;
        if (ordinalExp != null) {
            if (ordinalExp instanceof RolapColumn rc) {
                checkColumn(rc);
            }
            this.ordinalExp = ordinalExp;
        } else {
            this.ordinalExp = this.keyExp;
        }
        if (parentExp instanceof RolapColumn rc) {
            checkColumn(rc);
        }
        this.parentExp = parentExp;
        if (parentExp != null) {
            Util.assertTrue(
                !isAll(),
                new StringBuilder("'All' level '").append(this).append("' must not be parent-child").toString());
            Util.assertTrue(
                isUnique(),
                new StringBuilder("Parent-child level '").append(this)
                .append("' must have uniqueMembers=\"true\"").toString());
        }
        this.nullParentValue = nullParentValue;
        //Util.assertPrecondition(
        //    parentExp != null || nullParentValue == null,
        //    "parentExp != null || nullParentValue == null");
        this.xmlClosure = mappingClosure;
        for (RolapProperty property : properties) {
            if (property.getExp() instanceof org.eclipse.daanse.rolap.element.RolapColumn column) {
                checkColumn(column);
            }
        }
        this.properties = properties;
        this.parentAsLeafEnable = parentAsLeafEnable;
        this.parentAsLeafNameFormat = parentAsLeafNameFormat;
        List<Property> list = new ArrayList<>();
        for (Level level = this; level != null;
             level = level.getParentLevel())
        {
            final Property[] levelProperties = level.getProperties();
            for (final Property levelProperty : levelProperties) {
                Property existingProperty = lookupProperty(
                    list, levelProperty.getName());
                if (existingProperty == null) {
                    list.add(levelProperty);
                } else if (existingProperty.getType()
                    != levelProperty.getType())
                {
                    throw Util.newError(
                        new StringBuilder("Property ").append(this.getName()).append(".")
                        .append(levelProperty.getName()).append(" overrides a ")
                        .append("property with the same name but different type").toString());
                }
            }
        }
        this.inheritedProperties = list.toArray(new RolapProperty[list.size()]);

        Dimension dim = hierarchy.getDimension();
        if (dim.getDimensionType() == DimensionType.TIME_DIMENSION) {
            if (!levelType.isTime() && !isAll()) {
                throw new NonTimeLevelInTimeHierarchyException(getUniqueName());
            }
        } else if (dim.getDimensionType() == null) {
            // there was no dimension type assigned to the dimension
            // - check later
        } else {
            if (levelType.isTime()) {
                throw new TimeLevelInNonTimeHierarchyException(getUniqueName());
            }
        }
        this.internalType = internalType;
        this.hideMemberCondition = hideMemberCondition;
    }

    @Override
	public RolapHierarchy getHierarchy() {
        return (RolapHierarchy) hierarchy;
    }

    @Override
	public MetaData getMetaData() {
        return metaData;
    }

    private int loadApproxRowCount(String approxRowCount) {
        boolean notNullAndNumeric =
            approxRowCount != null
                && approxRowCount.matches("^\\d+$");
        if (notNullAndNumeric) {
            return Integer.parseInt(approxRowCount);
        } else {
            // if approxRowCount is not set, return MIN_VALUE to indicate
            return Integer.MIN_VALUE;
        }
    }

    @Override
	protected Logger getLogger() {
        return LOGGER;
    }

    String getTableName() {
        String tableName = null;

        SqlExpression expr = getKeyExp();
        if (expr instanceof RolapColumn mc) {
            tableName = ExpressionUtil.getTableAlias(mc);
        }
        return tableName;
    }

    public SqlExpression getKeyExp() {
        return keyExp;
    }

    @Override
    public boolean isParentAsLeafEnable() {
        return parentAsLeafEnable;
    }

    @Override
    public String getParentAsLeafNameFormat() {
        return parentAsLeafNameFormat;
    }

    @Override
    public SqlExpression getOrdinalExp() {
        return ordinalExp;
    }

    public SqlExpression getCaptionExp() {
        return captionExp;
    }

    public boolean hasCaptionColumn() {
        return captionExp != null;
    }

    public boolean hasOrdinalExp() {
      return getOrdinalExp() != null && !getOrdinalExp().equals(getKeyExp());
    }

    final int getFlags() {
        return flags;
    }

    public HideMemberCondition getHideMemberCondition() {
        return hideMemberCondition;
    }

    public final boolean isUnique() {
        return (flags & FLAG_UNIQUE) != 0;
    }

    public final Datatype getDatatype() {
        return datatype;
    }

    public final String getNullParentValue() {
        return nullParentValue;
    }

    /**
     * Returns whether this level is parent-child.
     */
    @Override
    public boolean isParentChild() {
        return parentExp != null;
    }

    public SqlExpression getParentExp() {
        return parentExp;
    }

    // RME: this has to be public for two of the DrillThroughTest test.
    public SqlExpression getNameExp() {
        return nameExp;
    }

    private Property lookupProperty(List<Property> list, String propertyName) {
        for (Property property : list) {
            if (property.getName().equals(propertyName)) {
                return property;
            }
        }
        return null;
    }

    public RolapLevel(
            String name,
            RolapSqlExpression parentExp,
            String nullParentValue,
            org.eclipse.daanse.rolap.mapping.model.ParentChildLink parentChildLink,
            RolapHierarchy hierarchy,
            int depth,
            boolean parentAsLeafEnable,
            String parentAsLeafNameFormat,
            org.eclipse.daanse.rolap.mapping.model.Level mappingLevel)
    {

        this(
            hierarchy,
            name,
            name,
            mappingLevel.isVisible(),
            mappingLevel.getDescription(),
            depth,
            LevelUtil.getKeyExp(mappingLevel),
            LevelUtil.getNameExp(mappingLevel),
            LevelUtil.getCaptionExp(mappingLevel),
            LevelUtil.getOrdinalExp(mappingLevel),
            parentExp,
            nullParentValue,
            parentChildLink,
            createProperties(mappingLevel),
            (mappingLevel.isUniqueMembers() ? FLAG_UNIQUE : 0),
            getType(mappingLevel),
            null,
            //toInternalType(mappingLevel.getDataType().getValue()),
            HideMemberCondition.fromValue(mappingLevel.getHideMemberIf().getLiteral()),
            LevelType.fromValue(
                "TimeHalfYear".equals(mappingLevel.getType().getLiteral())
                    ? "TimeHalfYears"
                    : mappingLevel.getType().getLiteral()),
            mappingLevel.getApproxRowCount(),
            RolapMetaData.createMetaData(mappingLevel.getAnnotations()),
            parentAsLeafEnable, parentAsLeafNameFormat);

        setLevelInProperties();
        if (!Util.isEmpty(mappingLevel.getName())) {
            setCaption(getName());
        }

        FormatterCreateContext memberFormatterContext =
            new FormatterCreateContext.Builder(getUniqueName())
                .formatterDef(mappingLevel.getMemberFormatter() != null ? mappingLevel.getMemberFormatter().getRef() : null)
                .formatterAttr(null)
                .build();
        memberFormatter =
            FormatterFactory.instance()
                .createRolapMemberFormatter(memberFormatterContext);
        levelMapping = mappingLevel;
    }

    public RolapLevel(
        RolapHierarchy hierarchy,
        int depth,
        org.eclipse.daanse.rolap.mapping.model.Level mappingLevel)
    {

        this(mappingLevel.getName(),
                null,
                null,
                null,
                hierarchy,
                depth,
                false, null,
                mappingLevel);

    }

    RolapLevel(
            String name,
            RolapHierarchy hierarchy,
            int depth,
            boolean parentAsLeafEnable,
            String parentAsLeafNameFormat,
            org.eclipse.daanse.rolap.mapping.model.Level mappingLevel){

            this(name,
                    null,
                    null,
                    null,
                    hierarchy,
                    depth,
                    parentAsLeafEnable, parentAsLeafNameFormat,
                    mappingLevel);

    }

    private static Datatype getType(org.eclipse.daanse.rolap.mapping.model.Level mappingLevel) {
        if (mappingLevel.getColumnType() != null && !ColumnInternalDataType.UNDEFINED.equals(mappingLevel.getColumnType()) ) {
            if (ColumnInternalDataType.STRING.equals(mappingLevel.getColumnType())) {
                return org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype.VARCHAR;
            }
            return org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype.fromValue(mappingLevel.getColumnType().getLiteral());
        }
        return org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype.fromValue(mappingLevel.getColumn().getType().getLiteral());
    }

    private void setLevelInProperties() {
        RolapProperty[] properties = getProperties();
        if (properties != null) {
            for (RolapProperty property : properties) {
                property.setLevel(this);
            }
        }
    }

    // helper for constructor
    private static RolapProperty[] createProperties(org.eclipse.daanse.rolap.mapping.model.Level xmlLevel)
    {
        List<RolapProperty> list = new ArrayList<>();
        final RolapSqlExpression nameExp = LevelUtil.getNameExp(xmlLevel);

        if (nameExp != null) {
            list.add(
                new RolapProperty(
                		StandardProperty.NAME.getName(), Property.Datatype.TYPE_STRING,
                    nameExp, null, null, null, true,
                    StandardProperty.NAME.getDescription(), null));
        }
        for (int i = 0; i < xmlLevel.getMemberProperties().size(); i++) {
        	org.eclipse.daanse.rolap.mapping.model.MemberProperty xmlProperty = xmlLevel.getMemberProperties().get(i);

            FormatterCreateContext formatterContext =
                    new FormatterCreateContext.Builder(xmlProperty.getName())
                        //.formatterDef(xmlProperty.getFormatter() != null ? xmlProperty.getFormatter().getRef() : null)
                        .formatterAttr(xmlProperty.getFormatter() != null ? xmlProperty.getFormatter().getRef() : null)
                        .build();
            MemberPropertyFormatter formatter =
                FormatterFactory.instance()
                    .createPropertyFormatter(formatterContext);

            list.add(
                new RolapProperty(
                    xmlProperty.getName(),
                    convertPropertyTypeNameToCode(xmlProperty.getPropertyType().getLiteral()),
                    getPropertyExp(xmlLevel, i),
                    formatter,
                    xmlProperty.getName(),
                    xmlLevel.getMemberProperties().get(i).isDependsOnLevelValue(),
                    false,
                    xmlProperty.getDescription(), null));
        }
        return list.toArray(new RolapProperty[list.size()]);
    }

    private static AbstractProperty.Datatype convertPropertyTypeNameToCode(
        String type)
    {
        if ("String".equals(type)) {
            return Property.Datatype.TYPE_STRING;
        } else if ("Numeric".equalsIgnoreCase(type)) {
            return Property.Datatype.TYPE_NUMERIC;
        } else if ("Integer".equalsIgnoreCase(type)) {
            return Property.Datatype.TYPE_INTEGER;
        } else if ("Long".equalsIgnoreCase(type)) {
            return Property.Datatype.TYPE_LONG;
        } else if ("Boolean".equalsIgnoreCase(type)) {
            return Property.Datatype.TYPE_BOOLEAN;
        } else if ("Timestamp".equalsIgnoreCase(type)) {
            return Property.Datatype.TYPE_TIMESTAMP;
        } else if ("Time".equalsIgnoreCase(type)) {
            return Property.Datatype.TYPE_TIME;
        } else if ("Date".equalsIgnoreCase(type)) {
            return Property.Datatype.TYPE_DATE;
        } else {

            //TODO: Do log as warn
//            throw Util.newError(new StringBuilder("Unknown property type '")
//                .append(type).append("'").toString());
            return Property.Datatype.TYPE_STRING;

        }
    }

    private void checkColumn(org.eclipse.daanse.rolap.element.RolapColumn nameColumn) {
        final RolapHierarchy rolapHierarchy = (RolapHierarchy) hierarchy;
        if (nameColumn.getTable() == null) {
            final org.eclipse.daanse.rolap.mapping.model.RelationalQuery table = rolapHierarchy.getUniqueTable();
            if (table == null) {
                throw Util.newError(
                    new StringBuilder("must specify a table for level ").append(getUniqueName())
                    .append(" because hierarchy has more than one table").toString());
            }
            nameColumn.setTable(RelationUtil.getAlias(table));
        } else {
            if (!rolapHierarchy.tableExists(nameColumn.getTable())) {
                throw Util.newError(
                    new StringBuilder("Table '").append(nameColumn.getTable())
                        .append("' not found").toString());
            }
        }
    }

    public void init(org.eclipse.daanse.rolap.mapping.model.DimensionConnector xmlDimension) {
        if (xmlClosure != null) {
            final RolapDimension dimension = ((RolapHierarchy) hierarchy)
                .createClosedPeerDimension(this, xmlClosure);
            closedPeerLevel =
                    (RolapLevel) dimension.getHierarchies().getFirst().getLevels().get(1);
        }
    }

    @Override
	public final boolean isAll() {
        return (flags & FLAG_ALL) != 0;
    }

    @Override
	public boolean areMembersUnique() {
        return (depth == 0) || (depth == 1) && hierarchy.hasAll();
    }

    public String getTableAlias() {
        return ExpressionUtil.getTableAlias(keyExp);
    }

    @Override
	public RolapProperty[] getProperties() {
        return properties;
    }

    @Override
	public Property[] getInheritedProperties() {
        return inheritedProperties;
    }

    @Override
	public int getApproxRowCount() {
        return approxRowCount;
    }

    @Override
    public int getCardinality() {
        //TODO
        return getApproxRowCount();
    }

    @Override
    public List<Member> getMembers() {
        //TODO need to set members in level
        return members != null ? members: List.of();
    }

    public org.eclipse.daanse.rolap.mapping.model.Level getLevelMapping() {
        return levelMapping;
    }

    private static final Map<String, BestFitColumnType> VALUES =
        Map.of(
            "int", BestFitColumnType.INT,
            "double", BestFitColumnType.DOUBLE,
            "Object", BestFitColumnType.OBJECT,
            "String", BestFitColumnType.STRING,
            "long", BestFitColumnType.LONG,
            "Numeric", BestFitColumnType.DOUBLE);//TODO. May remove

    private static BestFitColumnType toInternalType(String internalType) {
        BestFitColumnType type = null;
        if(internalType!=null) {

        	type = VALUES.getOrDefault(internalType, null);
        }
        if (type == null && internalType != null) {
            throw Util.newError(
                new StringBuilder("Invalid value '").append(internalType)
                    .append("' for attribute 'internalType' of element 'Level'. ")
                    .append("Valid values are: ")
                    .append(VALUES.keySet()).toString());
        }
        return type;
    }

    public BestFitColumnType getInternalType() {
        return internalType;
    }

    /**
     * Conditions under which a level's members may be hidden (thereby creating
     * a <dfn>ragged hierarchy</dfn>).
     */
    public enum HideMemberCondition {
        /** A member always appears. */
        Never,

        /** A member doesn't appear if its name is null or empty. */
        IfBlankName,

        /** A member appears unless its name matches its parent's. */
        IfParentsName;

        public static HideMemberCondition fromValue(String v) {
            return Stream.of(HideMemberCondition.values())
                .filter(e -> (e.toString().equalsIgnoreCase(v)))
                .findFirst().orElse(Never);
            // TODO:  care about fallback
//                .orElseThrow(() -> new IllegalArgumentException(
//                    new StringBuilder("HideMemberCondition enum Illegal argument ").append(v)
//                        .toString())
//                );
        }
    }

    public OlapElement lookupChild(CatalogReader schemaReader, Segment name) {
        return lookupChild(schemaReader, name, MatchType.EXACT);
    }

    @Override
	public OlapElement lookupChild(
            CatalogReader schemaReader, Segment name, MatchType matchType)
    {
        if (name instanceof IdImpl.KeySegment keySegment) {
            List<Comparable> keyValues = new ArrayList<>();
            for (NameSegment nameSegment : keySegment.getKeyParts()) {
                final String keyValue = nameSegment.getName();
                if (RolapUtil.mdxNullLiteral().equalsIgnoreCase(keyValue)) {
                    keyValues.add(Util.sqlNullValue);
                } else {
                    keyValues.add(keyValue);
                }
            }
            final List<SqlExpression> keyExps = getInheritedKeyExps();
            if (keyExps.size() != keyValues.size()) {
                throw Util.newError(
                    new StringBuilder("Wrong number of values in member key; ")
                        .append(keySegment).append(" has ").append(keyValues.size())
                        .append(" values, whereas level's key has ").append(keyExps.size())
                        .append(" columns ")
                        .append(new AbstractList<String>() {
                            @Override
							public String get(int index) {
                                return genericExpression(keyExps.get(index));
                            }
                            @Override
							public int size() {
                            return keyExps.size();
                        }})
                        .append(".").toString());
            }
            return getHierarchy().getMemberReader().getMemberByKey(
                this, keyValues);
        }

        if (name instanceof NameSegment nameSegment) {
            RolapProperty[] properties = getProperties();
            if (properties != null) {
                for (RolapProperty property : properties) {
                    if (nameSegment.getName().equals(property.getName())) {
                        return property;
                    }
                }
            }
        }

        List<Member> levelMembers = schemaReader.getLevelMembers(this, true);
        if (levelMembers.size() > 0) {
            Member parent = levelMembers.get(0).getParentMember();
            return
                RolapUtil.findBestMemberMatch(
                    levelMembers,
                    (RolapMember) parent,
                    this,
                    name,
                    matchType);
        }
        return null;
    }

    private List<SqlExpression> getInheritedKeyExps() {
        final List<SqlExpression> list =
            new ArrayList<>();
        for (RolapLevel x = this;; x = (RolapLevel) x.getParentLevel()) {
            final SqlExpression keyExp1 = x.getKeyExp();
            if (keyExp1 != null) {
                list.add(keyExp1);
            }
            if (x.isUnique()) {
                break;
            }
        }
        return list;
    }

    /**
     * Indicates that level is not ragged and not a parent/child level.
     */
    public boolean isSimple() {
        // most ragged hierarchies are not simple -- see isTooRagged.
        if (isTooRagged()) {
            return false;
        }
        if (isParentChild()) {
            return false;
        }
        // does not work for measures
        if (isMeasure()) {
            return false;
        }
        return true;
    }

    /**
     * Determines whether the specified level is too ragged for native
     * evaluation, which is able to handle one special case of a ragged
     * hierarchy: when the level specified in the query is the leaf level of
     * the hierarchy and HideMemberCondition for the level is IfBlankName.
     * This is true even if higher levels of the hierarchy can be hidden
     * because even in that case the only column that needs to be read is the
     * column that holds the leaf. IfParentsName can't be handled even at the
     * leaf level because in the general case we aren't reading the column
     * that holds the parent. Also, IfBlankName can't be handled for non-leaf
     * levels because we would have to read the column for the next level
     * down for members with blank names.
     *
     * @return true if the specified level is too ragged for native
     *         evaluation.
     */
    private boolean isTooRagged() {
        // Is this the special case of raggedness that native evaluation
        // is able to handle?
        if (getDepth() == getHierarchy().getLevels().size() - 1) {
            return switch (getHideMemberCondition()) {
            case Never, IfBlankName -> false;
            default -> true;
            };
        }
        // Handle the general case in the traditional way.
        return getHierarchy().isRagged();
    }


    /**
     * Returns true when the level is part of a parent/child hierarchy and has
     * an equivalent closed level.
     */
    boolean hasClosedPeer() {
        return closedPeerLevel != null;
    }

    public RolapLevel getClosedPeer() {
        return closedPeerLevel;
    }

    public static RolapLevel lookupLevel(
        List<RolapCubeLevel> levels,
        String levelName)
    {
        for (RolapLevel level : levels) {
            if (level.getName().equals(levelName)) {
                return level;
            }
        }
        return null;
    }

}
