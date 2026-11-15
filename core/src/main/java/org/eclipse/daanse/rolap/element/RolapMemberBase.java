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

import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Catalog;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.DimensionType;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.formatter.MemberPropertyFormatter;
import org.eclipse.daanse.olap.api.query.NameSegment;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.element.CalculableMemberBase;
import org.eclipse.daanse.olap.element.MemberBase;
import org.eclipse.daanse.olap.element.OlapMetaDataBase;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.execution.StatementImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import  org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.star.HierarchyUsage;
import org.eclipse.daanse.rolap.common.writeback.ScenarioImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic implementation of a member in a {@link RolapHierarchy}.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public class RolapMemberBase
    extends CalculableMemberBase
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
     * {@link org.eclipse.daanse.olap.api.cache.CacheControl#createMoveCommand}.
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
            || this instanceof RolapCalculatedMember;
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
        return this.getUniqueName().equals(that.getUniqueName()) && this.getKey().equals(that.getKey());
    }

    public void makeUniqueName(HierarchyUsage hierarchyUsage) {
        if (parentMember == null && key != null) {
            String n = hierarchyUsage.getName();
            if (n != null) {
                String name = keyToName(key);
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
        String name = keyToName(key);

        // Drop the '[All Xxxx]' segment in regular members.
        // Keep the '[All Xxxx]' segment in the 'all' member.
        // Keep the '[All Xxxx]' segment in calc members.
        // Drop it in parent-child members (which are flagged as calculated, but do
        // have a data member). A visual total takes the unique name of the member it
        // stands for and never reaches here.
        if (parentMember == null
            || (parentMember.isAll()
                && (!isCalculated()
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
	public String getName() {
        final Object name =
            getPropertyValue(StandardProperty.NAME.getName());
        return (name != null)
            ? String.valueOf(name)
            : keyToName(key);
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
        if (property == StandardProperty.VISIBLE) {
            // A member may say so itself; only then does the level decide.
            final Object declared = getPropertyFromMap(propertyName, matchCase);
            return declared != null ? declared : isVisible();
        }
        if (property == StandardProperty.SCENARIO) {
            return ScenarioImpl.forMember(this);
        }
        // Everything a member answers from its place in the hierarchy is answered once, for every art;
        // NAME is internal here and falls through to what this member was told.
        if (property != StandardProperty.NAME) {
            final Object shared = super.getPropertyValue(propertyName, matchCase);
            if (shared != null) {
                return shared;
            }
        }
        return getPropertyFromMap(propertyName, matchCase);
    }

    /**
     * How many children this member has. Counting them means reading them, in a context of its own so
     * that a client asking the member outside a query does not run without one; at the All member an
     * estimate of the level below may stand in.
     */
    @Override
    protected Integer childCount() {
        Connection conn = ((RolapCatalog) level.getDimension().getCatalog()).getInternalConnection();
        StatementImpl statement = (StatementImpl) conn.getInternalStatement();
        ExecutionImpl execution = new ExecutionImpl(statement,
                ExecuteDurationUtil.executeDurationValue(conn.getContext()));
        return ExecutionContext.where(execution.asContext(), () -> {
            if (isAll() && childLevelHasApproxRowCount()) {
                return getLevel().getChildLevel().getApproxRowCount();
            }
            ArrayList<RolapMember> memberList = new ArrayList<>();
            getHierarchy().getMemberReader().getMemberChildren(RolapMemberBase.this, memberList);
            return memberList.size();
        });
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

    @Override
	public boolean isHidden() {
        final RolapLevel rolapLevel = getLevel();
        switch (rolapLevel.getHideMemberCondition()) {
        case NEVER:
            return false;

        case IF_BLANK_NAME:
        {
            // If the key value in the database is null, then we use
            // a special key value whose toString() is "null".
            final String name = getName();
            return name.equals(RolapUtil.mdxNullLiteral())
                || name.isBlank();
        }

        //TODO:: IfParentsProperty and additional attribute that is by default the name, or a property that is switch
        case IF_PARENTS_NAME:
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
	public boolean isAllMember() {
        return getLevel().getHierarchy().hasAll()
                && getLevel().getDepth() == 0;
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

    public void setOrderKey(Comparable orderKey) {
        this.orderKey = orderKey;
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
                .getMemberChildren(this).isEmpty();
        }
        return isParentChildLeaf;
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

    /**
     * Renders the key as a member name. For a BOOLEAN-typed level the key may arrive as a
     * {@code Number} (0/1) when the column is physically stored as SMALLINT, so it is mapped to
     * {@code true}/{@code false}. Falls back to {@link #keyToString(Object)} otherwise.
     */
    private String keyToName(Object k) {
        final RolapLevel level = getLevel();
        if (level != null && level.getDatatype() == Datatype.BOOLEAN) {
            if (k instanceof Boolean b) {
                return b ? "true" : "false";
            }
            if (k instanceof Number num) {
                return num.intValue() != 0 ? "true" : "false";
            }
        }
        return keyToString(k);
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
