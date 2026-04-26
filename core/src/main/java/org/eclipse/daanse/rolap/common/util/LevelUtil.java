/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.rolap.common.util;

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.ColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.util.resource.relational.Columns;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.common.star.RolapSqlExpression;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapHierarchy;

public class LevelUtil {

    private LevelUtil() {
        // constructor
    }

    public static SqlExpression getKeyExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level) {
        return getKeyExp(level, null);
    }

    public static SqlExpression getKeyExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level, RolapHierarchy hierarchy) {
        if (level.getColumn() instanceof org.eclipse.daanse.rolap.mapping.model.database.relational.ExpressionColumn sec) {
            return new RolapSqlExpression(sec);
        } else if (level.getColumn() != null) {
            return new RolapColumn(ownerAlias(hierarchy, level.getColumn()), level.getColumn().getName());
        } else {
            return null;
        }
    }

    public static RolapSqlExpression getNameExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level) {
        return getNameExp(level, null);
    }

    public static RolapSqlExpression getNameExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level, RolapHierarchy hierarchy) {
        if (level.getNameColumn() instanceof org.eclipse.daanse.rolap.mapping.model.database.relational.ExpressionColumn sec) {
            return new RolapSqlExpression(sec);
        } else if (level.getNameColumn() != null && !Objects.equals(level.getNameColumn(), level.getColumn())) {
            return new RolapColumn(ownerAlias(hierarchy, level.getColumn()), level.getNameColumn().getName());
        } else {
            return null;
        }
    }

    private static String getTableName(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet table) {
        if (table != null) {
            return table.getName();
        }
        return null;
    }

    /**
     * Returns the alias used by the given hierarchy's join tree to reference the
     * column's owner table/view. Falls back to the owner's bare name when no
     * hierarchy context is available or when the owner is not found in the tree.
     */
    private static String ownerAlias(RolapHierarchy hierarchy,
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column) {
        ColumnSet owner = Columns.owner(column).orElse(null);
        if (hierarchy != null && owner instanceof NamedColumnSet named) {
            return hierarchy.findAliasForOwner(named);
        }
        return owner != null ? owner.getName() : null;
    }

	public static RolapSqlExpression getCaptionExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level) {
        return getCaptionExp(level, null);
    }

    public static RolapSqlExpression getCaptionExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level, RolapHierarchy hierarchy) {
	    if (level.getCaptionColumn() instanceof org.eclipse.daanse.rolap.mapping.model.database.relational.ExpressionColumn sec) {
            return new RolapSqlExpression(sec);
        } else if (level.getCaptionColumn() != null) {
            return new RolapColumn(ownerAlias(hierarchy, level.getColumn()), level.getCaptionColumn().getName());
        } else {
            return null;
        }
    }

    public static List<? extends SqlExpression> getOrdinalExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level) {
        return getOrdinalExp(level, null);
    }

    public static List<? extends SqlExpression> getOrdinalExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level, RolapHierarchy hierarchy) {
        if (level.getOrdinalColumns() != null && !level.getOrdinalColumns().isEmpty()) {
            return level.getOrdinalColumns().stream().filter(oc -> oc.getColumn() != null).map(oc -> {
                if (oc.getColumn() instanceof org.eclipse.daanse.rolap.mapping.model.database.relational.ExpressionColumn sec) {
                    return new RolapSqlExpression(sec, SortingDirection.valueOf(oc.getDirection().name()));
                }
                return new RolapColumn(ownerAlias(hierarchy, level.getColumn()), oc.getColumn().getName(), SortingDirection.valueOf(oc.getDirection().getName()));
            }).toList();
        } else {
            return List.of();
        }
    }

    public static RolapSqlExpression getParentExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ParentChildHierarchy hierarchy) {
        return getParentExp(hierarchy, null);
    }

    public static RolapSqlExpression getParentExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ParentChildHierarchy hierarchy, RolapHierarchy rolapHierarchy) {
        if (hierarchy.getParentColumn() instanceof org.eclipse.daanse.rolap.mapping.model.database.relational.ExpressionColumn sec) {
            return new RolapSqlExpression(sec);
        } else if (hierarchy.getParentColumn() != null) {
            return new RolapColumn(ownerAlias(rolapHierarchy, hierarchy.getParentColumn()), hierarchy.getParentColumn().getName());
        } else {
            return null;
        }
    }

    public static RolapSqlExpression getPropertyExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level, int i) {
        return getPropertyExp(level, i, null);
    }

    public static RolapSqlExpression getPropertyExp(org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level level, int i, RolapHierarchy hierarchy) {
        return new RolapColumn(ownerAlias(hierarchy, level.getColumn()), level.getMemberProperties().get(i).getColumn().getName());
    }
}
