/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 21 March, 2002
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

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.getTableAlias;
import static org.eclipse.daanse.rolap.common.util.RelationUtil.find;
import static org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.mapping.api.model.ColumnMapping;
import org.eclipse.daanse.rolap.mapping.api.model.DimensionConnectorMapping;
import org.eclipse.daanse.rolap.mapping.api.model.DimensionMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RelationalQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TableMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A HierarchyUsage is the usage of a hierarchy in the context
 * of a cube. Private hierarchies can only be used in their own
 * cube. Public hierarchies can be used in several cubes. The problem comes
 * when several cubes which the same public hierarchy are brought together
 * in one virtual cube. There are now several usages of the same public
 * hierarchy. Which one to use? It depends upon what measure we are
 * currently using. We should use the hierarchy usage for the fact table
 * which underlies the measure. That is what determines the foreign key to
 * join on.
 *
 * A HierarchyUsage is identified by
 * (hierarchy.sharedHierarchy, factTable) if the hierarchy is
 * shared, or (hierarchy, factTable) if it is private.
 *
 * @author jhyde
 * @since 21 March, 2002
 */
public class HierarchyUsage {
    private static final Logger LOGGER = LoggerFactory.getLogger(HierarchyUsage.class);
    private final static String mustSpecifyPrimaryKeyForHierarchy =
        "In usage of hierarchy ''{0}'' in cube ''{1}'', you must specify a primary key.";
    private final static String dimensionUsageHasUnknownLevel =
        "In usage of dimension ''{0}'' in cube ''{1}'', the level ''{2}'' is unknown";
    private final static String mustSpecifyForeignKeyForHierarchy = """
        In usage of hierarchy ''{0}'' in cube ''{1}'', you must specify a foreign key, because the hierarchy table is different from the fact table.
    """;
    private final static String mustSpecifyPrimaryKeyTableForHierarchy =
        "Must specify a primary key table for hierarchy ''{0}'', because it has more than one table.";

    enum Kind {
        UNKNOWN,
        SHARED,
        VIRTUAL,
        PRIVATE
    }

    /**
     * Fact table (or relation) which this usage is joining to. This
     * identifies the usage, and determines which join conditions need to be
     * used.
     */
    protected final RelationalQueryMapping fact;

    /**
     * This matches the hierarchy - may not be unique.
     * NOT NULL.
     */
    private final String hierarchyName;

    /**
     * not NULL for DimensionUsage
     * not NULL for Dimension
     */
    private final String name;

    /**
     * This is the name used to look up the hierachy usage. When the dimension
     * has only a single hierachy, then the fullName is simply the
     * CubeDimension name; there is no need to use the default dimension name.
     * But, when the dimension has more than one hierachy, then the fullName
     * is the CubeDimension dotted with the dimension hierachy name.
     *
     * NOTE: jhyde, 2009/2/2: The only use of this field today is for
     * {@link RolapCube#getUsageByName}, which is used only for tracing.
     */
    private final String fullName;

    /**
     * The foreign key by which this {@link Hierarchy} is joined to
     * the {@link #fact} table.
     */
    private final ColumnMapping foreignKey;

    /**
     * not NULL for DimensionUsage
     * NULL for Dimension
     */
    private final String source; // maybe need to remove that

    /**
     * May be null, this is the field that is used to disambiguate column
     * names in aggregate tables
     */
    private final String usagePrefix;

    // NOT USED
    private final String level;

    /**
     * Dimension table which contains the primary key for the hierarchy.
     * (Usually the table of the lowest level of the hierarchy.)
     */
    private RelationalQueryMapping joinTable;

    /**
     * The expression (usually a {@link mondrian.olap.MappingColumn}) by
     * which the hierarchy which is joined to the fact table.
     */
    private SqlExpression joinExp;

    private final Kind kind;

    /**
     * Creates a HierarchyUsage.
     *
     * @param cube Cube
     * @param hierarchy Hierarchy
     * @param cubeDim XML definition of a dimension which belongs to a cube
     */
    HierarchyUsage(
        RolapCube cube,
        RolapHierarchy hierarchy,
        DimensionConnectorMapping cubeDim)
    {
        assert cubeDim != null : "precondition: cubeDim != null";

        this.fact = cube.getFact();

        // Attributes common to all Hierarchy kinds
        // name
        // foreignKey
        this.name = cubeDim.getOverrideDimensionName();
        this.foreignKey = cubeDim.getForeignKey();
        DimensionMapping du = cubeDim.getDimension();

        this.kind = Kind.SHARED;


        // Shared Hierarchy attributes
        // source
        // level
        this.hierarchyName = deriveHierarchyName(hierarchy);
        int index = this.hierarchyName == null ? -1 : this.hierarchyName.indexOf('.');
        if (this.hierarchyName == null || index == -1) {
            this.fullName = this.name;
            this.source = du.getName();
        } else {
            String hname = this.hierarchyName.substring(
                index + 1, this.hierarchyName.length());

            StringBuilder buf = new StringBuilder(32);
            buf.append(this.name);
            buf.append('.');
            buf.append(hname);
            this.fullName = buf.toString();

            buf.setLength(0);
            buf.append(du.getName());
            buf.append('.');
            buf.append(hname);
            this.source = buf.toString();
        }

        this.level = cubeDim.getLevel() != null ? cubeDim.getLevel().getName() : null;
        this.usagePrefix = du.getUsagePrefix();

        init(cube, hierarchy, cubeDim);

        /*
        if (cubeDim instanceof MappingDimensionUsage du) {
            this.kind = Kind.SHARED;


            // Shared Hierarchy attributes
            // source
            // level
            this.hierarchyName = deriveHierarchyName(hierarchy);
            int index = this.hierarchyName == null ? -1 : this.hierarchyName.indexOf('.');
            if (this.hierarchyName == null || index == -1) {
                this.fullName = this.name;
                this.source = du.source();
            } else {
                String hname = this.hierarchyName.substring(
                    index + 1, this.hierarchyName.length());

                StringBuilder buf = new StringBuilder(32);
                buf.append(this.name);
                buf.append('.');
                buf.append(hname);
                this.fullName = buf.toString();

                buf.setLength(0);
                buf.append(du.source());
                buf.append('.');
                buf.append(hname);
                this.source = buf.toString();
            }

            this.level = du.level();
            this.usagePrefix = du.usagePrefix();

            init(cube, hierarchy, du);

        } else if (cubeDim instanceof MappingPrivateDimension privateDimension) {
            this.kind = Kind.PRIVATE;

            // Private Hierarchy attributes
            // type
            // caption
            MappingPrivateDimension d = privateDimension;

            this.hierarchyName = deriveHierarchyName(hierarchy);
            this.fullName = this.name;

            this.source = null;
            this.usagePrefix = d.usagePrefix();
            this.level = null;

            init(cube, hierarchy, null);

        } else if (cubeDim instanceof MappingVirtualCubeDimension) {
            this.kind = Kind.VIRTUAL;

            this.hierarchyName = cubeDim.getOverrideDimensionName();
            this.fullName = this.name;

            this.source = null;
            this.usagePrefix = null;
            this.level = null;

            init(cube, hierarchy, null);

        } else {
            getLogger().warn(
                "HierarchyUsage<init>: Unknown cubeDim={}",
                    cubeDim.getClass().getName());

            this.kind = Kind.UNKNOWN;

            this.hierarchyName = cubeDim.getOverrideDimensionName();
            this.fullName = this.name;

            this.source = null;
            this.usagePrefix = null;
            this.level = null;

            init(cube, hierarchy, null);
        }
        */
        if (getLogger().isDebugEnabled()) {
            getLogger().debug(
                new StringBuilder(toString())
                .append(", cubeDim=")
                .append(cubeDim.getClass().getName()).toString());
        }
    }

    private String deriveHierarchyName(RolapHierarchy hierarchy) {
        final String nameInner = hierarchy.getName();
            final String dimensionName = hierarchy.getDimension().getName();
        if (nameInner == null
            || nameInner.equals("")
            || nameInner.equals(dimensionName))
        {
            return nameInner;
        } else {
            return dimensionName + '.' + nameInner;
        }

    }

    protected Logger getLogger() {
        return LOGGER;
    }

    public String getHierarchyName() {
        return this.hierarchyName;
    }
    public String getFullName() {
        return this.fullName;
    }
    public String getName() {
        return this.name;
    }
    public ColumnMapping getForeignKey() {
        return this.foreignKey;
    }
    public String getSource() {
        return this.source;
    }
    public String getLevelName() {
        return this.level;
    }
    public String getUsagePrefix() {
        return this.usagePrefix;
    }

    public RelationalQueryMapping getJoinTable() {
        return this.joinTable;
    }

    public SqlExpression getJoinExp() {
        return this.joinExp;
    }

    public Kind getKind() {
        return this.kind;
    }
    public boolean isShared() {
        return this.kind == Kind.SHARED;
    }
    public boolean isVirtual() {
        return this.kind == Kind.VIRTUAL;
    }
    public boolean isPrivate() {
        return this.kind == Kind.PRIVATE;
    }

    @Override
	public boolean equals(Object o) {
        if (o instanceof HierarchyUsage other) {
            return (this.kind == other.kind)
                && Objects.equals(this.fact, other.fact)
                && this.hierarchyName.equals(other.hierarchyName)
                && Util.equalName(this.name, other.name)
                && Util.equalName(this.source, other.source)
                && Objects.equals(this.foreignKey, other.foreignKey);
        } else {
            return false;
        }
    }

    @Override
	public int hashCode() {
        int h = fact.hashCode();
        h = Util.hash(h, hierarchyName);
        h = Util.hash(h, name);
        h = Util.hash(h, source);
        h = Util.hash(h, foreignKey);
        return h;
    }

    @Override
	public String toString() {
        StringBuilder buf = new StringBuilder(100);
        buf.append("HierarchyUsage: ");
        buf.append("kind=");
        buf.append(this.kind.name());
        buf.append(", hierarchyName=");
        buf.append(this.hierarchyName);
        buf.append(", fullName=");
        buf.append(this.fullName);
        buf.append(", foreignKey=");
        buf.append(this.foreignKey);
        buf.append(", source=");
        buf.append(this.source);
        buf.append(", level=");
        buf.append(this.level);
        buf.append(", name=");
        buf.append(this.name);

        return buf.toString();
    }

    void init(
        RolapCube cube,
        RolapHierarchy hierarchy,
        DimensionConnectorMapping cubeDim)
    {
        // Three ways that a hierarchy can be joined to the fact table.
        if (cubeDim != null && cubeDim.getLevel() != null) {
            // 1. Specify an explicit 'level' attribute in a <DimensionUsage>.
            RolapLevel joinLevel = (RolapLevel)
                    Util.lookupHierarchyLevel(hierarchy, cubeDim.getLevel().getName());
            if (joinLevel == null) {
                throw new OlapRuntimeException(MessageFormat.format(
                    dimensionUsageHasUnknownLevel,
                        hierarchy.getUniqueName(),
                        cube.getName(),
                        cubeDim.getLevel().getName()));
            }
            String tableName = getTableAlias(joinLevel.getKeyExp());
            if (hierarchy instanceof RolapCubeHierarchy rch) {
                tableName = rch.lookupTableNameByAlias(tableName);
            }
            this.joinTable =
                findJoinTable(hierarchy, tableName);
            this.joinExp = joinLevel.getKeyExp();
        } else if (hierarchy.getHierarchyMapping() != null
            && hierarchy.getHierarchyMapping().getPrimaryKey() != null)
        {
            // 2. Specify a "primaryKey" attribute of in <Hierarchy>. You must
            //    also specify the "primaryKeyTable" attribute if the hierarchy
            //    is a join (hence has more than one table).
            this.joinTable =
                findJoinTable(
                    hierarchy,
                    hierarchy.getHierarchyMapping().getPrimaryKey().getTable());
            this.joinExp =
                new org.eclipse.daanse.rolap.common.RolapColumn(
                    getAlias(this.joinTable),
                    hierarchy.getHierarchyMapping().getPrimaryKey().getName());
        } else {
            // 3. If neither of the above, the join is assumed to be to key of
            //    the last level.
            final List<? extends Level> levels = hierarchy.getLevels();
            RolapLevel joinLevel = (RolapLevel) levels.getLast();
            String tableName = getTableAlias(joinLevel.getKeyExp());
            if (hierarchy instanceof RolapCubeHierarchy rch) {
                tableName = rch.lookupTableNameByAlias(tableName);
            }
            this.joinTable =
                findJoinTable(
                    hierarchy,
                    tableName);
            this.joinExp = joinLevel.getKeyExp();
        }

        // Unless this hierarchy is drawing from the fact table, we need
        // a join expresion and a foreign key.
        final boolean inFactTable = Utils.equalsQuery(this.joinTable, cube.getFact());
        if (!inFactTable) {
            if (this.joinExp == null) {
                throw new OlapRuntimeException(MessageFormat.format(
                    mustSpecifyPrimaryKeyForHierarchy,
                        hierarchy.getUniqueName(),
                        cube.getName()));
            }
            if (foreignKey == null) {
                throw new OlapRuntimeException(MessageFormat.format(
                    mustSpecifyForeignKeyForHierarchy,
                        hierarchy.getUniqueName(),
                        cube.getName()));
            }
        }
    }

    /**
     * Chooses the table with which to join a hierarchy to the fact table.
     *
     * @param hierarchy Hierarchy to be joined
     * @param tab Alias of the table; may be omitted if the hierarchy
     *   has only one table
     * @return A table, never null
     */
    private RelationalQueryMapping findJoinTable(
        RolapHierarchy hierarchy,
        TableMapping tab)
    {
        final RelationalQueryMapping table;
        if (tab == null || tab.getName() == null) {
            table = hierarchy.getUniqueTable();
            if (table == null) {
                throw new OlapRuntimeException(MessageFormat.format(
                    mustSpecifyPrimaryKeyTableForHierarchy,
                        hierarchy.getUniqueName()));
            }
        } else {
            table = find(hierarchy.getRelation(), tab.getName());
            if (table == null) {
                // todo: i18n msg
                throw Util.newError(
                    new StringBuilder("no table '").append(tab.getName())
                    .append("' found in hierarchy ").append(hierarchy.getUniqueName()).toString());
            }
        }
        return table;
    }

    private RelationalQueryMapping findJoinTable(
            RolapHierarchy hierarchy,
            String tableName)
        {
            final RelationalQueryMapping table;
            if (tableName == null) {
                table = hierarchy.getUniqueTable();
                if (table == null) {
                    throw new OlapRuntimeException(MessageFormat.format(
                        mustSpecifyPrimaryKeyTableForHierarchy,
                            hierarchy.getUniqueName()));
                }
            } else {
                table = find(hierarchy.getRelation(), tableName);
                if (table == null) {
                    // todo: i18n msg
                    throw Util.newError(
                        new StringBuilder("no table '").append(tableName)
                        .append("' found in hierarchy ").append(hierarchy.getUniqueName()).toString());
                }
            }
            return table;
        }

}
