/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Catalog;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.DimensionType;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.formatter.MemberPropertyFormatter;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.element.MemberBase;
import org.eclipse.daanse.olap.element.OlapMetaData;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.function.def.aggregate.AggregateFunDef;
import org.eclipse.daanse.olap.impl.StatementImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import  org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.common.HierarchyUsage;
import org.eclipse.daanse.rolap.common.RolapEvaluator;
import org.eclipse.daanse.rolap.common.RolapEvaluatorRoot;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.ScenarioImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic implementation of a member in a {@link RolapHierarchy}.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public class RolapMemberBase
    extends MemberBase
    implements RolapMember
{
    /**
     * For members of a level with an ordinal expression defined, the
     * value of that expression for this member as retrieved via JDBC;
     * otherwise null.
     */
    private Comparable orderKey;
    private Boolean isParentChildLeaf;
    private static final Logger LOGGER = LoggerFactory.getLogger(RolapMember.class);

    /**
     * Sets a member's parent.
     *
     * Can screw up the caching structure. Only to be called by
     * {@link org.eclipse.daanse.olap.api.CacheControl#createMoveCommand}.
     *
     * New parent must be in same level as old parent.
     *
     * @param parentMember New parent member
     *
     * @see #getParentMember()
     * @see #getParentUniqueName()
     */
    public void setParentMember(RolapMember parentMember) {
        final RolapMember previousParentMember = getParentMember();
        if (previousParentMember.getLevel() != parentMember.getLevel()) {
            throw new IllegalArgumentException(
                "new parent belongs to different level than old");
        }
        this.parentMember = parentMember;
    }

    /** Ordinal of the member within the hierarchy. Some member readers do not
     * use this property; in which case, they should leave it as its default,
     * -1. */
    private int ordinal;
    private final Object key;

    /**
     * Maps property name to property value.
     *
     *  We expect there to be a lot of members, but few of them will
     * have properties. So to reduce memory usage, when empty, this is set to
     * an immutable empty set.
     */
    private Map<String, Object> mapPropertyNameToValue;

    private Boolean containsAggregateFunction = null;

    private Object captionValue;

    /**
     * Creates a RolapMemberBase.
     *
     * @param parentMember Parent member
     * @param level Level this member belongs to
     * @param key Key to this member in the underlying RDBMS
     * @param name Name of this member
     * @param memberType Type of member
     */
    public RolapMemberBase(
        Member parentMember,
        Level level,
        Object key,
        String name,
        MemberType memberType)
    {
        super(parentMember, level, memberType);
        assert key != null;
        assert !(parentMember instanceof RolapCubeMember)
            || parentMember instanceof RolapAllCubeMember
            || this instanceof RolapCalculatedMember
            || this instanceof VisualTotalMember;
        if (key instanceof byte[]) {
            // Some drivers (e.g. Derby) return byte arrays for binary columns
            // but byte arrays do not implement Comparable
            this.key = new String((byte[])key);
        } else {
            this.key = key;
        }
        this.ordinal = -1;
        this.mapPropertyNameToValue = Collections.emptyMap();

        if (name != null
            && !(key != null && name.equals(key.toString())))
        {
            // Save memory by only saving the name as a property if it's
            // different from the key.
            setProperty(StandardProperty.NAME.getName(), name);
        } else if (key != null) {
            setUniqueName(key);
        }
    }

    protected RolapMemberBase() {
        super();
        this.key = Util.sqlNullValue;
    }

    public RolapMemberBase(RolapMember parentMember, RolapLevel level, Object value) {
        this(parentMember, level, value, null, MemberType.REGULAR);
        //assert !(level instanceof RolapCubeLevel);
    }

    @Override
	protected Logger getLogger() {
        return LOGGER;
    }

    @Override
	public RolapLevel getLevel() {
        return (RolapLevel) level;
    }

    @Override
    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
	public RolapHierarchy getHierarchy() {
        return (RolapHierarchy) level.getHierarchy();
    }

    @Override
	public RolapMember getParentMember() {
        return (RolapMember) super.getParentMember();
    }

    /**
     * An object value to be formatted further by member formatter.
     *
     * Actually, acts like MemberBase#getCaption(),
     * but using not formatted object values.
     */
    @Override
    public Object getCaptionValue() {
        if (captionValue != null) {
            return captionValue;
        }

        // falling back to member name, as it's done in MemberBase
        Object name = getPropertyValue(StandardProperty.NAME.getName());

        // falling back to member key, as it's done in #getName()
        return name != null ? name : key;
    }

    public void setCaptionValue(Object captionValue) {
        this.captionValue = captionValue;
    }

    // Regular members do not have annotations. Measures and calculated members
    // do, so they override this method.
    @Override
	public MetaData getMetaData() {
        return OlapMetaData.empty();
    }

    @Override
	public int hashCode() {
        return  super.hashCode();
    }

    @Override
	public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof RolapMemberBase && equalsOlapElement((RolapMemberBase) o)) {
            return true;
        }
        if (o instanceof RolapCubeMember
                && equalsOlapElement(((RolapCubeMember) o).getRolapMember()))
        {
            // TODO: remove, RolapCubeMember should never meet RolapMember
            assert !Bug.BugSegregateRolapCubeMemberFixed;
            return true;
        }
        return false;
    }

    @Override
	public boolean equalsOlapElement(OlapElement o) {
        return (o instanceof RolapMemberBase)
            && equalsOlapElement((RolapMemberBase) o);
    }

    private boolean equalsOlapElement(RolapMemberBase that) {
        assert that != null; // public method should have checked
        // Do not use equalsIgnoreCase; unique names should be identical, and
        // hashCode assumes this.
        return this.getUniqueName().equals(that.getUniqueName());
    }

    public void makeUniqueName(HierarchyUsage hierarchyUsage) {
        if (parentMember == null && key != null) {
            String n = hierarchyUsage.getName();
            if (n != null) {
                String name = keyToString(key);
                n = Util.quoteMdxIdentifier(n);
                this.uniqueName = Util.makeFqName(n, name);
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug(
                        "RolapMember.makeUniqueName: uniqueName=" + uniqueName);
                }
            }
        }
    }

    public void setUniqueName(Object key) {
        String name = keyToString(key);

        // Drop the '[All Xxxx]' segment in regular members.
        // Keep the '[All Xxxx]' segment in the 'all' member.
        // Keep the '[All Xxxx]' segment in calc members.
        // Drop it in visual-totals and parent-child members (which are flagged
        // as calculated, but do have a data member).
        if (parentMember == null
            || (parentMember.isAll()
                && (!isCalculated()
                    || this instanceof VisualTotalMember
                    || getDataMember() != null)))
        {
            final RolapHierarchy hierarchy = getHierarchy();
            final Dimension dimension = hierarchy.getDimension();
            final RolapLevel level = getLevel();
            if (dimension.getDimensionType() != null
                && (dimension.getDimensionType().equals(
                    DimensionType.MEASURES_DIMENSION)
                && hierarchy.getName().equals(dimension.getName())))
            {
                // Kludge to ensure that calc members are called
                // [Measures].[Foo] not [Measures].[Measures].[Foo]. We can
                // remove this code when we revisit the scheme to generate
                // member unique names.
                this.uniqueName = Util.makeFqName(dimension, name);
            } else {
                if (name.equals(level.getName())) {
                    this.uniqueName =
                        Util.makeFqName(
                            Util.makeFqName(
                                hierarchy.getUniqueName(),
                                level.getName()),
                            name);
                } else {
                    this.uniqueName = Util.makeFqName(hierarchy, name);
                }
            }
        } else {
            this.uniqueName = Util.makeFqName(parentMember, name);
        }
    }

    @Override
	public boolean isCalculatedInQuery() {
        return false;
    }

    @Override
	public String getName() {
        final Object name =
            getPropertyValue(StandardProperty.NAME.getName());
        return (name != null)
            ? String.valueOf(name)
            : keyToString(key);
    }

    @Override
	public void setName(String name) {
        throw new Error("unsupported");
    }

    /**
     * Sets a property of this member to a given value.
     *
     * WARNING: Setting system properties such as "$name" may have nasty
     * side-effects.
     */
    @Override
	public synchronized void setProperty(String name, Object value) {
        if (name.equals(StandardProperty.CAPTION.getName())) {
            setCaption((String)value);
            return;
        }

        if (mapPropertyNameToValue.isEmpty()) {
            // the empty map is shared and immutable; create our own
            mapPropertyNameToValue = new HashMap<String, Object>();
        }
        if (name.equals(StandardProperty.NAME.getName())) {
            if (value == null) {
                value = RolapUtil.mdxNullLiteral();
            }
            setUniqueName(value);
        }

        if (name.equals(StandardProperty.MEMBER_ORDINAL.getName())) {
            String ordinal = (String) value;
            if (ordinal.startsWith("\"") && ordinal.endsWith("\"")) {
                ordinal = ordinal.substring(1, ordinal.length() - 1);
            }
            final double d = Double.parseDouble(ordinal);
            setOrdinal((int) d);
        }

        mapPropertyNameToValue.put(name, value);
    }

    @Override
	public Object getPropertyValue(String propertyName) {
        return getPropertyValue(propertyName, true);
    }

    @Override
	public Object getPropertyValue(String propertyName, boolean matchCase) {
        StandardProperty property = StandardProperty.lookup(propertyName, matchCase);
        if (property != null) {
            Catalog schema;
            Member parentMember;
            List<RolapMember> list;
            if(property== StandardProperty.NAME) {
                // Do NOT call getName() here. This property is internal,
                // and must fall through to look in the property list.

            }else if(property==  StandardProperty.CAPTION){
                return getCaption();

            }else if(property==  StandardProperty.CATALOG_NAME){
                // TODO: can't go from member to connection thence to
                // Connection.getCatalogName()

            }else if(property==  StandardProperty.SCHEMA_NAME){
                schema = getHierarchy().getDimension().getCatalog();
                return schema.getName();

            }else if(property==  StandardProperty.CUBE_NAME){
                // TODO: can't go from member to cube cube yet

            }else if(property==  StandardProperty.DIMENSION_UNIQUE_NAME){
                return getHierarchy().getDimension().getUniqueName();

            }else if(property==  StandardProperty.HIERARCHY_UNIQUE_NAME){
                return getHierarchy().getUniqueName();

            }else if(property==  StandardProperty.LEVEL_UNIQUE_NAME){
                return getLevel().getUniqueName();

            }else if(property==  StandardProperty.LEVEL_NUMBER){
                return getLevel().getDepth();

            }else if(property==  StandardProperty.MEMBER_UNIQUE_NAME){
                return getUniqueName();

            }else if(property==  StandardProperty.MEMBER_NAME){
                return getName();

            }else if(property==  StandardProperty.MEMBER_TYPE){
                return getMemberType().ordinal();

            }else if(property==  StandardProperty.MEMBER_GUID){
                return null;

            }else if(property==  StandardProperty.MEMBER_CAPTION){
                return getCaption();

            }else if(property==  StandardProperty.MEMBER_ORDINAL){
                return getOrdinal();

            }else if(property==  StandardProperty.CHILDREN_CARDINALITY){
                Connection conn = ((RolapCatalog) level.getDimension().getCatalog())
                        .getInternalConnection();
                StatementImpl statement = (StatementImpl) conn.getInternalStatement();
                ExecutionImpl execution = new ExecutionImpl(statement,
                        ExecuteDurationUtil.executeDurationValue(conn.getContext()));
                return ExecutionContext.where(execution.asContext(), () -> {
                    if (isAll() && childLevelHasApproxRowCount()) {
                        return getLevel().getChildLevel()
                            .getApproxRowCount();
                    } else {
                        ArrayList<RolapMember> memberList =
                            new ArrayList<>();
                        getHierarchy().getMemberReader()
                            .getMemberChildren(
                                RolapMemberBase.this, memberList);
                        return memberList.size();
                    }
                });

            }else if(property==  StandardProperty.PARENT_LEVEL){
                parentMember = getParentMember();
                return parentMember == null
                    ? 0
                    : parentMember.getLevel().getDepth();

            }else if(property==  StandardProperty.PARENT_UNIQUE_NAME){
                parentMember = getParentMember();
                return parentMember == null
                    ? null
                    : parentMember.getUniqueName();

            }else if(property==  StandardProperty.PARENT_COUNT){
                parentMember = getParentMember();
                return parentMember == null ? 0 : 1;

            }else if(property==  StandardProperty.VISIBLE){
                final Object visProp =
                    getPropertyFromMap(propertyName, matchCase);
                if (visProp != null) {
                    // There was a visibility property specified for this
                    // member. Return that.
                    return visProp;
                }
                // Default behavior is to return isVisible.
                return isVisible();

            }else if(property==  StandardProperty.MEMBER_KEY){
            }else if(property==  StandardProperty.KEY){
                return this == this.getHierarchy().getAllMember()
                    ? 0
                    : getKey();

            }else if(property==  StandardProperty.SCENARIO){
                return ScenarioImpl.forMember(this);
                }
    }
        return getPropertyFromMap(propertyName, matchCase);
    }

    /**
     * Returns the value of a property by looking it up in the property map.
     *
     * @param propertyName Name of property
     * @param matchCase Whether to match name case-sensitive
     * @return Property value
     */
    protected Object getPropertyFromMap(
        String propertyName,
        boolean matchCase)
    {
        synchronized (this) {
            if (matchCase) {
                return mapPropertyNameToValue.get(propertyName);
            } else {
                for (String key : mapPropertyNameToValue.keySet()) {
                    if (key.equalsIgnoreCase(propertyName)) {
                        return mapPropertyNameToValue.get(key);
                    }
                }
                return null;
            }
        }
    }

    protected boolean childLevelHasApproxRowCount() {
        return getLevel().getChildLevel().getApproxRowCount()
            > Integer.MIN_VALUE;
    }

    /**
     * @deprecated Use {@link #isAll}; will be removed in mondrian-4.0
     */
    @Deprecated
	@Override
	public boolean isAllMember() {
        return getLevel().getHierarchy().hasAll()
                && getLevel().getDepth() == 0;
    }

    @Override
	public Property[] getProperties() {
        return getLevel().getInheritedProperties();
    }

    @Override
	public int getOrdinal() {
        return ordinal;
    }

    @Override
	public Comparable getOrderKey() {
        return orderKey;
    }

    public void setOrdinal(int ordinal) {
        if (this.ordinal == -1) {
          this.ordinal = ordinal;
        }
    }

    public void setOrdinal(int ordinal, boolean forced) {
      if (forced) {
          this.ordinal = ordinal;
      } else {
        setOrdinal(ordinal);
      }
  }

    public void setOrderKey(Comparable orderKey) {
        this.orderKey = orderKey;
    }

    private void resetOrdinal() {
        this.ordinal = -1;
    }

    @Override
	public Object getKey() {
        return this.key;
    }

    /**
     * Compares this member to another {@link RolapMemberBase}.
     *
     * The method first compares on keys; null keys always collate last.
     * If the keys are equal, it compares using unique name.
     *
     * This method does not consider {@link #ordinal} field, because
     * ordinal is only unique within a parent. If you want to compare
     * members which may be at any position in the hierarchy, use
     * org.eclipse.daanse.olap.fun.FunUtil#compareHierarchically.
     *
     * @return -1 if this is less, 0 if this is the same, 1 if this is greater
     */
    @Override
	public int compareTo(Object o) {
        RolapMemberBase other = (RolapMemberBase)o;
        assert this.key != null && other.key != null;

        if (this.key == Util.sqlNullValue
            && other.key == Util.sqlNullValue)
        {
            // if both keys are null, they are equal.
            // compare by unique name.
            return this.getName().compareTo(other.getName());
        }

        if (other.key == Util.sqlNullValue) {
            // not null is greater than null
            return 1;
        }

        if (this.key == Util.sqlNullValue) {
            // null is less than not null
            return -1;
        }

        // as both keys are not null, compare by key
        //  String, Double, Integer should be possible
        //  any key object should be "Comparable"
        // anyway - keys should be of the same class
        if (this.key.getClass().equals(other.key.getClass())) {
            if (this.key instanceof String) {
                // use a special case sensitive compare name which
                // first compares w/o case, and if 0 compares with case
                return Util.caseSensitiveCompareName(
                    (String) this.key, (String) other.key);
            } else {
                return Util.compareKey(this.key, other.key);
            }
        }
        // Compare by unique name in case of different key classes.
        // This is possible, if a new calculated member is created
        //  in a dimension with an Integer key. The calculated member
        //  has a key of type String.
        return this.getUniqueName().compareTo(other.getUniqueName());
    }

    @Override
	public boolean isHidden() {
        final RolapLevel rolapLevel = getLevel();
        switch (rolapLevel.getHideMemberCondition()) {
        case Never:
            return false;

        case IfBlankName:
        {
            // If the key value in the database is null, then we use
            // a special key value whose toString() is "null".
            final String name = getName();
            return name.equals(RolapUtil.mdxNullLiteral())
                || name.isBlank();
        }

        //TODO:: IfParentsProperty and additional attribute that is by default the name, or a property that is switch
        case IfParentsName:
        {
            final Member parentMember = getParentMember();
            if (parentMember == null) {
                return false;
            }
            final String parentName = parentMember.getName();
            final String name = getName();
            return (parentName == null ? "" : parentName).equals(
                name == null ? "" : name);
        }

        default:
            throw Util.badValue(rolapLevel.getHideMemberCondition());
        }
    }

    @Override
	public int getDepth() {
        return getLevel().getDepth();
    }

    @Override
	public String getPropertyFormattedValue(String propertyName) {
        // do we have a formatter ? if yes, use it
        Property[] props = getLevel().getProperties();
        Property prop = null;
        for (Property prop1 : props) {
            if (prop1.getName().equals(propertyName)) {
                prop = prop1;
                break;
            }
        }
        Object propertyValue = getPropertyValue(propertyName);
        MemberPropertyFormatter pf;
        if (prop != null && (pf = prop.getFormatter()) != null) {
            return pf.format(this, prop, propertyValue);
        }
        // fallback
        return propertyValue == null ? null : propertyValue.toString();
    }

    @Override
	public boolean isParentChildLeaf() {
        if (isParentChildLeaf == null) {
            isParentChildLeaf = getLevel().isParentChild()
                && getDimension().getCatalog().getCatalogReaderWithDefaultRole()
                .getMemberChildren(this).size() == 0;
        }
        return isParentChildLeaf;
    }

    /**
     * Returns a list of member lists where the first member
     * list is the root members while the last member array is the
     * leaf members.
     *
     * If you know that you will need to get all or most of the members of
     * a hierarchy, then calling this which gets all of the hierarchy's
     * members all at once is much faster than getting members one at
     * a time.
     *
     * @param schemaReader Schema reader
     * @param hierarchy  Hierarchy
     * @return List of arrays of members
     */
    public static List<List<Member>> getAllMembers(
        CatalogReader schemaReader,
        Hierarchy hierarchy)
    {
        long start = System.currentTimeMillis();

        try {
            // Getting the members by Level is the fastest way that I could
            // find for getting all of a hierarchy's members.
            List<List<Member>> list = new ArrayList<>();
            List<? extends Level> levels = hierarchy.getLevels();
            for (Level level : levels) {
                List<Member> members =
                    schemaReader.getLevelMembers(level, true);
                if (members != null) {
                    list.add(members);
                }
            }
            return list;
        } finally {
            if (LOGGER.isDebugEnabled()) {
                long end = System.currentTimeMillis();
                LOGGER.debug(
                    "RolapMember.getAllMembers: time=" + (end - start));
            }
        }
    }

    public static int getHierarchyCardinality(
        CatalogReader schemaReader,
        Hierarchy hierarchy)
    {
        int cardinality = 0;
        List<? extends Level> levels = hierarchy.getLevels();
        for (Level level1 : levels) {
            cardinality += schemaReader.getLevelCardinality(level1, true, true);
        }
        return cardinality;
    }

    /**
     * Sets member ordinal values using a Bottom-up/Top-down algorithm.
     *
     * Gets an array of members for each level and traverses
     * array for the lowest level, setting each member's
     * parent's parent's etc. member's ordinal if not set working back
     * down to the leaf member and then going to the next leaf member
     * and traversing up again.
     *
     * The above algorithm only works for a hierarchy that has all of its
     * leaf members in the same level (that is, a non-ragged hierarchy), which
     * is the norm. After all member ordinal values have been set, traverses
     * the array of members, making sure that all members' ordinals have been
     * set. If one is found that is not set, then one must to a full Top-down
     * setting of the ordinals.
     *
     * The Bottom-up/Top-down algorithm is MUCH faster than the Top-down
     * algorithm.
     *
     * @param schemaReader Schema reader
     * @param seedMember Member
     */
    public static void setOrdinals(
        CatalogReader schemaReader,
        Member seedMember)
    {
        seedMember = RolapUtil.strip((RolapMember) seedMember);

         // The following are times for executing different set ordinals
         // algorithms for both the FoodMart Sales cube/Store dimension
         // and a Large Data set with a dimension with about 250,000 members.
         //
         // Times:
         //    Original setOrdinals Top-down
         //       Foodmart: 63ms
         //       Large Data set: 651865ms
         //    Calling getAllMembers before calling original setOrdinals
         //    Top-down
         //       Foodmart: 32ms
         //       Large Data set: 73880ms
         //    Bottom-up/Top-down
         //       Foodmart: 17ms
         //       Large Data set: 4241ms
        long start = System.currentTimeMillis();

        try {
            Hierarchy hierarchy = seedMember.getHierarchy();
            int ordinal = hierarchy.hasAll() ? 1 : 0;
            List<List<Member>> levelMembers =
                getAllMembers(schemaReader, hierarchy);
            List<Member> leafMembers =
                levelMembers.get(levelMembers.size() - 1);
            levelMembers = levelMembers.subList(0, levelMembers.size() - 1);

            // Set all ordinals
            for (Member child : leafMembers) {
                ordinal = bottomUpSetParentOrdinals(ordinal, child);
                ordinal = setOrdinal(child, ordinal);
            }

            boolean needsFullTopDown = needsFullTopDown(levelMembers);

            // If we must to a full Top-down, then first reset all ordinal
            // values to -1, and then call the Top-down
            if (needsFullTopDown) {
                for (List<Member> members : levelMembers) {
                    for (Member member : members) {
                        if (member instanceof RolapMemberBase) {
                            ((RolapMemberBase) member).resetOrdinal();
                        }
                    }
                }

                // call full Top-down
                setOrdinalsTopDown(schemaReader, seedMember);
            }
        } finally {
            if (LOGGER.isDebugEnabled()) {
                long end = System.currentTimeMillis();
                LOGGER.debug("RolapMember.setOrdinals: time=" + (end - start));
            }
        }
    }

    /**
     * Returns whether the ordinal assignment algorithm needs to perform
     * the more expensive top-down algorithm. If the hierarchy is 'uneven', not
     * all leaf members are at the same level, then bottom-up setting of
     * ordinals will have missed some.
     *
     * @param levelMembers Array containing the list of members in each level
     * except the leaf level
     * @return whether we need to apply the top-down ordinal assignment
     */
    private static boolean needsFullTopDown(List<List<Member>> levelMembers) {
        for (List<Member> members : levelMembers) {
            for (Member member : members) {
                if (member.getOrdinal() == -1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Walks up the hierarchy, setting the ordinals of ancestors until it
     * reaches the root or hits an ancestor whose ordinal has already been
     * assigned.
     *
     * Assigns the given ordinal to the ancestor nearest the root which has
     * not been assigned an ordinal, and increments by one for each descendant.
     *
     * @param ordinal Ordinal to assign to deepest ancestor
     * @param child Member whose ancestors ordinals to set
     * @return Ordinal, incremented for each time it was used
     */
    private static int bottomUpSetParentOrdinals(int ordinal, Member child) {
        Member parent = child.getParentMember();
        if ((parent != null) && parent.getOrdinal() == -1) {
            ordinal = bottomUpSetParentOrdinals(ordinal, parent);
            ordinal = setOrdinal(parent, ordinal);
        }
        return ordinal;
    }

    private static int setOrdinal(Member member, int ordinal) {
        if (member instanceof RolapMemberBase) {
            ((RolapMemberBase) member).setOrdinal(ordinal++);
        } else {
            // TODO
            LOGGER.warn(
                "RolapMember.setAllChildren: NOT RolapMember member.name={}, member.class={}, ordinal={}",
                member.getName(), member.getClass().getName(), ordinal);
            ordinal++;
        }
        return ordinal;
    }

    /**
     * Sets ordinals of a complete member hierarchy as required by the
     * MEMBER_ORDINAL XMLA element using a depth-first algorithm.
     *
     * For big hierarchies it takes a bunch of time. SQL Server is
     * relatively fast in comparison so it might be storing such
     * information in the DB.
     *
     * @param schemaReader Schema reader
     * @param member Member
     */
    private static void setOrdinalsTopDown(
        CatalogReader schemaReader,
        Member member)
    {
        long start = System.currentTimeMillis();

        try {
            Member parent = schemaReader.getMemberParent(member);

            if (parent == null) {
                // top of the world
                int ordinal = 0;

                List<Member> siblings =
                    schemaReader.getHierarchyRootMembers(member.getHierarchy());

                for (Member sibling : siblings) {
                    ordinal = setAllChildren(ordinal, schemaReader, sibling);
                }

            } else {
                setOrdinalsTopDown(schemaReader, parent);
            }
        } finally {
            if (LOGGER.isDebugEnabled()) {
                long end = System.currentTimeMillis();
                LOGGER.debug(
                    "RolapMember.setOrdinalsTopDown: time=" + (end - start));
            }
        }
    }

    private static int setAllChildren(
        int ordinal,
        CatalogReader schemaReader,
        Member member)
    {
        ordinal = setOrdinal(member, ordinal);

        List<Member> children = schemaReader.getMemberChildren(member);
        for (Member child : children) {
            ordinal = setAllChildren(ordinal, schemaReader, child);
        }

        return ordinal;
    }

    /**
     * Converts a key to a string to be used as part of the member's name
     * and unique name.
     *
     * Usually, it just calls {@link Object#toString}. But if the key is an
     * integer value represented in a floating-point column, we'd prefer the
     * integer value. For example, one member of the
     * [Sales].[Store SQFT] dimension comes out "20319.0" but we'd
     * like it to be "20319".
     */
    protected static String keyToString(Object key) {
        String name;
        if (key == null || Util.sqlNullValue.equals(key)) {
            name = RolapUtil.mdxNullLiteral();
        } else if (key instanceof NameSegment) {
            name = ((NameSegment) key).getName();
        } else {
            name = key.toString();
        }
        if ((key instanceof Number) && name.endsWith(".0")) {
            name = name.substring(0, name.length() - 2);
        }
        return name;
    }

    @Override
	public boolean containsAggregateFunction() {
        // searching for agg functions is expensive, so cache result
        if (containsAggregateFunction == null) {
            containsAggregateFunction =
                foundAggregateFunction(getExpression());
        }
        return containsAggregateFunction;
    }

    /**
     * Returns whether an expression contains a call to an aggregate
     * function such as "Aggregate" or "Sum".
     *
     * @param exp Expression
     * @return Whether expression contains a call to an aggregate function.
     */
    private static boolean foundAggregateFunction(Expression exp) {
        if (exp instanceof ResolvedFunCallImpl resolvedFunCall) {
            if (resolvedFunCall.getFunDef() instanceof AggregateFunDef) {
                return true;
            } else {
                for (Expression argExp : resolvedFunCall.getArgs()) {
                    if (foundAggregateFunction(argExp)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
	public Calc getCompiledExpression(RolapEvaluatorRoot root) {
        return root.getCompiled(getExpression(), true, null);
    }

    @Override
	public int getHierarchyOrdinal() {
        return getHierarchy().getOrdinalInCube();
    }

    @Override
	public void setContextIn(RolapEvaluator evaluator) {
        final RolapMember defaultMember =
            evaluator.root.defaultMembers[getHierarchyOrdinal()];

        // This method does not need to call RolapEvaluator.removeCalcMember.
        // That happens implicitly in setContext.
        evaluator.setContext(defaultMember);
        evaluator.setExpanding(this);
    }

    @Override
    public Member createPseudoLeafMember(Member child, String name) {
        RolapMember m =
                new RolapMemberBase(this, (((org.eclipse.daanse.rolap.element.RolapCubeMember)child).getRolapMember().getLevel()), this.getName(), name, MemberType.REGULAR);
        return new RolapPseudoLeafMember(
                (RolapCubeMember) this, m,
                ((org.eclipse.daanse.rolap.element.RolapCubeLevel)this.getLevel().getChildLevel()));
    }

}
