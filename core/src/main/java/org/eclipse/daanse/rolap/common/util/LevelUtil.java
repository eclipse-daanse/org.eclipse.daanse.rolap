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

import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.common.star.RolapSqlExpression;
import org.eclipse.daanse.rolap.element.RolapColumn;

public class LevelUtil {

    private LevelUtil() {
        // constructor
    }

    public static SqlExpression getKeyExp(org.eclipse.daanse.rolap.mapping.model.Level level) {
        if (level.getColumn() instanceof org.eclipse.daanse.rolap.mapping.model.SQLExpressionColumn sec) {
            return new RolapSqlExpression(sec);
        } else if (level.getColumn() != null) {
            return new RolapColumn(getTableName(level.getColumn().getTable()), level.getColumn().getName());
        } else {
            return null;
        }
    }

    public static RolapSqlExpression getNameExp(org.eclipse.daanse.rolap.mapping.model.Level level) {
        if (level.getNameColumn() instanceof org.eclipse.daanse.rolap.mapping.model.SQLExpressionColumn sec) {
            return new RolapSqlExpression(sec);
        } else if (level.getNameColumn() != null && !Objects.equals(level.getNameColumn(), level.getColumn())) {
            return new RolapColumn(getTableName(level.getColumn().getTable()), level.getNameColumn().getName());
        } else {
            return null;
        }
    }

    private static String getTableName(org.eclipse.daanse.rolap.mapping.model.Table table) {
        if (table != null) {
            return table.getName();
        }
        return null;
	}

	public static RolapSqlExpression getCaptionExp(org.eclipse.daanse.rolap.mapping.model.Level level) {
	    if (level.getCaptionColumn() instanceof org.eclipse.daanse.rolap.mapping.model.SQLExpressionColumn sec) {
            return new RolapSqlExpression(sec);
        } else if (level.getCaptionColumn() != null) {
            return new RolapColumn(getTableName(level.getColumn().getTable()), level.getCaptionColumn().getName());
        } else {
            return null;
        }
    }

    public static List<? extends SqlExpression> getOrdinalExp(org.eclipse.daanse.rolap.mapping.model.Level level) {
        if (level.getOrdinalColumns() != null && !level.getOrdinalColumns().isEmpty()) {
            return level.getOrdinalColumns().stream().filter(oc -> oc.getColumn() != null).map(oc -> {
                if (oc.getColumn() instanceof org.eclipse.daanse.rolap.mapping.model.SQLExpressionColumn sec) {
                    return new RolapSqlExpression(sec, SortingDirection.valueOf(oc.getDirection().name()));
                }
                return new RolapColumn(getTableName(level.getColumn().getTable()), oc.getColumn().getName(), SortingDirection.valueOf(oc.getDirection().getName()));
            }).toList();
        } else {
            return List.of();
        }
    }

    public static RolapSqlExpression getParentExp(org.eclipse.daanse.rolap.mapping.model.ParentChildHierarchy hierarchy) {
        if (hierarchy.getParentColumn() instanceof org.eclipse.daanse.rolap.mapping.model.SQLExpressionColumn sec) {
            return new RolapSqlExpression(sec);
        } else if (hierarchy.getParentColumn() != null) {
            return new RolapColumn(getTableName(hierarchy.getParentColumn().getTable()), hierarchy.getParentColumn().getName());
        } else {
            return null;
        }
    }

    public static RolapSqlExpression getPropertyExp(org.eclipse.daanse.rolap.mapping.model.Level level, int i) {
        return new RolapColumn(getTableName(level.getColumn().getTable()), level.getMemberProperties().get(i).getColumn().getName());
    }
}
